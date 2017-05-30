/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.ocsp;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.TimeBasedObjectCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.joda.time.DateTime;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_VALIDATION_INFO;
import static ee.ria.xroad.common.util.CryptoUtils.*;

/** Helper class for verifying OCSP responses. */
@Slf4j
public final class OcspVerifier {

    private static final String ID_KP_OCSPSIGNING = "1.3.6.1.5.5.7.3.9";

    private static final String SINGLE_RESP = "single_resp";
    private static final String SIGNATURE = "signature";
    private static final String CERTIFICATE = "certificate";

    private final int ocspFreshnessSeconds;

    private final OcspVerifierOptions options;

    private static final TimeBasedObjectCache CACHE = new TimeBasedObjectCache(SystemProperties
            .getOcspVerifierCachePeriod());;

    /**
     * Constructor
     */
    public OcspVerifier(int ocspFreshnessSeconds, OcspVerifierOptions options) {
        this.ocspFreshnessSeconds = ocspFreshnessSeconds;

        if (options == null) {
            this.options = new OcspVerifierOptions(true);
        } else {
            this.options = options;
        }
    }

    /**
     * Verifies certificate with respect to OCSP response.
     * @param response the OCSP response
     * @param subject the certificate to verify
     * @param issuer the issuer of the subject certificate
     * @throws Exception CodedException with appropriate error code
     * if verification fails or the status of OCSP is not good.
     */
    public void verifyValidityAndStatus(OCSPResp response,
            X509Certificate subject, X509Certificate issuer) throws Exception {
        verifyValidityAndStatus(response, subject, issuer, new Date());
    }

    /**
     * Verifies certificate with respect to OCSP response at a specified date
     * and checks the OCSP status
     * @param response the OCSP response
     * @param subject the certificate to verify
     * @param issuer the issuer of the subject certificate
     * @param atDate the date
     * @throws Exception CodedException with appropriate error code
     * if verification fails or the status of OCSP is not good.
     */
    public void verifyValidityAndStatus(OCSPResp response,
            X509Certificate subject, X509Certificate issuer, Date atDate)
                    throws Exception {
        verifyValidity(response, subject, issuer, atDate);
        verifyStatus(response);
    }

    /**
     * Verifies certificate with respect to OCSP response but does not
     * check the status of the OCSP response.
     * @param response the OCSP response
     * @param subject the certificate to verify
     * @param issuer the issuer of the subject certificate
     * @throws Exception CodedException with appropriate error code
     * if verification fails.
     */
    public void verifyValidity(OCSPResp response, X509Certificate subject,
            X509Certificate issuer) throws Exception {
        verifyValidity(response, subject, issuer, new Date());
    }

    /**
     * Verifies certificate with respect to OCSP response but does not
     * check the status of the OCSP response.
     * @param response the OCSP response
     * @param subject the certificate to getOcspCertverify
     * @param issuer the issuer of the subject certificate
     * @param atDate the date
     * @throws Exception CodedException with appropriate error code
     * if verification fails.
     */
    public void verifyValidity(OCSPResp response, X509Certificate subject,
            X509Certificate issuer, Date atDate) throws Exception {
        log.debug("verifyValidity(subject: {}, issuer: {}, atDate: {})",
                new Object[] {subject.getSubjectX500Principal().getName(),
                    issuer.getSubjectX500Principal().getName(), atDate});

        SingleResp singleResp = verifyResponseValidityCached(response, subject, issuer);
        verifyValidityAt(atDate, singleResp);
    }

    private void verifyValidityAt(Date atDate, SingleResp singleResp) {
        // 5. The time at which the status being indicated is known
        // to be correct (thisUpdate) is sufficiently recent.
        if (isExpired(singleResp, atDate)) {
            throw new CodedException(X_INCORRECT_VALIDATION_INFO,
                    "OCSP response is too old (thisUpdate: %s)",
                    singleResp.getThisUpdate());
        }

        if (options.isVerifyNextUpdate()) {
            // 6. When available, the time at or before which newer information will
            // be available about the status of the certificate (nextUpdate) is
            // greater than the current time.
            log.debug("Verify OCSP nextUpdate, atDate: {} nextUpdate: {}", atDate, singleResp.getNextUpdate());
            if (singleResp.getNextUpdate() != null
                    && singleResp.getNextUpdate().before(atDate)) {
                SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                throw new CodedException(X_INCORRECT_VALIDATION_INFO,
                        String.format("OCSP nextUpdate is too old, atDate: %s nextUpdate: %s", fmt.format(atDate),
                                fmt.format(singleResp.getNextUpdate())));
            }
        } else {
            log.debug("OCSP nextUpdate verification is turned off");
        }
    }

    private synchronized SingleResp verifyResponseValidityCached(OCSPResp response, X509Certificate subject,
                                                                 X509Certificate issuer)
            throws Exception {
        String key = SINGLE_RESP + response.hashCode() + subject.hashCode() + issuer.hashCode();
        if (!CACHE.isValid(key)) {
            CACHE.setValue(key, verifyResponseValidity(response, subject, issuer));
        }

        return (SingleResp) CACHE.getValue(key);
    }

    private SingleResp verifyResponseValidity(OCSPResp response, X509Certificate subject, X509Certificate issuer)
            throws Exception {
        BasicOCSPResp basicResp = (BasicOCSPResp) response.getResponseObject();
        SingleResp singleResp = basicResp.getResponses()[0];

        CertificateID requestCertId = createCertId(subject, issuer);

        // http://www.ietf.org/rfc/rfc2560.txt -- 3.2:
        // Prior to accepting a signed response as valid, OCSP clients
        // SHALL confirm that:

        // 1. The certificate identified in a received response corresponds to
        // that which was identified in the corresponding request;
        if (!singleResp.getCertID().equals(requestCertId)) {
            throw new CodedException(X_INCORRECT_VALIDATION_INFO,
                    "OCSP response does not apply to certificate (sn = %s)",
                    subject.getSerialNumber());
        }

        X509Certificate ocspCert = getOcspCert(basicResp);
        if (ocspCert == null) {
            throw new CodedException(X_INCORRECT_VALIDATION_INFO,
                    "Could not find OCSP certificate for responder ID");
        }

        if (!verifySignature(basicResp, ocspCert)) {
            throw new CodedException(X_INCORRECT_VALIDATION_INFO,
                    "Signature on OCSP response is not valid");
        }

        // 3. The identity of the signer matches the intended
        // recipient of the request.
        // -- Not important here because the original request is not available.

        // 4. The signer is currently authorized to sign the response.
        if (!isAuthorizedOcspSigner(ocspCert, issuer)) {
            throw new CodedException(X_INCORRECT_VALIDATION_INFO,
                    "OCSP responder is not authorized for given CA");
        }
        return singleResp;
    }

    private boolean verifySignature(BasicOCSPResp basicResp, X509Certificate ocspCert) throws OperatorCreationException,
            OCSPException {
        ContentVerifierProvider verifier =
                createDefaultContentVerifier(ocspCert.getPublicKey());

        return basicResp.isSignatureValid(verifier);
    }

    /**
     * Verifies the status of the OCSP response.
     * @param response the OCSP response
     * @throws Exception CodedException with error code X_CERT_VALIDATION
     * if status is not good.
     */
    public static void verifyStatus(OCSPResp response) throws Exception {
        BasicOCSPResp basicResp = (BasicOCSPResp) response.getResponseObject();
        SingleResp singleResp = basicResp.getResponses()[0];

        CertificateStatus status = singleResp.getCertStatus();
        if (status != null) { // null indicates GOOD.
            throw new CodedException(X_CERT_VALIDATION,
                    "OCSP response indicates certificate status is %s",
                    getStatusString(status));
        }
    }

    /**
     * Returns true if the OCSP response is about to expire at the given date.
     * @param singleResp the response
     * @param atDate the date
     * @return true, if the OCSP response is expired
     */
    public boolean isExpired(SingleResp singleResp, Date atDate) {
        Date allowedThisUpdate = new DateTime(atDate)
            .minusSeconds(ocspFreshnessSeconds).toDate();

        log.trace("isExpired(thisUpdate: {}, allowedThisUpdate: {}, "
                + "atDate: {})", new Object[] {singleResp.getThisUpdate(),
                        allowedThisUpdate, atDate });

        return singleResp.getThisUpdate().before(allowedThisUpdate);
    }

    /**
     * Returns true if the OCSP response is about to expire at the current date.
     * @param response the response
     * @return true, if the OCSP response is expired
     * @throws Exception if an error occurs
     */
    public boolean isExpired(OCSPResp response) throws Exception {
        BasicOCSPResp basicResp = (BasicOCSPResp) response.getResponseObject();
        SingleResp singleResp = basicResp.getResponses()[0];
        return isExpired(singleResp, new Date());
    }

    /**
     * Returns true if the OCSP response is about to expire at the
     * specified date.
     * @param response the response
     * @param atDate the date
     * @return true, if the OCSP response is expired at the specified date.
     * @throws Exception if an error occurs
     */
    public boolean isExpired(OCSPResp response, Date atDate) throws Exception {
        BasicOCSPResp basicResp = (BasicOCSPResp) response.getResponseObject();
        SingleResp singleResp = basicResp.getResponses()[0];
        return isExpired(singleResp, atDate);
    }

    /**
     * @param response the OCSP response
     * @return certificate that was used to sign the given OCSP response.
     * @throws Exception if an error occurs
     */
    public static X509Certificate getOcspCert(BasicOCSPResp response)
            throws Exception {
        List<X509Certificate> knownCerts = getOcspCerts(response);
        ResponderID respId = response.getResponderId().toASN1Primitive();

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
