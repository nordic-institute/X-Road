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
package ee.ria.xroad.common.cert;

import ee.ria.xroad.common.*;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.junit.Test;

import java.security.cert.CertPathBuilderException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    /**
     * Tests verifying a simple certificate chain without intermediates.
     * @throws Exception if an error occurs
     */
    @Test
    public void chainWithNoIntermediates() throws Exception {
        X509Certificate rootCa = TestCertUtil.getCertChainCert("root_ca.p12");
        X509Certificate userCert = TestCertUtil.getCertChainCert("user_0.p12");

        CertChain chain = new CertChain("EE", userCert, rootCa,
                new ArrayList<X509Certificate>());
        verify(chain, getAllOcspResponses(),
                makeDate(userCert.getNotBefore(), 1));
    }

    /**
     * Tests verifying a certificate chain with 3 intermediate certificates.
     * @throws Exception if an error occurs
     */
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

    /**
     * Test that verifying a chain with missing intermediate certificate fails.
     * @throws Exception if an error occurs
     */
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

    /**
     * Tests that verifying the chain with invalid user certificate fails.
     * @throws Exception if an error occurs
     */
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

    /**
     * Tests that verifying a chain with invalid CA certificate fails.
     * @throws Exception if an error occurs
     */
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

    /**
     * Tests that verifying a chain with missing OCSP responses fails.
     * @throws Exception if an error occurs
     */
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

    /**
     * Tests that verifying a chain with invalid OCSP responses fails.
     * @throws Exception if an error occurs
     */
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
