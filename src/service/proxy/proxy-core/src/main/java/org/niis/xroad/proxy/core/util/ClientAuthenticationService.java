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

package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.RequestWrapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.proxy.core.clientproxy.IsAuthenticationData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.ServerConfProvider;

import javax.net.ssl.X509TrustManager;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ClientAuthenticationService {

    private final ServerConfProvider serverConfProvider;
    private final VaultKeyProvider vaultKeyProvider;
    private final ProxyProperties proxyProperties;

    public IsAuthenticationData getIsAuthenticationData(RequestWrapper request, boolean logClientCert) {
        var isPlaintextConnection = !"https".equals(request.getHttpURI().getScheme()); // if not HTTPS, it's plaintext
        var cert = request.getPeerCertificates()
                .filter(ArrayUtils::isNotEmpty)
                .map(arr -> arr[0]);

        if (logClientCert) {
            cert.map(X509Certificate::getSubjectX500Principal)
                    .ifPresentOrElse(
                            subject -> log.info("Client certificate's subject: {}", subject),
                            () -> log.info("Client certificate not found"));
        }

        return new IsAuthenticationData(cert.orElse(null), isPlaintextConnection);
    }

    /**
     * Verifies the authentication for the client certificate.
     *
     * @param client the client identifier
     * @param auth   the authentication data of the information system
     */
    public void verifyClientAuthentication(ClientId client, IsAuthenticationData auth) {

        IsAuthentication isAuthentication = serverConfProvider.getIsAuthentication(client);
        if (isAuthentication == null) {
            // Means the client was not found in the server conf.
            // The getIsAuthentication method implemented in ServerConfCommonImpl
            // checks if the client exists; if it does, returns the
            // isAuthentication value or NOSSL if no value is specified.
            throw XrdRuntimeException.systemInternalError("Client '%s' not found".formatted(client));
        }

        log.trace("IS authentication for client '{}' is: {}", client, isAuthentication);

        if (isAuthentication == IsAuthentication.SSLNOAUTH && auth.isPlaintextConnection()) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                    "Client (%s) specifies HTTPS NO AUTH but client made plaintext connection".formatted(client));
        } else if (isAuthentication == IsAuthentication.SSLAUTH) {
            if (auth.cert() == null) {
                throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                        "Client (%s) specifies HTTPS but did not supply TLS certificate".formatted(client));
            }

            // Accept certificates issued by OpenBao (management requests from Proxy UI to ClientProxy within the same security server)
            if (clientAuthenticationIssuedByVault(auth)) {
                return;
            }

            List<X509Certificate> isCerts = serverConfProvider.getIsCerts(client);
            if (isCerts.isEmpty()) {
                throw XrdRuntimeException.systemException(SSL_AUTH_FAILED, "Client (%s) has no IS certificates".formatted(client));
            }

            if (!isCerts.contains(auth.cert())) {
                throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                        "Client (%s) TLS certificate does not match any IS certificates".formatted(client));
            }

            clientIsCertPeriodValidation(client, auth.cert());
        }
    }

    private boolean clientAuthenticationIssuedByVault(IsAuthenticationData auth) {
        try {
            var trustManager = (X509TrustManager) vaultKeyProvider.getTrustManager();
            for (X509Certificate vaultIssuer : trustManager.getAcceptedIssuers()) {
                try {
                    auth.cert().verify(vaultIssuer.getPublicKey());
                    return true;
                } catch (Exception e) {
                    // given issuer is not the one that signed the client cert, try next
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to obtain vault key provider's trust manager", e);
            return false;
        }
    }

    private void clientIsCertPeriodValidation(ClientId client, X509Certificate cert) {
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            if (proxyProperties.enforceClientIsCertValidityPeriodCheck()) {
                throw XrdRuntimeException.systemException(SSL_AUTH_FAILED, "Client (%s) TLS certificate is expired".formatted(client));
            } else {
                log.warn("Client {} TLS certificate is expired", client);
            }
        } catch (CertificateNotYetValidException e) {
            if (proxyProperties.enforceClientIsCertValidityPeriodCheck()) {
                throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                        "Client (%s) TLS certificate is not yet valid".formatted(client));
            } else {
                log.warn("Client {} TLS certificate is not yet valid", client);
            }
        }
    }

}
