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
package ee.ria.xroad.common.ocsp;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.util.TimeUtils;

import com.google.common.cache.Cache;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_VALIDATION_INFO;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the OCSP verifier.
 */
public class OcspVerifierTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    static {
        TestSecurityUtil.initSecurity();
    }

    X509Certificate subject;
    X509Certificate issuer;
    X509Certificate signer;
    PrivateKey signerKey;

    /**
     * Test that verifying OCSP response against an invalid certificate fails.
     * @throws Exception if an error occurs
     */
    @Test
    public void errorCertMismatch() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, subject);
    }

    /**
     * Test that verifying OCSP response against an invalid response signature
     * fails.
     * @throws Exception if an error occurs
     */
    @Test
    public void errorInvalidResponseSignature() throws Exception {
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, issuer, issuer);
    }

    /**
     * Tests that verifying fails if signer info mismatches.
     * @throws Exception if an error occurs
     */
    @Test
    public void errorSignerIdentityMismatch() throws Exception {
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                issuer, signerKey, CertificateStatus.GOOD);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, issuer, signer);
    }

    /**
     * Tests that verifying fails if signer is unauthorized.
     * @throws Exception if an error occurs
     */
    @Test
    public void errorSignerUnauthorized() throws Exception {
        // we now sign the response with a cert that has been
        // issued by another CA
        X509Certificate anotherSignerCert = TestCertUtil.getCa2TestOrg().certChain[0];
        assertNotNull(anotherSignerCert);
        PrivateKey anotherSignerKey = TestCertUtil.getCa2TestOrg().key;
        assertNotNull(anotherSignerKey);

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                anotherSignerCert, anotherSignerKey, CertificateStatus.GOOD);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying fails if OCSP response thisUpdate is newer than now
     * @throws Exception if an error occurs
     */
    @Test
    public void errorThisUpdateAfterNow() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plusMillis(12345L));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, new Date());

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying fails if OCSP response nextUpdate is older than now
     * @throws Exception if an error occurs
     */
    @Test
    public void errorNextUpdateBeforeNow() throws Exception {
        Date nextUpdate = Date.from(TimeUtils.now().minusMillis(12345L));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                new Date(), nextUpdate);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying does not fail if OCSP response nextUpdate is before now and nextUpdate
     * verification is turned off.
     * @throws Exception if an error occurs
     */
    @Test
    public void nextUpdateBeforeNow() throws Exception {
        Date nextUpdate = Date.from(TimeUtils.now().minusMillis(12345L));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                new Date(), nextUpdate);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(false));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying succeeds if certificate status is good.
     * @throws Exception if an error occurs
     */
    @Test
    public void certStatusGood() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, null);

        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying succeeds if certificate status is revoked.
     * @throws Exception if an error occurs
     */
    @Test
    public void certStatusRevoked() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey,
                new RevokedStatus(new Date(), CRLReason.unspecified),
                thisUpdate, null);

        thrown.expectError(X_CERT_VALIDATION);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    /**
     * Tests that verifying succeeds if certificate status is unknown.
     * @throws Exception if an error occurs
     */
    @Test
    public void certStatusUnknown() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, new UnknownStatus(),
                thisUpdate, null);

        thrown.expectError(X_CERT_VALIDATION);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    @Test
    public void responseValidityCache() throws Exception {
        Date thisUpdate = Date.from(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, null);

        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(), new OcspVerifierOptions(true));
        verifier.verifyValidity(ocsp, subject, issuer);
        Field field = OcspVerifier.class.getDeclaredField("RESPONSE_VALIDITY_CACHE");
        field.setAccessible(true);
        Cache<String, SingleResp> cache = (Cache<String, SingleResp>)field.get(verifier);
        assertTrue("Cache should be filled", cache != null && cache.size() > 0);

    }

    /**
     * Loads the test certificates.
     * @throws Exception if an error occurs
     */
    @Before
    public void loadCerts() {
        GlobalConf.reload(new TestGlobalConf());

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

    private class TestGlobalConf extends EmptyGlobalConf {

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
