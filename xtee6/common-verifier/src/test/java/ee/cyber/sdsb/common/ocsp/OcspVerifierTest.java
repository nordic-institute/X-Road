package ee.cyber.sdsb.common.ocsp;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.EmptyGlobalConf;
import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestSecurityUtil;
import ee.cyber.sdsb.common.conf.GlobalConf;

import static ee.cyber.sdsb.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.cyber.sdsb.common.ErrorCodes.X_INCORRECT_VALIDATION_INFO;
import static org.junit.Assert.assertNotNull;

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

    @Test
    public void errorCertMismatch() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier.verify(ocsp, subject, subject);
    }

    @Test
    public void errorInvalidResponseSignature() throws Exception {
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier.verify(ocsp, issuer, issuer);
    }

    @Test
    public void errorSignerIdentityMismatch() throws Exception {
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                issuer, signerKey, CertificateStatus.GOOD);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier.verify(ocsp, issuer, signer);
    }

    @Test
    public void errorSignerUnauthorized() throws Exception {
        // we now sign the response with a cert that has been
        // issued by another CA
        X509Certificate anotherSignerCert = TestCertUtil.getCa2TestOrg().cert;
        assertNotNull(anotherSignerCert);
        PrivateKey anotherSignerKey = TestCertUtil.getCa2TestOrg().key;
        assertNotNull(anotherSignerKey);

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                anotherSignerCert, anotherSignerKey, CertificateStatus.GOOD);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier.verify(ocsp, issuer, anotherSignerCert);
    }

    @Test
    public void errorThisUpdateAfterNow() throws Exception {
        Date thisUpdate = new DateTime().plus(12345L).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, new Date());

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier.verify(ocsp, subject, issuer);
    }

    @Test
    public void errorNextUpdateBeforeNow() throws Exception {
        Date nextUpdate = new DateTime().minus(12345L).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                new Date(), nextUpdate);

        thrown.expectError(X_INCORRECT_VALIDATION_INFO);
        OcspVerifier.verify(ocsp, subject, issuer);
    }

    @Test
    public void certStatusGood() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD,
                thisUpdate, null);

        OcspVerifier.verify(ocsp, subject, issuer);
    }

    @Test
    public void certStatusRevoked() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey,
                new RevokedStatus(new Date(), CRLReason.unspecified),
                thisUpdate, null);

        thrown.expectError(X_CERT_VALIDATION);
        OcspVerifier.verify(ocsp, subject, issuer);
    }

    @Test
    public void certStatusUnknown() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, new UnknownStatus(),
                thisUpdate, null);

        thrown.expectError(X_CERT_VALIDATION);
        OcspVerifier.verify(ocsp, subject, issuer);
    }

    @Before
    public void loadCerts() throws Exception {
        GlobalConf.reload(new MyGlobalConf());

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

    private class MyGlobalConf extends EmptyGlobalConf {
        @Override
        public List<X509Certificate> getOcspResponderCertificates() {
            return Arrays.asList(signer);
        }

        @Override
        public X509Certificate getCaCert(X509Certificate orgCert) {
            return TestCertUtil.getCaCert();
        }
    }
}
