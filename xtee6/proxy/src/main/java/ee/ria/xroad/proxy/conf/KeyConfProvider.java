package ee.ria.xroad.proxy.conf;

import java.security.cert.X509Certificate;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.identifier.ClientId;

/**
 * Declares methods for accessing key configuration.
 */
public interface KeyConfProvider {

    /**
     * @return security (signing) context for given member.
     * @param memberId client ID of the member
     */
    SigningCtx getSigningCtx(ClientId memberId);

    /**
     * @return the current key and certificate for SSL authentication.
     */
    AuthKey getAuthKey();

    /**
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     * @param cert the certificate
     * @throws Exception in case of any errors
     */
    OCSPResp getOcspResponse(X509Certificate cert) throws Exception;

    /**
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     * @param certHash hash of the certificate
     * @throws Exception in case of any errors
     */
    OCSPResp getOcspResponse(String certHash) throws Exception;

    /**
     * @return OCSP responses for given certificates.
     * @param certs list of certificates
     * @throws Exception in case of any errors
     */
    List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception;

    /**
     * Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.
     * @param certs list of certificates
     * @param responses list of OCSP responses
     * @throws Exception in case of any errors
     */
    void setOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> responses) throws Exception;

}
