package ee.cyber.xroad.common.ocsp;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.joda.time.DateTime;

import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.xroad.common.CodedException;
import ee.cyber.xroad.common.ErrorCodes;

import static ee.cyber.xroad.common.util.CryptoUtils.*;

/** Helper class for verifying OCSP responses. */
public final class OcspVerifier {

    private static final String ID_KP_OCSPSIGNING = "1.3.6.1.5.5.7.3.9";

    /**
     * Verifies certificate with respect to OCSP response.
     * @param response the OCSP response
     * @param subject the certificate to verify
     * @param issuer the issuer of the subject certificate
     * @throws CodedException with appropriate error code
     * if verification fails.
     */
    public static void verify(OCSPResp response, X509Certificate subject,
            X509Certificate issuer) throws Exception {
        verify(response, subject, issuer, new Date());
    }

    /**
     * Verifies certificate with respect to OCSP response at a specified date.
     * @param response the OCSP response
     * @param subject the certificate to verify
     * @param issuer the issuer of the subject certificate
     * @param atDate the date
     * @throws CodedException with appropriate error code
     * if verification fails.
     */
    public static void verify(OCSPResp response, X509Certificate subject,
            X509Certificate issuer, Date atDate) throws Exception {
        BasicOCSPResp basicResp = (BasicOCSPResp) response.getResponseObject();
        SingleResp singleResp = basicResp.getResponses()[0];

        CertificateID requestCertId = createCertId(subject, issuer);

        // http://www.ietf.org/rfc/rfc2560.txt -- 3.2:
        // Prior to accepting a signed response as valid, OCSP clients
        // SHALL confirm that:

        // 1. The certificate identified in a received response corresponds to
        // that which was identified in the corresponding request;
        if (!singleResp.getCertID().equals(requestCertId)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_VALIDATION_INFO,
                    "OCSP response does not apply to certificate (sn = %s)",
                    subject.getSerialNumber());
        }

        // 2. The signature on the response is valid.
        X509Certificate ocspCert = getOcspCert(basicResp);
        if (ocspCert == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_VALIDATION_INFO,
                    "Could not find OCSP certificate for responder ID");
        }

        ContentVerifierProvider verifier =
                createDefaultContentVerifier(ocspCert.getPublicKey());

        if (!basicResp.isSignatureValid(verifier)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_VALIDATION_INFO,
                    "Signature on OCSP response is not valid");
        }

        // 3. The identity of the signer matches the intended
        // recipient of the request.
        // -- Not important here because the original request is not available.

        // 4. The signer is currently authorized to sign the response.
        if (!isAuthorizedOcspSigner(ocspCert, issuer)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_VALIDATION_INFO,
                    "OCSP responder is not authorized for given CA");
        }

        // 5. The time at which the status being indicated is known
        // to be correct (thisUpdate) is sufficiently recent.
        if (isExpired(singleResp, atDate)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_VALIDATION_INFO,
                    "OCSP response is too old");
        }

        // 6. When available, the time at or before which newer information will
        // be available about the status of the certificate (nextUpdate) is
        // greater than the current time.
        if (singleResp.getNextUpdate() != null
                && singleResp.getNextUpdate().before(atDate)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_VALIDATION_INFO,
                    "OCSP response is too old: newer information is available");
        }

        // Finally check the status for the response
        final CertificateStatus status = singleResp.getCertStatus();
        // null indicates GOOD.
        if (status != null) {
            throw new CodedException(ErrorCodes.X_CERT_VALIDATION,
                    "OCSP response indicates certificate status is %s",
                    getStatusString(status));
        }
    }

    public static boolean isExpired(SingleResp singleResp, Date atDate) {
        Date allowedThisUpdate = new DateTime(atDate).minusMinutes(
                GlobalConf.getOcspFreshnessSeconds(true)).toDate();
        return singleResp.getThisUpdate().before(allowedThisUpdate);
    }

    /**
     * Returns certificate that was used to sign the given OCSP response.
     */
    public static X509Certificate getOcspCert(BasicOCSPResp response)
            throws Exception {
        List<X509Certificate> knownCerts = getOcspCerts(response);
        ResponderID respId = response.getResponderId().toASN1Object();

        // We can search either by key hash or by name, depending which
        // one is provided in the responder ID.
        if (respId.getName() != null) {
            for (X509Certificate cert : knownCerts) {
                X509CertificateHolder certHolder =
                        new X509CertificateHolder(cert.getEncoded());
                if (certHolder.getSubject().equals(respId.getName())) {
                    return cert;
                }
            }
        } else if (respId.getKeyHash() != null) {
            DigestCalculator dc = createDigestCalculator(SHA1_ID);
            for (X509Certificate cert : knownCerts) {
                X509CertificateHolder certHolder =
                        new X509CertificateHolder(cert.getEncoded());
                DERBitString keyData =
                        certHolder.getSubjectPublicKeyInfo().getPublicKeyData();
                byte[] d = calculateDigest(dc, keyData.getBytes());
                if (MessageDigestAlgorithm.isEqual(respId.getKeyHash(), d)) {
                    return cert;
                }
            }
        }

        return null;
    }

    private static List<X509Certificate> getOcspCerts(BasicOCSPResp response)
            throws Exception {
        List<X509Certificate> certs = new ArrayList<>();

        certs.addAll(GlobalConf.getOcspResponderCertificates());
        certs.addAll(GlobalConf.getAllCaCerts());

        for (X509CertificateHolder cert : response.getCerts()) {
            certs.add(readCertificate(cert.getEncoded()));
        }

        return certs;
    }

    private static String getStatusString(CertificateStatus status) {
        if (status instanceof UnknownStatus) {
            return "UNKNOWN";
        } else if (status instanceof RevokedStatus) {
            RevokedStatus rs = (RevokedStatus) status;
            return String.format("REVOKED (date: %tF %tT)",
                    rs.getRevocationTime(), rs.getRevocationTime());
        } else {
            return "INVALID";
        }

    }

    private static boolean isAuthorizedOcspSigner(X509Certificate ocspCert,
            X509Certificate issuer) throws Exception {
        // 1. Matches a local configuration of OCSP signing authority for the
        // certificate in question; or
        if (GlobalConf.isOcspResponderCert(issuer, ocspCert)) {
            return true;
        }

        // 2. Is the certificate of the CA that issued the certificate in
        // question; or
        if (ocspCert.equals(issuer)) {
            return true;
        }

        // 3. Includes a value of id-ad-ocspSigning in an ExtendedKeyUsage
        // extension and is issued by the CA that issued the certificate in
        // question.
        if (ocspCert.getIssuerX500Principal().equals(
                issuer.getSubjectX500Principal())) {
            List<String> keyUsage = ocspCert.getExtendedKeyUsage();
            if (keyUsage == null) {
                return false;
            }
            for (String key : keyUsage) {
                if (key.equals(ID_KP_OCSPSIGNING)) {
                    return true;
                }
            }
        }

        return false;
    }
}
