package ee.ria.xroad.common;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.operator.ContentSigner;

import ee.ria.xroad.common.util.CryptoUtils;

/**
 * Contains utility methods for creating test OCSP responses.
 */
public final class OcspTestUtils {

    private OcspTestUtils() {
    }

    /**
     * Creates an OCSP response for the subject's certificate with the given status.
     * @param subject the subject certificate
     * @param issuer certificate of the subject certificate issuer
     * @param signer certificate of the OCSP response signer
     * @param signerKey key of the OCSP response signer
     * @param certStatus OCSP response status
     * @return OCSPResp
     * @throws Exception in case of any errors
     */
    public static OCSPResp createOCSPResponse(X509Certificate subject,
            X509Certificate issuer,
            X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus) throws Exception {
        return createOCSPResponse(subject, issuer, signer, signerKey,
                certStatus, null, null);
    }

    /**
     * Creates an OCSP response for the subject's certificate with the given status.
     * @param subject the subject certificate
     * @param issuer certificate of the subject certificate issuer
     * @param signer certificate of the OCSP response signer
     * @param signerKey key of the OCSP response signer
     * @param certStatus OCSP response status
     * @param thisUpdate date this response was valid on
     * @param nextUpdate date when next update should be requested
     * @return OCSPResp
     * @throws Exception in case of any errors
     */
    public static OCSPResp createOCSPResponse(X509Certificate subject,
            X509Certificate issuer, X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus, Date thisUpdate, Date nextUpdate)
                    throws Exception {
        BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(
                new RespID(new X500Name(
                        signer.getSubjectX500Principal().getName())));
        CertificateID cid = CryptoUtils.createCertId(subject, issuer);

        if (thisUpdate != null) {
            builder.addResponse(cid, certStatus, thisUpdate, nextUpdate, null);
        } else {
            builder.addResponse(cid, certStatus);
        }

        ContentSigner contentSigner = CryptoUtils.createContentSigner(
                subject.getSigAlgName(), signerKey);

        Object responseObject = builder.build(contentSigner, null, new Date());

        OCSPResp resp = new OCSPRespBuilder().build(
                OCSPRespBuilder.SUCCESSFUL, responseObject);
        return resp;
    }

    /**
     * Creates a "signature required" OCSP response.
     * @return OCSPResp
     * @throws Exception in case of any errors
     */
    public static OCSPResp createSigRequiredOCSPResponse() throws Exception {
        OCSPResp resp = new OCSPRespBuilder().build(
                OCSPRespBuilder.SIG_REQUIRED, null);
        return resp;
    }

}
