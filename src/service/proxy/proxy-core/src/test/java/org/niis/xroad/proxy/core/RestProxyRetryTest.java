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
package org.niis.xroad.proxy.core;

import ee.ria.xroad.common.message.RestMessage;

import io.restassured.response.ValidatableResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.RestRetryTestUtil.CountingLogManager;
import org.niis.xroad.proxy.core.RestRetryTestUtil.TestAddressResolver;
import org.niis.xroad.proxy.core.messagelog.MessageLog;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.proxy.core.RestRetryTestUtil.startRejectingProxy;
import static org.niis.xroad.proxy.core.RestRetryTestUtil.uri;

/**
 * Tests the client proxy retry of REST requests when a TLS handshake failure surfaces
 * mid-request (a TLS 1.3 server proxy rejecting the client certificate after the handshake
 * has completed from the client's point of view).
 */
class RestProxyRetryTest extends AbstractProxyIntegrationTest {

    private static final String PREFIX = "/r" + RestMessage.PROTOCOL_VERSION;

    private static final TestAddressResolver RESOLVER = new TestAddressResolver();
    private static final CountingLogManager LOG_MANAGER = new CountingLogManager(TEST_GLOBAL_CONF, TEST_SERVER_CONF);

    @BeforeAll
    static void useScriptedResolverAndMessageLogCapture() throws Exception {
        // xroad.proxy.client-proxy.enable-request-retry left at its default (true)
        serviceAddressResolverOverride = RESOLVER;
        restartProxies();
        MessageLog.init(LOG_MANAGER);
    }

    @BeforeEach
    void resetRetryTestState() {
        RESOLVER.reset();
        LOG_MANAGER.reset();
    }

    @Test
    void shouldRetryWithNextSecurityServerWhenHandshakeFailsMidRequest() throws Exception {
        int badPort = getFreePort();
        int decoyPort = getFreePort(); // nothing listens here, so the first attempt connects the rejecting proxy
        var badProxy = startRejectingProxy(badPort, serverKeyConf.getAuthKey());
        try {
            RESOLVER.plan(List.of(
                    List.of(uri(badPort), uri(decoyPort)),
                    List.of(uri(proxyServerPort))));

            postEcho()
                    .statusCode(200)
                    .body("value", Matchers.equalTo(42));

            assertThat(RESOLVER.resolveCount()).isEqualTo(2);
            assertThat(LOG_MANAGER.clientRequestLogCount()).isEqualTo(1);
        } finally {
            badProxy.destroy();
        }
    }

    @Test
    void shouldPreserveErrorContractWhenRetriesExhausted() throws Exception {
        int badPort1 = getFreePort();
        int badPort2 = getFreePort();
        var badProxy1 = startRejectingProxy(badPort1, serverKeyConf.getAuthKey());
        var badProxy2 = startRejectingProxy(badPort2, serverKeyConf.getAuthKey());
        try {
            RESOLVER.plan(List.of(List.of(uri(badPort1), uri(badPort2))));

            postEcho()
                    .statusCode(500)
                    .header("X-Road-Error", Matchers.containsString("ssl_authentication_failed"));

            // initial attempt + one retry; after both addresses are in cooldown the retry aborts
            assertThat(RESOLVER.resolveCount()).isEqualTo(2);
            assertThat(LOG_MANAGER.clientRequestLogCount()).isEqualTo(1);
        } finally {
            badProxy1.destroy();
            badProxy2.destroy();
        }
    }

    @Test
    void shouldAbortRetryWhenNoUsableAddressesRemain() throws Exception {
        int badPort = getFreePort();
        var badProxy = startRejectingProxy(badPort, serverKeyConf.getAuthKey());
        try {
            RESOLVER.plan(List.of(List.of(uri(badPort))));

            postEcho()
                    .statusCode(500)
                    .header("X-Road-Error", Matchers.containsString("ssl_authentication_failed"));

            // the only address enters cooldown after the first failure: no retry is attempted
            assertThat(RESOLVER.resolveCount()).isEqualTo(1);
            assertThat(LOG_MANAGER.clientRequestLogCount()).isEqualTo(1);
        } finally {
            badProxy.destroy();
        }
    }

    private ValidatableResponse postEcho() {
        return given()
                .baseUri("http://127.0.0.1")
                .port(proxyClientPort)
                .header("Content-Type", "application/json")
                .header("X-Road-Client", "EE/BUSINESS/consumer/sub")
                .body("{\"value\" : 42}")
                .post(PREFIX + "/EE/BUSINESS/producer/sub/echo")
                .then();
    }
}
