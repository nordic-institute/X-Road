package ee.cyber.sdsb.common.cert;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.CertUtils;
import ee.cyber.sdsb.common.util.CryptoUtils;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Certificate-related helper functions.
 */
public class CertHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CertHelper.class);

    /**
     * Builds certificate chain from cert to trusted root.
     * @param additionalCerts additional certificates that can be used to
     *                   construct the cert chain.
     */
    public static CertChain buildChain(X509Certificate cert,
            List<X509Certificate> additionalCerts) {
        try {
            X509Certificate trustAnchor = GlobalConf.getCaCert(cert);
            if (trustAnchor == null) {
                throw new Exception("Unable to find trust anchor");
            }

            List<X509Certificate> trustedCerts = Arrays.asList(trustAnchor);
            return new CertChain(cert, trustedCerts, additionalCerts);
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }

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
    public static void verifyAuthCert(X509Certificate cert,
            List<X509Certificate> additionalCerts,
            List<OCSPResp> ocspResponses, ClientId member,
            SecurityServerId securityServer) throws Exception {
        LOG.debug("verifyAuthCert({}: {}, {}, {})",
                new Object[] { cert.getSerialNumber(),
                        cert.getSubjectX500Principal().getName(), member,
                        securityServer});

        // Verify certificate against CAs.
        try {
            CertChain chain = buildChain(cert, additionalCerts);
            chain.verify(ocspResponses, new Date());
        } catch (CodedException e) {
            // meaningful errors get SSL auth verification prefix
            throw e.withPrefix(X_SSL_AUTH_FAILED);
        }

        // Verify (using GlobalConf) that given certificate can be used
        // to authenticate given member.
        if (!GlobalConf.authCertMatchesMember(cert, member)) {
            if (GlobalConf.hasAuthCert(cert, securityServer)) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client '%s' is not registered at security server '%s'",
                        member, securityServer);
            }

            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Authentication certificate %s is not associated " +
                    "with any security serves", cert.getSerialNumber());
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
