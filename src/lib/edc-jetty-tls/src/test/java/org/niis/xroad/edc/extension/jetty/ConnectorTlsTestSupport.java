/*
 * The MIT License
 *
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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.InternalSSLKey;

import org.niis.xroad.edc.reload.PeriodicMaterialReloader.Loaded;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader.MaterialLoader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class ConnectorTlsTestSupport {

    static final String WRONG_KEY_PASSWORD = "not-the-keystore-password";

    private ConnectorTlsTestSupport() {
    }

    static KeyStore keyStoreOf(TestCertUtil.PKCS12 credentials) throws Exception {
        return keyStoreOf(credentials, InternalSSLKey.getKEY_PASSWORD());
    }

    static KeyStore keyStoreOf(TestCertUtil.PKCS12 credentials, char[] keyPassword) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, credentials.key, keyPassword, credentials.certChain);
        return keyStore;
    }

    static KeyStore wrongKeyPassword() throws Exception {
        return keyStoreOf(TestCertUtil.getOcspSigner(), WRONG_KEY_PASSWORD.toCharArray());
    }

    static Loaded<KeyStore> usable(String fingerprint, TestCertUtil.PKCS12 credentials) throws Exception {
        return new Loaded<>(keyStoreOf(credentials), fingerprint);
    }


    static SSLContext trustAllTlsContext() throws Exception {
        var trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // trust-all test client: every certificate is accepted
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // trust-all test client: every certificate is accepted
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {trustAll}, new SecureRandom());
        return sslContext;
    }

    static X509Certificate servedCertificate(int port) throws Exception {
        try (var socket = (SSLSocket) trustAllTlsContext().getSocketFactory().createSocket("localhost", port)) {
            socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
            socket.startHandshake();
            return (X509Certificate) socket.getSession().getPeerCertificates()[0];
        }
    }

    static final class ScriptedLoader implements MaterialLoader<KeyStore> {

        final AtomicInteger loads = new AtomicInteger();

        private final AtomicReference<Loaded<KeyStore>> current = new AtomicReference<>();
        private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

        void serve(Loaded<KeyStore> material) {
            failure.set(null);
            current.set(material);
        }

        void fail(RuntimeException exception) {
            failure.set(exception);
        }

        @Override
        public Loaded<KeyStore> load() {
            loads.incrementAndGet();
            var exception = failure.get();
            if (exception != null) {
                throw exception;
            }
            var material = current.get();
            if (material == null) {
                throw new IllegalStateException("slot is empty");
            }
            return material;
        }
    }
}
