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
package org.niis.xroad.proxy.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.RequestWrapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.proxy.core.clientproxy.IsAuthenticationData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.Client;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CLIENT_IDENTIFIER;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Consolidates client verification operations previously duplicated across 3 processor classes.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ClientVerificationService {

    private final ServerConfProvider serverConfProvider;
    private final ClientAuthenticationService clientAuthenticationService;
    private final GlobalConfProvider globalConfProvider;
    private final ProxyProperties proxyProperties;
    private final CertHelper certHelper;

    /**
     * Verifies that the client is registered with REGISTERED status.
     *
     * @param client the client identifier
     * @throws XrdRuntimeException if client is null or not registered
     */
    public void verifyClientStatus(ClientId client) {
        if (client == null) {
            throw XrdRuntimeException.systemException(INVALID_CLIENT_IDENTIFIER, "The client identifier is missing");
        }

        String status = serverConfProvider.getMemberStatus(client);
        if (!Client.STATUS_REGISTERED.equals(status)) {
            throw XrdRuntimeException.systemException(UNKNOWN_MEMBER, "Client '%s' not found".formatted(client));
        }
    }

    /**
     * Verifies client authentication by delegating to {@link ClientAuthenticationService} when certificate
     * verification is enabled via proxy properties.
     */
    public void verifyClientAuthentication(ClientId sender, RequestWrapper request) {
        log.trace("verifyClientAuthentication()");
        if (!proxyProperties.verifyClientCert()) {
            return;
        }
        IsAuthenticationData clientCert = clientAuthenticationService.getIsAuthenticationData(request, proxyProperties.logClientCert());
        clientAuthenticationService.verifyClientAuthentication(sender, clientCert);
    }

    /**
     * Verifies the SSL client certificate by building the certificate chain including the trust anchor
     * and delegating verification to {@link CertHelper}.
     *
     * @param instanceIdentifier the X-Road instance identifier (used to locate the CA cert)
     * @param clientSslCerts     the client's SSL certificate chain
     * @param ocspResponses      OCSP responses for the certificate chain
     * @param clientId           the client identifier (used for auth cert verification)
     * @throws CertificateEncodingException if certificate encoding fails
     * @throws IOException                  if an I/O error occurs
     */
    public void verifySslClientCert(String instanceIdentifier, X509Certificate[] clientSslCerts,
                                    List<OCSPResp> ocspResponses, ClientId clientId)
            throws CertificateEncodingException, IOException {
        if (ocspResponses.isEmpty()) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                    "Cannot verify TLS certificate, corresponding OCSP response is missing");
        }

        X509Certificate trustAnchor = globalConfProvider.getCaCert(instanceIdentifier,
                clientSslCerts[clientSslCerts.length - 1]);

        if (trustAnchor == null) {
            throw XrdRuntimeException.systemInternalError("Unable to find trust anchor");
        }

        try {
            CertChain chain = CertChainFactory.create(instanceIdentifier, ArrayUtils.add(clientSslCerts, trustAnchor));
            certHelper.verifyAuthCert(chain, ocspResponses, clientId);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED, e);
        }
    }
}
