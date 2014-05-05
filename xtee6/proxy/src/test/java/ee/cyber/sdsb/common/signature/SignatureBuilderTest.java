package ee.cyber.sdsb.common.signature;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestSecurityUtil;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MessageFileNames;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SignatureBuilderTest {

    private static X509Certificate subjectCert;
    private static PrivateKey subjectKey;
    private static X509Certificate issuerCert;
    private static X509Certificate signerCert;
    private static PrivateKey signerKey;

    static {
        TestSecurityUtil.initSecurity();
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        subjectCert = TestCertUtil.getTestOrg().cert;
        subjectKey = TestCertUtil.getTestOrg().key;
        issuerCert = TestCertUtil.getCaCert();
        signerCert = TestCertUtil.getOcspSigner().cert;
        signerKey = TestCertUtil.getOcspSigner().key;
    }

    @Test
    public void buildSuccessfullyNoExtraCerts() throws Exception {
        List<PartHash> hashes = new ArrayList<>();
        hashes.add(new PartHash(MessageFileNames.MESSAGE,
                CryptoUtils.SHA512_ID, hash("xxx")));

        SignatureBuilder builder = new SignatureBuilder();
        builder.addParts(hashes);

        Date thisUpdate = new DateTime().plusDays(1).toDate();

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(
                subjectCert, issuerCert, signerCert,
                signerKey, CertificateStatus.GOOD, thisUpdate, null);

        builder.setSigningCert(subjectCert, ocsp);

        SignatureData data = builder.build(new TestSigningKey(subjectKey),
                CryptoUtils.SHA512WITHRSA_ID);
        assertNotNull(data);
        assertNotNull(data.getSignatureXml());
        assertNull(data.getHashChainResult());
        assertNull(data.getHashChain());
    }

    @Test
    public void buildSuccessfullyWithExtraCerts() throws Exception {
        List<PartHash> hashes = new ArrayList<>();
        hashes.add(new PartHash(MessageFileNames.MESSAGE,
                CryptoUtils.SHA512_ID, hash("xxx")));
        hashes.add(new PartHash(MessageFileNames.attachment(0),
                CryptoUtils.SHA512_ID, hash("yyy")));

        SignatureBuilder builder = new SignatureBuilder();
        builder.addParts(hashes);

        Date thisUpdate = new DateTime().plusDays(1).toDate();

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(
                subjectCert, issuerCert, signerCert,
                signerKey, CertificateStatus.GOOD, thisUpdate, null);

        // note that we don't really care which certificates and ocsp responses
        // we include in the signature for this test case
        builder.addExtraCertificates(Arrays.asList(subjectCert, subjectCert));
        builder.addOcspResponses(Arrays.asList(ocsp, ocsp));

        builder.setSigningCert(subjectCert, ocsp);

        SignatureData data = builder.build(new TestSigningKey(subjectKey),
                CryptoUtils.SHA512WITHRSA_ID);
        assertNotNull(data);
        assertNotNull(data.getSignatureXml());
        assertNotNull(data.getHashChainResult());
        assertNotNull(data.getHashChain());
    }

    private static String hash(String input) {
        return CryptoUtils.encodeBase64(input.getBytes());
    }
}
