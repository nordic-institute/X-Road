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
package org.niis.xroad.globalconf.impl.ocsp;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.util.TimeUtils;

import com.google.common.cache.Cache;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.test.globalconf.EmptyGlobalConf;

import java.lang.reflect.Field;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.common.core.exception.ErrorCode.CERT_VALIDATION;
import static org.niis.xroad.common.core.exception.ErrorCode.INCORRECT_VALIDATION_INFO;

/**
 * Tests the OCSP verifier.
 */
public class OcspVerifierTest {

    OcspVerifierFactory ocspVerifierFactory = new OcspVerifierFactory();

    static {
        TestSecurityUtil.initSecurity();
    }

    GlobalConfProvider globalConfProvider;
    X509Certificate subject;
    X509Certificate issuer;
    X509Certificate signer;
    PrivateKey signerKey;

    /**
     * Test that verifying OCSP response against an invalid certificate fails.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void errorCertMismatch() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);
        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, subject, subject))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getErrorCode()).isEqualTo(INCORRECT_VALIDATION_INFO.code()));
    }

    /**
     * Test that verifying OCSP response against an invalid response signature
     * fails.
     */
    @Test
    void errorInvalidResponseSignature() {
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD);

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, issuer, issuer))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getErrorCode()).isEqualTo(INCORRECT_VALIDATION_INFO.code()));
    }

    /**
     * Tests that verifying fails if signer info mismatches.
     */
    @Test
    void errorSignerIdentityMismatch() {
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                issuer, signerKey, CertificateStatus.GOOD);

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, issuer, signer))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getErrorCode()).isEqualTo(INCORRECT_VALIDATION_INFO.code()));
    }

    /**
     * Tests that verifying fails if signer is unauthorized.
     */
    @Test
    void errorSignerUnauthorized() {
        // we now sign the response with a cert that has been
        // issued by another CA
        X509Certificate anotherSignerCert = TestCertUtil.getCa2TestOrg().certChain[0];
        assertNotNull(anotherSignerCert);
        PrivateKey anotherSignerKey = TestCertUtil.getCa2TestOrg().key;
        assertNotNull(anotherSignerKey);

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                anotherSignerCert, anotherSignerKey, CertificateStatus.GOOD);

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, subject, issuer))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getErrorCode()).isEqualTo(INCORRECT_VALIDATION_INFO.code()));
    }

    /**
     * Tests that verifying fails if OCSP response thisUpdate is newer than now
     *
     * @throws Exception if an error occurs
     */
    @Test
    void errorThisUpdateAfterNow() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plusMillis(12345L));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, new Date());

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, subject, issuer))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getErrorCode()).isEqualTo(INCORRECT_VALIDATION_INFO.code()));
    }

    /**
     * Tests that verifying fails if OCSP response nextUpdate is older than now
     *
     * @throws Exception if an error occurs
     */
    @Test
    void errorNextUpdateBeforeNow() throws Exception {
        Date nextUpdate = Date.from(TimeUtils.now().minusMillis(12345L));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                new Date(), nextUpdate);

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, subject, issuer))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getErrorCode()).isEqualTo(INCORRECT_VALIDATION_INFO.code()));
    }

    /**
     * Tests that verifying does not fail if OCSP response nextUpdate is before now and nextUpdate
     * verification is turned off.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void nextUpdateBeforeNow() throws Exception {
        Date nextUpdate = Date.from(TimeUtils.now().minusMillis(12345L));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                new Date(), nextUpdate);
        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(false));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying succeeds if certificate status is good.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void certStatusGood() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, null);

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying succeeds if certificate status is revoked.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void certStatusRevoked() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey,
                new RevokedStatus(new Date(), CRLReason.unspecified),
                thisUpdate, null);

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, subject, issuer))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getFaultCode()).isEqualTo(CERT_VALIDATION.code()));
    }

    /**
     * Tests that verifying succeeds if certificate status is unknown.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void certStatusUnknown() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, new UnknownStatus(),
                thisUpdate, null);

        var verifier = ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        assertThatThrownBy(() -> verifier.verifyValidityAndStatus(ocsp, subject, issuer))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        ce -> assertThat(ce.getFaultCode()).isEqualTo(CERT_VALIDATION.code()));
    }

    @Test
    void responseValidityCache() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, null);

        OcspVerifier verifier =
                ocspVerifierFactory.create(globalConfProvider, new OcspVerifierOptions(true));
        verifier.verifyValidity(ocsp, subject, issuer);
        Field field = OcspVerifierFactory.class.getDeclaredField("responseValidityCache");
        field.setAccessible(true);
        Cache<String, SingleResp> cache = (Cache<String, SingleResp>) field.get(ocspVerifierFactory);
        assertTrue(cache != null && cache.size() > 0, "Cache should be filled");
    }

    /**
     * Loads the test certificates.
     *
     * @throws Exception if an error occurs
     */
    @BeforeEach
    public void loadCerts() {
        globalConfProvider = new TestGlobalConf();

        if (issuer == null) {
            issuer = TestCertUtil.getCertChainCert("root_ca.p12");
            assertNotNull(issuer);

            signer = issuer;
            signerKey = TestCertUtil.getCertChainKey("root_ca.p12");
            assertNotNull(signerKey);
        }

        if (subject == null) {
            subject = TestCertUtil.getCertChainCert("user_0.p12");
            assertNotNull(subject);
        }
    }

    private final class TestGlobalConf extends EmptyGlobalConf {

        @Override
        public boolean isOcspResponderCert(X509Certificate ca, X509Certificate ocspCert) {
            return false;
        }

        @Override
        public List<X509Certificate> getOcspResponderCertificates() {
            return Collections.singletonList(signer);
        }

        @Override
        public X509Certificate getCaCert(String instanceIdentifier, X509Certificate orgCert) {
            return TestCertUtil.getCaCert();
        }
    }
}
