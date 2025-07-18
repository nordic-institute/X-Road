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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.config.CustomClientTlsSSLSocketFactory;
import org.niis.xroad.securityserver.restapi.wsdl.HostnameVerifiers;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.entity.CertificateEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import java.net.Socket;
import java.net.URL;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for testing internal server connections
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class InternalServerTestService {
    private static final String TLS = "TLS";
    private final ServerConfProvider serverConfProvider;

    /**
     * Tests if a HTTPS connection can be established to the given URL using
     * the specified certificates.
     *
     * @param trustedCerts certificates used for authentication
     * @param url          the URL for opening the connection
     * @throws Exception in case connection fails
     */
    public void testHttpsConnection(
            List<CertificateEntity> trustedCerts, String url) throws Exception {

        List<X509Certificate> trustedX509Certs = new ArrayList<>();
        for (CertificateEntity trustedCert : trustedCerts) {
            trustedX509Certs.add(CryptoUtils.readCertificate(trustedCert.getData()));
        }

        SSLContext ctx = SSLContext.getInstance(TLS);
        ctx.init(createServiceKeyManager(),
                new TrustManager[]{new ServiceTrustManager(trustedX509Certs)},
                new SecureRandom());

        HttpsURLConnection con = (HttpsURLConnection) (new URL(url).openConnection());

        con.setSSLSocketFactory(new CustomClientTlsSSLSocketFactory(ctx.getSocketFactory()));
        con.setHostnameVerifier(HostnameVerifiers.ACCEPT_ALL);

        con.connect();
    }

    private KeyManager[] createServiceKeyManager() throws Exception {
        InternalSSLKey key = serverConfProvider.getSSLKey();

        if (key != null) {
            return new KeyManager[]{new ServiceKeyManager(key)};
        }

        return null;
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    private static final class ServiceKeyManager extends X509ExtendedKeyManager {

        private static final String ALIAS = "AuthKeyManager";

        private final InternalSSLKey sslKey;

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return ALIAS;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return ALIAS;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return ALIAS;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return ALIAS;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            log.trace("getCertificateChain: {}", (Object) sslKey.getCertChain());
            return sslKey.getCertChain();
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            log.trace("getPrivateKey: {}", sslKey.getKey());
            return sslKey.getKey();
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return null;
        }
    }

    private class ServiceTrustManager implements X509TrustManager {

        private List<X509Certificate> trustedCerts;

        ServiceTrustManager(List<X509Certificate> trustedCerts) {
            this.trustedCerts = trustedCerts;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            log.trace("checkClientTrusted()");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {

            if (chain == null || chain.length == 0) {
                throw new IllegalArgumentException(
                        "Server certificate chain is empty");
            }

            if (trustedCerts.contains(chain[0])) {
                log.trace("Found matching IS certificate");
                return;
            }

            log.error("Could not find matching IS certificate");
            throw new CertificateException("Server certificate is not trusted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            log.trace("getAcceptedIssuers()");
            return new X509Certificate[]{};
        }
    }
}
