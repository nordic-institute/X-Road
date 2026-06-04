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

import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.messagelog.LogMessage;
import org.niis.xroad.messagelog.RestLogMessage;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.proxy.core.addon.messagelog.AbstractLogManager;
import org.niis.xroad.proxy.core.service.ServiceAddressResolver;
import org.niis.xroad.proxy.core.test.DummySslServerProxy;
import org.niis.xroad.proxy.core.util.ProxyRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;

import javax.net.ssl.X509TrustManager;

import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.ria.xroad.common.TestCertUtil.getCaCert;

/**
 * Shared helpers for the client proxy TLS handshake retry integration tests.
 */
final class RestRetryTestUtil {

    private RestRetryTestUtil() {
    }

    static URI uri(int port) {
        return URI.create("https://127.0.0.1:%d/".formatted(port));
    }

    /**
     * Starts a TLS endpoint that presents the given auth key (so the client-side trust
     * verification passes at connect time) but rejects the client certificate, producing the
     * delayed TLS 1.3 handshake failure during request execution.
     */
    static DummySslServerProxy startRejectingProxy(int port, AuthKey authKey) throws Exception {
        var certChain = authKey.certChain().getAllCertsWithoutTrustedRoot().toArray(new X509Certificate[0]);
        var keyManager = new DummySslServerProxy.DummyAuthKeyManager() {
            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return certChain;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return authKey.key();
            }
        };
        var proxy = new DummySslServerProxy("127.0.0.1", port, keyManager, new RejectingClientTrustManager());
        proxy.start();
        return proxy;
    }

    /**
     * Trust manager that rejects every client certificate, triggering a post-handshake
     * TLS 1.3 authentication failure on the connecting client. The rejection is delayed
     * so that on loopback — like over a real network — the client completes its handshake
     * and starts sending the request before the rejection alert arrives; without the delay
     * the server kills the connection while the client is still writing its handshake
     * flight, and the failure would surface at connect time instead of mid-request.
     */
    static final class RejectingClientTrustManager implements X509TrustManager {

        private static final long REJECTION_DELAY_MILLIS = 500;

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                Thread.sleep(REJECTION_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new CertificateException("Client certificate rejected");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{getCaCert()};
        }
    }

    /**
     * Service address resolver returning a scripted list of addresses per resolve call;
     * the last list repeats once the script is exhausted.
     */
    static final class TestAddressResolver implements ServiceAddressResolver {
        private volatile List<List<URI>> plan = List.of();
        private final AtomicInteger calls = new AtomicInteger();

        void plan(List<List<URI>> addressesPerResolve) {
            this.plan = addressesPerResolve;
            calls.set(0);
        }

        int resolveCount() {
            return calls.get();
        }

        void reset() {
            plan = List.of();
            calls.set(0);
        }

        @Override
        public List<URI> resolve(ServiceId serviceProvider, SecurityServerId securityServerId, ProxyRequestContext ctx) {
            int index = calls.getAndIncrement();
            return new ArrayList<>(plan.get(Math.min(index, plan.size() - 1)));
        }
    }

    /**
     * Message log manager that records REST log messages so tests can assert how many
     * client-side request records were produced.
     */
    static final class CountingLogManager extends AbstractLogManager {
        private final List<RestLogMessage> messages = new CopyOnWriteArrayList<>();

        CountingLogManager(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider) {
            super(globalConfProvider, serverConfProvider);
        }

        @Override
        public void log(LogMessage message) {
            if (message instanceof RestLogMessage restLogMessage) {
                messages.add(restLogMessage);
            }
        }

        @Override
        public TimestampRecord timestamp(Long messageRecordId) {
            return null;
        }

        @Override
        public Map<String, DiagnosticsStatus> getDiagnosticStatus() {
            return Map.of();
        }

        long clientRequestLogCount() {
            return messages.stream()
                    .filter(LogMessage::isClientSide)
                    .filter(message -> !message.isResponse())
                    .count();
        }

        void reset() {
            messages.clear();
        }
    }
}
