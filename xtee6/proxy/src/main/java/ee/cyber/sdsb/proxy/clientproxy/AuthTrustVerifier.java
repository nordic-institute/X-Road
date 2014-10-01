package ee.cyber.sdsb.proxy.clientproxy;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.ArrayUtils;
import org.apache.http.protocol.HttpContext;
import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.cert.CertHelper;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.util.CertUtils;
import ee.cyber.sdsb.proxy.conf.KeyConf;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CertHashBasedOcspResponderClient.getOcspResponsesFromServer;

/**
 * This class is responsible for verifying the server proxy SSL certificate.
 * Since we need to know the provider name used when making request to the
 * server proxy, we cannot verify the server certificate directly in the
 * AuthTrustManager, because it is impossible to relate the provider name
 * to the current SSL session in the AuthTrustManager.
 *
 * Instead, in the AuthTrustManager, we declare that the server is trusted
 * but then use a RequestInterceptor that will be called after the
 * SSL handshake takes place. We can then retrieve the provider name from
 * the HttpContext (stored there previously by the MultipartSender) and
 * the peer certificates and do the validation of the certificate.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class AuthTrustVerifier {

    public static final String ID_PROVIDERNAME = "request.providerName";

    static void verify(HttpContext context, SSLSession sslSession) {
        log.debug("verify()");

        ServiceId service = (ServiceId) context.getAttribute(ID_PROVIDERNAME);
        if (service == null) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Could not get provider name from context");
        }

        X509Certificate[] certs = getPeerCertificates(sslSession);
        if (certs.length == 0) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Could not get peer certificates from context");
        }

        try {
            verifyAuthCert(service.getClientId(), certs);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private static void verifyAuthCert(ClientId serviceProvider,
            X509Certificate[] certs) throws Exception {
        CertChain chain;
        List<OCSPResp> ocspResponses;
        try {
            X509Certificate trustAnchor =
                    GlobalConf.getCaCert(certs[certs.length - 1]);
            if (trustAnchor == null) {
                throw new Exception("Unable to find trust anchor");
            }

            chain = CertChain.create(
                    (X509Certificate[]) ArrayUtils.add(certs, trustAnchor));
            ocspResponses = getOcspResponses(chain.getEndEntityCert(),
                    serviceProvider, chain.getAllCertsWithoutTrustedRoot());
        } catch (CodedException e) {
            throw e.withPrefix(X_SSL_AUTH_FAILED);
        }

        CertHelper.verifyAuthCert(chain, ocspResponses, serviceProvider);
    }

    /**
     * Gets OCSP responses for each certificate in the chain. If the OCSP
     * response is not locally available (cached), it will be retrieved
     * from the internal OCSP responder that is located at the service provider.
     */
    private static List<OCSPResp> getOcspResponses(X509Certificate authCert,
            ClientId serviceProvider, List<X509Certificate> chain)
                    throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        List<OCSPResp> responses = new ArrayList<>();

        // Check for locally available OCSP responses
        for (X509Certificate cert : chain) {
            OCSPResp response = null;
            try {
                // Do we have a cached OCSP response for that cert?
                response = KeyConf.getOcspResponse(cert);
            } catch (CodedException e) {
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
            responses.addAll(getAndCacheOcspResponses(authCert,
                    serviceProvider, certs));
        }

        return responses;
    }

    /**
     * Sends the GET request with all cert hashes which need OCSP responses.
     */
    private static List<OCSPResp> getAndCacheOcspResponses(
            X509Certificate authCert, ClientId serviceProvider,
            List<X509Certificate> hashes) throws Exception {
        String address = GlobalConf.getProviderAddress(authCert);
        if (address == null || address.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER,
                    "Could not get address for " + serviceProvider);
        }

        List<OCSPResp> receivedResponses;
        try {
            receivedResponses = getOcspResponsesFromServer(address,
                    CertUtils.getCertHashes(hashes));
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        // Did we get OCSP response for each cert hash?
        if (receivedResponses.size() != hashes.size()) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not get all OCSP responses from server "
                            + "(expected %s, but got %s)",
                    hashes.size(), receivedResponses.size());
        }

        // Cache the responses locally
        KeyConf.setOcspResponses(hashes, receivedResponses);

        return receivedResponses;
    }

    private static X509Certificate[] getPeerCertificates(SSLSession session) {
        if (session == null) {
            throw new CodedException(X_SSL_AUTH_FAILED, "No SSL session");
        }

        try {
            // Note: assuming X509-based auth
            return (X509Certificate[]) session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            log.error("Error while getting peer certificates", e);
            throw new CodedException(X_SSL_AUTH_FAILED, "Service provider "
                    + "did not send correct authentication certificate");
        }
    }

}
