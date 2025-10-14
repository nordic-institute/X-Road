/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.globalconf.impl.cert;

import ee.ria.xroad.common.CodedException;

import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierOptions;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorResult;
import java.security.cert.CertStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_CERT_PATH;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CERT_PATH_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static org.niis.xroad.globalconf.impl.cert.CertHelper.getOcspResponseForCert;

/**
 * Certificate chain verifier.
 */
public class CertChainVerifier {

    /**
     * Default validation algorithm type is PKIX.
     */
    private static final String VALIDATION_ALGORITHM = "PKIX";

    private final GlobalConfProvider globalConfProvider;

    /**
     * Holds the PKIX algorithm parameters.
     */
    private final PKIXBuilderParameters pkixParams;

    /**
     * Holds the constructed certificate path.
     */
    private CertPath certPath;

    /**
     * Holds the cert chain to be verified.
     */
    private final CertChain certChain;
    private final OcspVerifierFactory ocspVerifierFactory;

    /**
     * Builds the certificate path for the target certificate using a list
     * of trust anchors and a list of intermediate certificates.
     *
     * @param globalConfProvider the global configuration provider
     * @param certChain          the certificate chain object
     */
    public CertChainVerifier(GlobalConfProvider globalConfProvider, OcspVerifierFactory ocspVerifierFactory, CertChain certChain) {
        this.globalConfProvider = globalConfProvider;
        this.ocspVerifierFactory = ocspVerifierFactory;
        this.certChain = certChain;

        Set<TrustAnchor> trustAnchors =
                createTrustAnchorSet(
                        Arrays.asList(certChain.getTrustedRootCert()));

        X509CertSelector certSelector = new X509CertSelector();
        certSelector.setCertificate(certChain.getEndEntityCert());

        try {
            pkixParams = new PKIXBuilderParameters(trustAnchors, certSelector);
            pkixParams.setRevocationEnabled(false);

            if (!certChain.getAdditionalCerts().isEmpty()) {
                CertStore intermediateCertStore =
                        CertStore.getInstance("Collection",
                                new CollectionCertStoreParameters(
                                        certChain.getAdditionalCerts()), BOUNCY_CASTLE);
                pkixParams.addCertStore(intermediateCertStore);
            }
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, e);
        }
    }

    /**
     * @return certificates in the chain, starting from the target certificate
     * and ending with the certificate issued by the trust anchor.
     */
    @SuppressWarnings("unchecked")
    public List<X509Certificate> getCerts() {
        // By using the validation algorithm PKIX,
        // we get a list of x509 certificates
        return (List<X509Certificate>) certPath.getCertificates();
    }

    /**
     * Similar to verify(), but does not check validity of the certificates.
     *
     * @param atDate the date at which to verify the chain
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
     *
     * @param ocspResponses list of OCSP responses that are used to
     *                      validate the certificates.
     * @param atDate        The date at which the verification is performed.
     */
    public void verify(List<OCSPResp> ocspResponses, Date atDate) {
        if (ocspResponses == null || ocspResponses.isEmpty()) {
            throw new IllegalArgumentException(
                    "List of OCSP responses cannot be null or empty");
        }

        verifyImpl(ocspResponses, atDate);
    }

    private void verifyImpl(List<OCSPResp> ocspResponses, Date atDate) {
        pkixParams.setDate(atDate);
        try {
            certPath = buildCertPath(pkixParams);

            verifyCertPath(certPath, pkixParams);

            if (ocspResponses != null) {
                verifyOcspResponses(getCerts(), ocspResponses, atDate);
            }
        } catch (CertPathBuilderException ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        } catch (Exception ex) {
            throw translateWithPrefix(X_INVALID_CERT_PATH_X, ex);
        }
    }

    public void verifyOcspResponses(List<OCSPResp> ocspResponses,
                                     Date atDate) {
        pkixParams.setDate(atDate);
        try {
            certPath = buildCertPath(pkixParams);
            verifyOcspResponses(getCerts(), ocspResponses, atDate);
        } catch (CertPathBuilderException ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        } catch (Exception ex) {
            throw translateWithPrefix(X_INVALID_CERT_PATH_X, ex);
        }
    }

    private void verifyOcspResponses(List<X509Certificate> certs,
                                     List<OCSPResp> ocspResponses,
                                     Date atDate) throws CertificateEncodingException,
            IOException, OCSPException, OperatorCreationException {
        for (X509Certificate subject : certs) {
            X509Certificate issuer = globalConfProvider.getCaCert(certChain.getInstanceIdentifier(), subject);
            OCSPResp response = getOcspResponseForCert(subject, issuer, ocspResponses);
            if (response == null) {
                throw new CodedException(X_CERT_VALIDATION,
                        "Unable to find OCSP response for certificate " + subject.getSubjectX500Principal().getName());
            }

            OcspVerifier verifier = ocspVerifierFactory.create(globalConfProvider,
                    new OcspVerifierOptions(globalConfProvider.getGlobalConfExtensions().shouldVerifyOcspNextUpdate()));
            verifier.verifyValidityAndStatus(response, subject, issuer, atDate);
        }
    }

    private static CertPath buildCertPath(PKIXBuilderParameters pkixParams)
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, CertPathBuilderException {
        CertPathBuilder certPathBuilder =
                CertPathBuilder.getInstance(VALIDATION_ALGORITHM);

        // We now attempt to build the certificate path from the
        // target certificate to the trusted root certificate.
        return certPathBuilder.build(pkixParams).getCertPath();
    }

    private static PKIXCertPathValidatorResult verifyCertPath(CertPath certPath, PKIXBuilderParameters pkixParams)
            throws NoSuchAlgorithmException, CertPathValidatorException, InvalidAlgorithmParameterException {
        CertPathValidator certPathValidator =
                CertPathValidator.getInstance(VALIDATION_ALGORITHM);

        CertPathValidatorResult result =
                certPathValidator.validate(certPath, pkixParams);

        return (PKIXCertPathValidatorResult) result;
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
