package ee.cyber.sdsb.common.cert;

import java.security.Security;
import java.security.cert.CertPathBuilderException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.junit.Test;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestSecurityUtil;
import ee.cyber.sdsb.common.conf.globalconf.EmptyGlobalConf;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;

import static org.junit.Assert.*;

/**
 * Test cases for verifying the functionality of
 * java.security.cert.CertPathBuilder and java.security.cert.CertPathBuilder.
 *
 * Please note that the certificates used in these tests are valid for 1 year,
 * starting from September 2012 unless stated otherwise.
 */
public class CertChainTest {

    static {
        TestSecurityUtil.initSecurity();
        GlobalConf.reload(new CertChainTestGlobalConf());
    }

    @Test
    public void chainWithNoIntermediates() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate userCert = TestCertUtil.getCertChainCert("user_0.p12");

        CertChain chain = new CertChain("EE", userCert, rootCa,
                new ArrayList<X509Certificate>());
        verify(chain, getAllOcspResponses(),
                makeDate(userCert.getNotBefore(), 1));
    }

    @Test
    public void chainWith3Intermediates() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate interCa1 = TestCertUtil.getCertChainCert("ca_1.p12");
        X509Certificate interCa2 = TestCertUtil.getCertChainCert("ca_2.p12");
        X509Certificate interCa3 = TestCertUtil.getCertChainCert("ca_3.p12");
        X509Certificate userCert = TestCertUtil.getCertChainCert("user_3.p12");

        CertChain chain = new CertChain("EE",
                userCert,
                rootCa,
                Arrays.asList(interCa1, interCa2, interCa3));
        verify(chain, getAllOcspResponses(),
                makeDate(rootCa.getNotBefore(), 1));
    }

    @Test
    public void chainWithMissingIntermediate() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate interCa1 = TestCertUtil.getCertChainCert("ca_1.p12");
        X509Certificate interCa3 = TestCertUtil.getCertChainCert("ca_3.p12");
        X509Certificate userCert = TestCertUtil.getCertChainCert("user_3.p12");

        try {
            CertChain chain = new CertChain("EE", userCert,
                    rootCa,
                    Arrays.asList(interCa1, interCa3));
            verifyChainOnly(chain, new Date());
            fail("Path creation should fail");
        } catch (CodedException e) {
            assertTrue(e.getCause() instanceof CertPathBuilderException);
        }
    }

    @Test
    public void invalidUserCertSignature() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate interCa1 = TestCertUtil.getCertChainCert("ca_1.p12");
        X509Certificate interCa2 = TestCertUtil.getCertChainCert("ca_2.p12");
        X509Certificate interCa3 = TestCertUtil.getCertChainCert("ca_3.p12");
        X509Certificate userCert =
                TestCertUtil.getCertChainCert("user_invalid_sig.p12");

        CertChain chain = new CertChain("EE",
                userCert,
                rootCa,
                Arrays.asList(interCa1, interCa2, interCa3));
        try {
            verifyChainOnly(chain, null);
            fail("Path creation should fail");
        } catch (CodedException e) {
            assertTrue(e.getCause() instanceof CertPathBuilderException);
        }
    }

    @Test
    public void invalidCaCertNoExtensions() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate interCa1 = TestCertUtil.getCertChainCert("ca_1.p12");
        X509Certificate interCa2 = TestCertUtil.getCertChainCert("ca_2.p12");
        X509Certificate interCa3 = TestCertUtil.getCertChainCert("ca_3.p12");
        // this CA cert has no extensions
        X509Certificate interCa4 =
                TestCertUtil.getCertChainCert("ca_4_no_ext.p12");
        X509Certificate userCert = TestCertUtil.getCertChainCert("user_4.p12");

        List<OCSPResp> ocsp = generateOcspResponses(
                Arrays.asList(interCa1, interCa2, interCa3, interCa4, userCert),
                CertificateStatus.GOOD);

        CertChain chain = new CertChain("EE",
                userCert,
                rootCa,
                Arrays.asList(interCa1, interCa2, interCa3, interCa4));
        try {
            verify(chain, ocsp, null);
            fail("Path creation should fail");
        } catch (CodedException e) {
            assertTrue(e.getCause() instanceof CertPathBuilderException);
        }
    }

    //@Test
    public void unsafeUserCertSignatureAlgorithm() throws Exception {
        String disabledAlgorithms =
                Security.getProperty("jdk.certpath.disabledAlgorithms");
        assertEquals("MD5", disabledAlgorithms);

        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate interCa1 = TestCertUtil.getCertChainCert("ca_1.p12");
        X509Certificate interCa2 = TestCertUtil.getCertChainCert("ca_2.p12");
        X509Certificate interCa3 = TestCertUtil.getCertChainCert("ca_3.p12");
        // this cert uses md5WithRSAEncryption signature algorithm
        X509Certificate userCert =
                TestCertUtil.getCertChainCert("user_3_md5.p12");

        List<OCSPResp> ocsp = generateOcspResponses(
                Arrays.asList(interCa1, interCa2, interCa3, userCert),
                CertificateStatus.GOOD);

        try {
            CertChain chain = new CertChain("EE", userCert,
                    rootCa,
                    Arrays.asList(interCa1, interCa2, interCa3));
            verify(chain, ocsp, new Date());
            fail("Path creation should fail");
        } catch (CodedException e) {
            assertTrue(e.getCause() instanceof CertPathBuilderException);
        }
    }

    @Test
    public void missingOcspResponse() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate interCa1 = TestCertUtil.getCertChainCert("ca_1.p12");
        X509Certificate interCa2 = TestCertUtil.getCertChainCert("ca_2.p12");
        X509Certificate interCa3 = TestCertUtil.getCertChainCert("ca_3.p12");
        X509Certificate userCert = TestCertUtil.getCertChainCert("user_3.p12");

        List<OCSPResp> ocsp = generateOcspResponses(
                Arrays.asList(interCa1, interCa3, userCert),
                CertificateStatus.GOOD);

        CertChain chain = new CertChain("EE",
                userCert,
                rootCa,
                Arrays.asList(interCa1, interCa2, interCa3));
        try {
            verify(chain, ocsp, makeDate(rootCa.getNotBefore(), 1));
            fail("OCSP verification should fail");
        } catch (CodedException e) {
            assertTrue(e.getFaultCode().startsWith(
                    ErrorCodes.X_INVALID_CERT_PATH_X));
        }
    }

    @Test
    public void invalidOcspResponse() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate interCa1 = TestCertUtil.getCertChainCert("ca_1.p12");
        X509Certificate interCa2 = TestCertUtil.getCertChainCert("ca_2.p12");
        X509Certificate interCa3 = TestCertUtil.getCertChainCert("ca_3.p12");
        X509Certificate userCert = TestCertUtil.getCertChainCert("user_3.p12");

        List<OCSPResp> ocsp = generateOcspResponses(
                Arrays.asList(interCa1, interCa2, interCa3, userCert),
                new RevokedStatus(new Date(), 0));

        CertChain chain = new CertChain("EE",
                userCert,
                rootCa,
                Arrays.asList(interCa1, interCa2, interCa3));
        try {
            verify(chain, ocsp, makeDate(rootCa.getNotBefore(), 1));
            fail("OCSP verification should fail");
        } catch (CodedException e) {
            assertTrue(e.getFaultCode().startsWith(
                    ErrorCodes.X_INVALID_CERT_PATH_X));
        }
    }

    // -- Utility methods

    private static void  verify(CertChain chain, List<OCSPResp> ocspResponses,
            Date atDate) {
        new CertChainVerifier(chain).verify(ocspResponses, atDate);
    }

    private static void  verifyChainOnly(CertChain chain, Date atDate) {
        new CertChainVerifier(chain).verifyChainOnly(atDate);
    }

    private static Date makeDate(Date someDate, int plusDays) {
        return new Date(someDate.getTime()
                + (1000L * 60 * 60 * 24 * plusDays));
    }

    private static List<OCSPResp> getAllOcspResponses() throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        certs.add(TestCertUtil.getCertChainCert("user_0.p12"));
        certs.add(TestCertUtil.getCertChainCert("user_1.p12"));
        certs.add(TestCertUtil.getCertChainCert("user_2.p12"));
        certs.add(TestCertUtil.getCertChainCert("user_3.p12"));
        certs.add(TestCertUtil.getCertChainCert("user_4.p12"));
        certs.add(TestCertUtil.getCertChainCert("ca_1.p12"));
        certs.add(TestCertUtil.getCertChainCert("ca_2.p12"));
        certs.add(TestCertUtil.getCertChainCert("ca_3.p12"));

        return generateOcspResponses(certs, CertificateStatus.GOOD);
    }

    private static List<OCSPResp> generateOcspResponses(
            List<X509Certificate> certs, CertificateStatus status)
                    throws Exception {
        List<OCSPResp> responses = new ArrayList<>();
        for (X509Certificate cert : certs) {
            responses.add(OcspTestUtils.createOCSPResponse(cert,
                    getIssuerCert(cert, certs),
                    TestCertUtil.getOcspSigner().cert,
                    TestCertUtil.getOcspSigner().key,
                    status));
        }
        return responses;
    }

    private static X509Certificate getIssuerCert(X509Certificate subject,
            List<X509Certificate> certs) throws Exception {
        for (X509Certificate cert : certs) {
            if (cert.getSubjectX500Principal().equals(
                    subject.getIssuerX500Principal())) {
                return cert;
            }
        }

        return TestCertUtil.getCertChainCert("root_ca.p12");
    }

    private static class CertChainTestGlobalConf extends EmptyGlobalConf {
        @Override
        public List<X509Certificate> getOcspResponderCertificates() {
            try {
                return Arrays.asList(TestCertUtil.getOcspSigner().cert);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public X509Certificate getCaCert(String instanceIdentifier,
                X509Certificate orgCert) throws Exception {
            List<X509Certificate> certs = new ArrayList<>();
            certs.add(TestCertUtil.getCertChainCert("ca_1.p12"));
            certs.add(TestCertUtil.getCertChainCert("ca_2.p12"));
            certs.add(TestCertUtil.getCertChainCert("ca_3.p12"));
            return getIssuerCert(orgCert, certs);
        }
    }
}
