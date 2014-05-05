package ee.cyber.sdsb.proxy.clientproxy;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.apache.http.protocol.HttpContext;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertHelper;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.proxy.conf.ServerConf;

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
public class AuthTrustVerifier {

    public static final String ID_PROVIDERNAME = "request.providerName";

    private static final Logger LOG =
            LoggerFactory.getLogger(AuthTrustVerifier.class);

    AuthTrustVerifier() {
    }

    static void verify(HttpContext context, SSLSession sslSession) {
        LOG.debug("verify()");

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
            verifyAuthCert(service.getClientId(), certs[0], certs);
        } catch (Exception e) {
            // since the overridden process() method only allows HttpException
            // or IOException, we simply catch all exceptions here and translate
            // them to corresponding CodedExceptions.
            throw translateException(e);
        }
    }

    private static void verifyAuthCert(ClientId serviceProvider,
            X509Certificate authCert, X509Certificate[] certs)
                    throws Exception {
        List<OCSPResp> ocspResponses;
        try {
            ocspResponses = getOcspResponses(authCert, serviceProvider, certs);
        } catch (CodedException e) {
            throw e.withPrefix(X_SSL_AUTH_FAILED);
        }

        CertHelper.verifyAuthCert(authCert, Arrays.asList(certs),
                ocspResponses, serviceProvider, ServerConf.getIdentifier());
    }

    /**
     * Gets OCSP responses for each certificate in the chain. If the OCSP
     * response is not locally available (cached), it will be retrieved
     * from the internal OCSP responder that is located at the service provider.
     */
    private static List<OCSPResp> getOcspResponses(X509Certificate authCert,
            ClientId serviceProvider, X509Certificate[] chain)
                    throws Exception {
        List<String> hashes = new ArrayList<>();
        List<OCSPResp> responses = new ArrayList<>();

        // Check for locally available OCSP responses
        for (X509Certificate cert : chain) {
            OCSPResp response = null;
            try {
                // Do we have a cached OCSP response for that cert?
                response = ServerConf.getOcspResponse(cert);
            } catch (CodedException e) {
                // Log it and continue; only thrown if the response could
                // not be loaded from a file -- not important to us here.
                LOG.warn("Cached OCSP response could not be found", e);
            }

            if (response != null) {
                responses.add(response);
            } else {
                // Did not find response locally, add the hash to be retrieved
                // from server.
                hashes.add(CryptoUtils.calculateCertHexHash(cert));
            }
        }

        // Retrieve OCSP responses for those certs whose responses
        // are not locally available, from ServerProxy
        if (!hashes.isEmpty()) {
            responses.addAll(getAndCacheOcspResponses(authCert,
                    serviceProvider, hashes));
        }

        return responses;
    }

    /**
     * Sends the GET request with all cert hashes which need OCSP responses.
     */
    private static List<OCSPResp> getAndCacheOcspResponses(
            X509Certificate authCert, ClientId serviceProvider,
            List<String> hashes) throws Exception {
        String address = GlobalConf.getProviderAddress(authCert);
        if (address == null || address.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER,
                    "Could not get address for " + serviceProvider);
        }

        List<OCSPResp> receivedResponses;
        try {
            receivedResponses = getOcspResponsesFromServer(address, hashes);
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
        for (int i = 0; i < receivedResponses.size(); i++) {
            ServerConf.setOcspResponse(hashes.get(i), receivedResponses.get(i));
        }

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
            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }
    }
}
