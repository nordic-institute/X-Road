package ee.cyber.sdsb.common;

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

import ee.cyber.sdsb.common.util.CryptoUtils;


public final class OcspTestUtils {

    public static OCSPResp createOCSPResponse(X509Certificate subject,
            X509Certificate issuer,
            X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus) throws Exception {
        return createOCSPResponse(subject, issuer, signer, signerKey,
                certStatus, null, null);
    }

    public static OCSPResp createOCSPResponse(CertificateID cid,
            X509Certificate issuer,
            X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus, Date thisUpdate, Date nextUpdate)
            throws Exception {
        return createOCSPResponse(null, cid, issuer, signer, signerKey,
                certStatus, thisUpdate, nextUpdate);
    }

    public static OCSPResp createOCSPResponse(X509Certificate subject,
            X509Certificate issuer,
            X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus, Date thisUpdate, Date nextUpdate)
                    throws Exception {
        return createOCSPResponse(subject, null, issuer, signer, signerKey,
                certStatus, thisUpdate, nextUpdate);
    }

    private static OCSPResp createOCSPResponse(X509Certificate subject,
            CertificateID cid, X509Certificate issuer,
            X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus, Date thisUpdate, Date nextUpdate)
            throws Exception {
        BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(
                new RespID(new X500Name(
                        signer.getSubjectX500Principal().getName())));
        if (cid == null) {
            cid = CryptoUtils.createCertId(subject, issuer);
        }

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

    public static OCSPResp createSigRequiredOCSPResponse() throws Exception {
        OCSPResp resp = new OCSPRespBuilder().build(
                OCSPRespBuilder.SIG_REQUIRED, null);
        return resp;
    }

}
