package ee.cyber.sdsb.common.cert;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.CertUtils;
import ee.cyber.sdsb.common.util.CryptoUtils;

import static ee.cyber.sdsb.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Certificate-related helper functions.
 */
@Slf4j
public class CertHelper {

    /**
     * Returns short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    public static String getSubjectCommonName(X509Certificate cert) {
        return CertUtils.getSubjectCommonName(cert);
    }

    /** Returns the SerialNumber component from the Subject field. */
    public static String getSubjectSerialNumber(X509Certificate cert) {
        return CertUtils.getSubjectSerialNumber(cert);
    }

    /** Returns a fully constructed Client identifier from DN of the certificate. */
    public static ClientId getSubjectClientId(X509Certificate cert) {
        return CertUtils.getSubjectClientId(cert);
    }

    /**
     * Verifies that the certificate <cert>cert</cert> can be used for
     * authenticating as member <code>member</code>.
     * The <code>ocspResponsec</code> is used to verify validity of the
     * certificate.
     * Throws exception if verification fails.
     */
    public static void verifyAuthCert(CertChain chain,
            List<OCSPResp> ocspResponses, ClientId member) throws Exception {
        X509Certificate cert = chain.getEndEntityCert();

        log.debug("verifyAuthCert({}: {}, {})",
                new Object[] { cert.getSerialNumber(),
                        cert.getSubjectX500Principal().getName(), member });

        // Verify certificate against CAs.
        try {
            new CertChainVerifier(chain).verify(ocspResponses, new Date());
        } catch (CodedException e) {
            // meaningful errors get SSL auth verification prefix
            throw e.withPrefix(X_SSL_AUTH_FAILED);
        }

        // Verify (using GlobalConf) that given certificate can be used
        // to authenticate given member.
        if (!GlobalConf.authCertMatchesMember(cert, member)) {
            if (!GlobalConf.hasAuthCert(cert, null)) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Authentication certificate %s is not associated " +
                        "with any security servers", cert.getSerialNumber());
            }

            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Client '%s' is not registered at target security server",
                    member);
        }
    }

    /**
     * Finds OCSP response for a given certificate.
     */
    public static OCSPResp getOcspResponseForCert(X509Certificate cert,
            X509Certificate issuer, List<OCSPResp> ocspResponses)
            throws Exception {
        CertificateID certId = CryptoUtils.createCertId(cert, issuer);
        for (OCSPResp resp : ocspResponses) {
            BasicOCSPResp basicResp = (BasicOCSPResp) resp.getResponseObject();
            SingleResp singleResp = basicResp.getResponses()[0];
            if (certId.equals(singleResp.getCertID())) {
                return resp;
            }
        }

        return null;
    }
}
