/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.proxy.core.testsuite;

import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.RestRetryTestUtil.CountingLogManager;
import org.niis.xroad.proxy.core.RestRetryTestUtil.HandshakeOrderGate;
import org.niis.xroad.proxy.core.RestRetryTestUtil.RejectingSslServerProxy;
import org.niis.xroad.proxy.core.RestRetryTestUtil.TestAddressResolver;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper;
import org.niis.xroad.proxy.core.test.TestContext;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.TestPortUtils.findRandomPort;
import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;
import static org.niis.xroad.proxy.core.RestRetryTestUtil.startRejectingProxy;
import static org.niis.xroad.proxy.core.RestRetryTestUtil.startResettingProxy;
import static org.niis.xroad.proxy.core.RestRetryTestUtil.uri;

/**
 * Tests the client proxy retry of SOAP requests when a TLS handshake failure surfaces
 * mid-request (a TLS 1.3 server proxy rejecting the client certificate after the handshake
 * has completed from the client's point of view).
 */
@Slf4j
class SoapProxyRetryTest {

    private static final Map<String, String> PROPS = new HashMap<>();
    private static final ProxyTestSuiteHelper HELPER = new ProxyTestSuiteHelper();
    private static final TestAddressResolver RESOLVER = new TestAddressResolver();
    private static final HandshakeOrderGate GATE = new HandshakeOrderGate();

    private final CountingLogManager logManager =
            new CountingLogManager(mock(GlobalConfProvider.class), mock(ServerConfProvider.class));

    private TestContext ctx;
    private int serverProxyPort;

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        PROPS.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        PROPS.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");
        PROPS.put("xroad.proxy.ssl-enabled", "true");
        HELPER.setPropsIfNotSet(PROPS);
        
        HELPER.startTestServices();
    }

    @AfterAll
    static void afterAll() {
        HELPER.destroyTestServices();
    }

    @BeforeEach
    void setUp() {
        RESOLVER.reset();
        GATE.reset();
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.destroy();
        }
    }

    @Test
    void shouldRetryWithNextSecurityServerWhenHandshakeFailsMidRequest() throws Exception {
        startContext(true);
        int decoyPort = findRandomPort(); // nothing listens here, so the first attempt connects the rejecting proxy
        RejectingSslServerProxy badProxy = startRejectingProxy(ctx.getKeyConfProvider().getAuthKey(), GATE);
        int badPort = badProxy.getPort();
        try {
            RESOLVER.plan(List.of(
                    List.of(uri(badPort), uri(decoyPort)), // consumed by the op-monitoring address lookup
                    List.of(uri(badPort), uri(decoyPort)),
                    List.of(uri(serverProxyPort))));

            assertTrue(normalTestCase().execute(ctx));

            // op-monitoring lookup + initial attempt + one retry
            assertThat(RESOLVER.resolveCount()).isEqualTo(3);
            assertThat(logManager.clientRequestLogCount()).isEqualTo(1);
        } finally {
            badProxy.destroy();
        }
    }

    @Test
    void shouldNotRetryWhenLargeRequestDiesMidWrite() throws Exception {
        startContext(true);
        int decoyPort = findRandomPort();
        RejectingSslServerProxy badProxy = startResettingProxy(ctx.getKeyConfProvider().getAuthKey(), GATE);
        int badPort = badProxy.getPort();
        try {
            // a retry would succeed against the real server proxy and produce a normal response,
            // so a fault here proves no retry was attempted
            RESOLVER.plan(List.of(
                    List.of(uri(badPort), uri(decoyPort)), // consumed by the op-monitoring address lookup
                    List.of(uri(badPort), uri(decoyPort)),
                    List.of(uri(serverProxyPort))));

            // the resetting server kills the TCP connection without a TLS alert once the request
            // transfer is underway, so the large attachment dies with a plain connection error
            // (no SSLHandshakeException in the chain) and the strict retry gate deliberately
            // does not retry it
            assertTrue(bigAttachmentTestCase().execute(ctx));

            // op-monitoring lookup + the single attempt
            assertThat(RESOLVER.resolveCount()).isEqualTo(2);
        } finally {
            badProxy.destroy();
        }
    }

    @Test
    void shouldPreserveErrorContractWhenRetriesExhausted() throws Exception {
        startContext(true);
        RejectingSslServerProxy badProxy1 = startRejectingProxy(ctx.getKeyConfProvider().getAuthKey(), GATE);
        RejectingSslServerProxy badProxy2 = startRejectingProxy(ctx.getKeyConfProvider().getAuthKey(), GATE);
        int badPort1 = badProxy1.getPort();
        int badPort2 = badProxy2.getPort();
        try {
            RESOLVER.plan(List.of(List.of(uri(badPort1), uri(badPort2))));

            assertTrue(sslAuthFailureTestCase().execute(ctx));

            // op-monitoring lookup + initial attempt + one retry; after both addresses are in
            // cooldown the retry aborts
            assertThat(RESOLVER.resolveCount()).isEqualTo(3);
            assertThat(logManager.clientRequestLogCount()).isEqualTo(1);
        } finally {
            badProxy1.destroy();
            badProxy2.destroy();
        }
    }

    @Test
    void shouldNotRetryWhenRetryIsDisabled() throws Exception {
        startContext(false);
        int decoyPort = findRandomPort();
        RejectingSslServerProxy badProxy = startRejectingProxy(ctx.getKeyConfProvider().getAuthKey(), GATE);
        int badPort = badProxy.getPort();
        try {
            // a retry would succeed against the real server proxy and produce a normal response,
            // so a fault here proves no retry was attempted
            RESOLVER.plan(List.of(
                    List.of(uri(badPort), uri(decoyPort)), // consumed by the op-monitoring address lookup
                    List.of(uri(badPort), uri(decoyPort)),
                    List.of(uri(serverProxyPort))));

            assertTrue(clientProxyFaultTestCase().execute(ctx));

            // op-monitoring lookup + the single attempt
            assertThat(RESOLVER.resolveCount()).isEqualTo(2);
        } finally {
            badProxy.destroy();
        }
    }

    private void startContext(boolean retryEnabled) {
        PROPS.put("xroad.proxy.client-proxy.enable-request-retry", valueOf(retryEnabled));
        PROPS.put("xroad.proxy.server.listen-port", "0");
        PROPS.put("xroad.proxy.client-proxy.client-http-port", "0");
        HELPER.proxyProperties = ConfigUtils.initConfiguration(ProxyProperties.class, PROPS);
        ctx = new TestContext(HELPER, true, mock(org.niis.xroad.monitor.rpc.MonitorRpcClient.class), RESOLVER,
                GATE::clientPastConnect);
        serverProxyPort = ctx.getServerProxyListenPort();
        int actualClientHttpPort = ctx.getClientHttpPort();
        PROPS.put("xroad.proxy.server.listen-port", valueOf(serverProxyPort));
        PROPS.put("xroad.proxy.client-proxy.client-http-port", valueOf(actualClientHttpPort));
        HELPER.proxyProperties = ConfigUtils.initConfiguration(ProxyProperties.class, PROPS);
        MessageLog.init(logManager);
    }

    private static MessageTestCase normalTestCase() {
        return new MessageTestCase() {
            {
                requestFileName = "getstate.query";
                responseFile = "getstate.answer";
            }

            @Override
            protected void validateNormalResponse(Message receivedResponse) {
                // normal response received — the retry was transparent to the client
            }
        };
    }

    private static MessageTestCase sslAuthFailureTestCase() {
        return new MessageTestCase() {
            {
                requestFileName = "getstate.query";
            }

            @Override
            protected void validateFaultResponse(Message receivedResponse) {
                assertErrorCodeStartsWith(SERVER_CLIENTPROXY_X, SSL_AUTH_FAILED.code());
            }
        };
    }

    private static MessageTestCase clientProxyFaultTestCase() {
        return new MessageTestCase() {
            {
                requestFileName = "getstate.query";
            }

            @Override
            protected void validateFaultResponse(Message receivedResponse) {
                // depending on whether the rejection alert interrupts the request write or the
                // response read, the error is network_error or ssl_authentication_failed —
                // both are the pre-retry-feature behavior
                assertErrorCodeStartsWith(SERVER_CLIENTPROXY_X);
            }
        };
    }

    private static MessageTestCase bigAttachmentTestCase() {
        return new MessageTestCase() {

            private static final int ATTACHMENT_SIZE_BYTES = 1024 * 1024;

            {
                responseFile = "getstate.answer";
            }

            @Override
            protected Pair<String, InputStream> getRequestInput(boolean addUtf8Bom) throws Exception {
                PipedOutputStream os = new PipedOutputStream();
                PipedInputStream is = new PipedInputStream(os);
                MultiPartOutputStream mpos = new MultiPartOutputStream(os);

                new Thread(() -> writeRequest(mpos)).start();

                return Pair.of("multipart/related; charset=UTF-8; boundary=" + mpos.getBoundary(), is);
            }

            private void writeRequest(MultiPartOutputStream mpos) {
                var path = Paths.get(QUERIES_DIR + "/getstate.query");
                try (mpos; InputStream in = changeQueryId(Files.newInputStream(path))) {
                    mpos.startPart(MimeTypes.TEXT_XML_UTF8);
                    mpos.write(IOUtils.toByteArray(in));

                    mpos.startPart("application/octet-stream", new String[]{"Content-Transfer-Encoding: binary"});
                    var block = new byte[1024];
                    new Random(42).nextBytes(block);
                    for (int written = 0; written < ATTACHMENT_SIZE_BYTES; written += block.length) {
                        mpos.write(block);
                    }
                } catch (Exception ex) {
                    log.error("Error when writing the attachment request", ex);
                }
            }

            @Override
            protected void validateNormalResponse(Message receivedResponse) throws Exception {
                throw new Exception("Received normal response - the mid-write failure was retried unexpectedly");
            }

            @Override
            protected void validateFaultResponse(Message receivedResponse) {
                // the connection error of the failed write surfaces to the client unchanged
                assertErrorCodeStartsWith(SERVER_CLIENTPROXY_X);
            }
        };
    }
}
