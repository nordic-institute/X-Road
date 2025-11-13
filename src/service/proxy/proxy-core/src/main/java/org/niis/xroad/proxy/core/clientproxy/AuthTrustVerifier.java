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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.protocol.HttpContext;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.util.CertHashBasedOcspResponderClient;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;

/**
 * This class is responsible for verifying the server proxy SSL certificate.
 * Since we need to know the provider name used when making request to the
 * server proxy, we cannot verify the server certificate directly in the
 * AuthTrustManager, because it is impossible to relate the provider name
 * to the current SSL session in the AuthTrustManager.
 * <p>
 * Instead, in the AuthTrustManager, we declare that the server is trusted
 * but then use a RequestInterceptor that will be called after the
 * SSL handshake takes place. We can then retrieve the provider name from
 * the HttpContext (stored there previously by the MultipartSender) and
 * the peer certificates and do the validation of the certificate.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class AuthTrustVerifier {

    public static final String ID_PROVIDERNAME = "request.providerName";

    private final CertHashBasedOcspResponderClient certHashBasedOcspResponderClient;
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final CertHelper certHelper;

    protected void verify(HttpContext context, SSLSession sslSession,
                          URI selectedAddress) {
        log.debug("verify()");

        ServiceId service = (ServiceId) context.getAttribute(ID_PROVIDERNAME);
        if (service == null) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                    "Could not get provider name from context");
        }

        X509Certificate[] certs = getPeerCertificates(sslSession);
        if (certs.length == 0) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                    "Could not get peer certificates from context");
        }

        try {
            verifyAuthCert(service.getClientId(), certs, selectedAddress);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private void verifyAuthCert(ClientId serviceProvider, X509Certificate[] certs, URI address)
            throws CertificateEncodingException, IOException, CertificateParsingException, OperatorCreationException {
        CertChain chain;
        List<OCSPResp> ocspResponses;
        try {
            List<X509Certificate> additionalCerts = Arrays.asList(ArrayUtils.subarray(certs, 1, certs.length));
            chain = CertChainFactory.create(serviceProvider.getXRoadInstance(),
                    globalConfProvider.getCaCert(serviceProvider.getXRoadInstance(), certs[0]),
                    certs[0], additionalCerts);
            ocspResponses = getOcspResponses(
                    chain.getAllCertsWithoutTrustedRoot(), address.getHost());
        } catch (XrdRuntimeException e) {
            throw e.withPrefix(X_SSL_AUTH_FAILED);
        }

        certHelper.verifyAuthCert(chain, ocspResponses, serviceProvider);
    }

    /**
     * Gets OCSP responses for each certificate in the chain. If the OCSP
     * response is not locally available (cached), it will be retrieved
     * from the internal OCSP responder that is located at the given address.
     */
    private List<OCSPResp> getOcspResponses(
            List<X509Certificate> chain, String address) throws CertificateEncodingException, IOException {
        List<X509Certificate> certs = new ArrayList<>();
        List<OCSPResp> responses = new ArrayList<>();

        // Check for locally available OCSP responses
        for (X509Certificate cert : chain) {
            OCSPResp response = null;
            try {
                // Do we have a cached OCSP response for that cert?
                log.trace("get ocsp response from key conf");
                response = keyConfProvider.getOcspResponse(cert);
                log.trace("ocsp response from key conf: {}", response);
            } catch (XrdRuntimeException e) {
                // Log it and continue; only thrown if the response could
                // not be loaded from a file -- not important to us here.
                log.warn("Cached OCSP response could not be found", e);
            }

            if (response != null) {
                responses.add(response);
            } else {
                // Did not find response locally, add the hash to be retrieved
                // from server.
                certs.add(cert);
            }
        }

        // Retrieve OCSP responses for those certs whose responses
        // are not locally available, from ServerProxy
        if (!certs.isEmpty()) {
            log.trace("number of certs that still need ocsp responses: {}", certs.size());
            responses.addAll(getAndCacheOcspResponses(certs, address));
        } else {
            log.trace("all the certs have ocsp responses");
        }

        return responses;
    }

    /**
     * Sends the GET request with all cert hashes which need OCSP responses.
     */
    private List<OCSPResp> getAndCacheOcspResponses(List<X509Certificate> certs, String address)
            throws CertificateEncodingException, IOException {
        List<OCSPResp> receivedResponses;
        try {
            log.trace("get ocsp responses from server {}", address);
            receivedResponses = certHashBasedOcspResponderClient.getOcspResponsesFromServer(address, certs);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
        }

        // Did we get OCSP response for each cert?
        if (receivedResponses.size() != certs.size()) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR,
                    "Could not get all OCSP responses from server (expected %s, but got %s)".formatted(
                            certs.size(), receivedResponses.size()));
        }

        // Cache the responses locally
        log.trace("got ocsp responses, setting them to key conf");
        keyConfProvider.setOcspResponses(certs, receivedResponses);

        return receivedResponses;
    }

    private static X509Certificate[] getPeerCertificates(SSLSession session) {
        if (session == null) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED, "No TLS session");
        }

        try {
            // Note: assuming X509-based auth
            return (X509Certificate[]) session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED, "Service provider did not provide TLS certificate", e);
        }
    }

}
