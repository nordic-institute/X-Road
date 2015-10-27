package ee.ria.xroad.common.signature;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MessageFileNames;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test to verify correct signature builder behavior.
 */
public class SignatureBuilderTest {

    private static X509Certificate subjectCert;
    private static PrivateKey subjectKey;
    private static X509Certificate issuerCert;
    private static X509Certificate signerCert;
    private static PrivateKey signerKey;

    static {
        TestSecurityUtil.initSecurity();
    }

    /**
     * Set up certificates.
     * @throws Exception in case of any unexpected error
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        subjectCert = TestCertUtil.getConsumer().cert;
        subjectKey = TestCertUtil.getConsumer().key;
        issuerCert = TestCertUtil.getCaCert();
        signerCert = TestCertUtil.getOcspSigner().cert;
        signerKey = TestCertUtil.getOcspSigner().key;
    }

    /**
     * Test to ensure signature with no extra certificates is built successfully.
     * @throws Exception in case of any unexpected error
     */
    @Test
    public void buildSuccessfullyNoExtraCerts() throws Exception {
        MessagePart hash = new MessagePart(MessageFileNames.MESSAGE,
                CryptoUtils.SHA512_ID, hash("xxx"));

        SignatureBuilder builder = new SignatureBuilder();
        builder.addPart(hash);

        Date thisUpdate = new DateTime().plusDays(1).toDate();

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(
                subjectCert, issuerCert, signerCert,
                signerKey, CertificateStatus.GOOD, thisUpdate, null);

        builder.setSigningCert(subjectCert);
        builder.addOcspResponses(Collections.singletonList(ocsp));

        SignatureData data = builder.build(new TestSigningKey(subjectKey),
                CryptoUtils.SHA512WITHRSA_ID);
        assertNotNull(data);
        assertNotNull(data.getSignatureXml());
        assertNull(data.getHashChainResult());
        assertNull(data.getHashChain());
    }

    /**
     * Test to ensure signature with extra certificates is built successfully.
     * @throws Exception in case of any unexpected error
     */
    @Test
    public void buildSuccessfullyWithExtraCerts() throws Exception {
        SignatureBuilder builder = new SignatureBuilder();

        Date thisUpdate = new DateTime().plusDays(1).toDate();

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(
                subjectCert, issuerCert, signerCert,
                signerKey, CertificateStatus.GOOD, thisUpdate, null);

        // note that we don't really care which certificates and ocsp responses
        // we include in the signature for this test case
        builder.addExtraCertificates(Arrays.asList(subjectCert, subjectCert));
        builder.addOcspResponses(Arrays.asList(ocsp, ocsp));

        builder.setSigningCert(subjectCert);

        SignatureData data = builder.build(new TestSigningKey(subjectKey),
                CryptoUtils.SHA512WITHRSA_ID);
        assertNotNull(data);
        assertNotNull(data.getSignatureXml());
        assertNotNull(data.getHashChainResult());
        assertNotNull(data.getHashChain());
    }

    private static byte[] hash(String input) {
        return input.getBytes();
    }
}
