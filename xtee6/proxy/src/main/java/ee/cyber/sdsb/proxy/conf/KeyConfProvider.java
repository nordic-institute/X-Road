package ee.cyber.sdsb.proxy.conf;

import java.security.cert.X509Certificate;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.identifier.ClientId;

public interface KeyConfProvider {

    /**
     * @return security (signing) context for given member.
     */
    SigningCtx getSigningCtx(ClientId memberId);

    /**
     * @return the current key and certificate for SSL authentication.
     */
    AuthKey getAuthKey();

    /**
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     */
    OCSPResp getOcspResponse(X509Certificate cert) throws Exception;

    /**
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     */
    OCSPResp getOcspResponse(String certHash) throws Exception;

    /**
     * @return OCSP responses for given certificate hashes.
     */
    List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception;

    /**
     * Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.
     */
    void setOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> responses) throws Exception;

}
