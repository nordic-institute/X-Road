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
package ee.ria.xroad.common.conf.globalconf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.X509TrustManager;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * This trust manager is used for connections between the security servers.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthTrustManager implements X509TrustManager {
    private final GlobalConfProvider globalConfProvider;

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        log.trace("getAcceptedIssuers");
        try {
            return globalConfProvider.getAuthTrustChain();
        } catch (Exception e) {
            log.error("Error getting authentication trust chain", e);
            return new X509Certificate[]{};
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        if (chain.length == 0) {
            log.error("Server did not send TLS certificate");
            throw new CertificateException(
                    "Server did not send TLS certificate");
        }

        log.trace("Received {} server certificates {}", chain.length, chain);

        if (!isSecurityServerAuthCert(chain[0])) {
            log.error("The server's authentication certificate is not trusted");
            throw new CertificateException(
                    "The server's authentication certificate is not trusted");
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType)
            throws CertificateException {
        // Check for the certificates later in AuthTrustVerifier
    }

    /**
     * Checks if the authentication certificate belongs to registered
     * security server
     *
     * @param cert the authentication certificate
     * @return true if the authentication certificate belongs to registered
     * security server
     */
    private boolean isSecurityServerAuthCert(X509Certificate cert) {
        log.trace("isSecurityServerAuthCert({})", cert.getSubjectX500Principal());
        try {
            return globalConfProvider.getServerId(cert) != null;
        } catch (Exception e) {
            log.error("Error occurred while getting server id", e);
            return false;
        }
    }
}
