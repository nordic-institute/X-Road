package ee.ria.xroad_legacy.common.cert;

import java.security.cert.*;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad_legacy.common.CodedException;
import ee.ria.xroad_legacy.common.ocsp.OcspVerifier;

import static ee.ria.xroad.common.cert.CertHelper.getOcspResponseForCert;
import static ee.ria.xroad_legacy.common.ErrorCodes.*;

public class CertChain {
    /** Default validation algorithm type is PKIX. */
    private static final String VALIDATION_ALGORITHM = "PKIX";

    /** Holds the PKIX algorithm parameters. */
    private PKIXBuilderParameters pkixParams;

    /** Holds the constructed certificate path. */
    private CertPath certPath;

    /**
     * Builds the certificate path for the target certificate using a list
     * of trust anchors and a list of intermediate certificates.
     * @param cert the target certificate
     * @param trustedCerts the list of trust anchors
     * @param additionalCerts a list of intermediate certificates
     */
    public CertChain(X509Certificate cert, List<X509Certificate> trustedCerts,
            List<X509Certificate> additionalCerts) throws Exception {
        Set<TrustAnchor> trustAnchors = createTrustAnchorSet(trustedCerts);

        X509CertSelector certSelector = new X509CertSelector();
        certSelector.setCertificate(cert);

        pkixParams = new PKIXBuilderParameters(trustAnchors, certSelector);
        pkixParams.setRevocationEnabled(false);

        if (additionalCerts != null && !additionalCerts.isEmpty()) {
            CertStore intermediateCertStore =
                    CertStore.getInstance("Collection",
                            new CollectionCertStoreParameters(
                                    additionalCerts), "BC");
            pkixParams.addCertStore(intermediateCertStore);
        }
    }

    /** Returns certificates in the chain, starting from the target certificate
     * and ending with the certificate issued by the trust anchor. */
    @SuppressWarnings("unchecked")
    public List<X509Certificate> getCerts() {
        // By using the validation algorithm PKIX,
        // we get a list of x509 certificates
        return (List<X509Certificate>) certPath.getCertificates();
    }

    /**
     * Similar to verify(), but does not check validity of the certificates.
     */
    public void verifyChainOnly(Date atDate) {
        verifyImpl(null, atDate);
    }

    /**
     * Verifies the certificate chain. First it attempts to build the chain
     * from the certificate to the trusted root certificate, using additional
     * intermediate certificates if provided. Then the certificate path is
     * validated. Lastly, for each certificate in the chain, the corresponding
     * OCSP response is found and verified.
     * If verification fails, throws CodedException with error code
     * InvalidCertPath...
     * @param ocspResponses list of OCSP responses that are used to
     *                      validate the certificates.
     * @param atDate The date at which the verification is performed.
     */
    public void verify(List<OCSPResp> ocspResponses, Date atDate) {
        if (ocspResponses == null) {
            throw new IllegalArgumentException(
                    "List of OCSP responses cannot be null");
        }
        verifyImpl(ocspResponses, atDate);
    }

    private void verifyImpl(List<OCSPResp> ocspResponses, Date atDate) {
        pkixParams.setDate(atDate);
        try {
            certPath = buildCertPath(pkixParams);

            PKIXCertPathValidatorResult pkixResult =
                    verifyCertPath(certPath, pkixParams);

            if (ocspResponses != null) {
                verifyOcspResponses(
                        getCerts(), ocspResponses, pkixResult, atDate);
            }
        } catch (CertPathBuilderException ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        } catch (Exception ex) {
            throw translateWithPrefix(X_INVALID_CERT_PATH_X, ex);
        }
    }

    private static CertPath buildCertPath(PKIXBuilderParameters pkixParams)
            throws Exception {
        CertPathBuilder certPathBuilder =
                CertPathBuilder.getInstance(VALIDATION_ALGORITHM);

        // We now attempt to build the certificate path from the
        // target certificate to the trusted root certificate.
        return certPathBuilder.build(pkixParams).getCertPath();
    }

    private static PKIXCertPathValidatorResult verifyCertPath(CertPath certPath,
            PKIXBuilderParameters pkixParams) throws Exception {
        CertPathValidator certPathValidator =
                CertPathValidator.getInstance(VALIDATION_ALGORITHM);

        CertPathValidatorResult result =
                certPathValidator.validate(certPath, pkixParams);

        return (PKIXCertPathValidatorResult) result;
    }

    private static void verifyOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> ocspResponses, PKIXCertPathValidatorResult result,
            Date atDate) throws Exception {
        for (X509Certificate subject : certs) {
            X509Certificate issuer =
                    GlobalConf.getCaCert(GlobalConf.getInstanceIdentifier(),
                            subject);
            OCSPResp response =
                    getOcspResponseForCert(subject, issuer, ocspResponses);
            if (response == null) {
                throw new CodedException(X_CERT_VALIDATION,
                        "Unable to find OCSP response for certificate "
                        + subject.getSubjectX500Principal().getName());
            }

            OcspVerifier.verify(response, subject, issuer, atDate);
        }
    }

    private static Set<TrustAnchor> createTrustAnchorSet(
            List<X509Certificate> trustedRootCerts) {
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        for (X509Certificate trustedRootCert : trustedRootCerts) {
            trustAnchors.add(new TrustAnchor(
                    trustedRootCert, null/* No name constraints */));
        }

        return trustAnchors;
    }

}
