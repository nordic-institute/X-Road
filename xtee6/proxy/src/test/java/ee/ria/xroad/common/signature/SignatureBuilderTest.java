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
package ee.ria.xroad.common.signature;

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
        List<MessagePart> hashes = new ArrayList<>();
        hashes.add(new MessagePart(MessageFileNames.MESSAGE,
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

    /**
     * Test to ensure signature with extra certificates is built successfully.
     * @throws Exception in case of any unexpected error
     */
    @Test
    public void buildSuccessfullyWithExtraCerts() throws Exception {
        List<MessagePart> hashes = new ArrayList<>();
        hashes.add(new MessagePart(MessageFileNames.MESSAGE,
                CryptoUtils.SHA512_ID, hash("xxx")));
        hashes.add(new MessagePart(MessageFileNames.attachment(0),
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

    private static byte[] hash(String input) {
        return input.getBytes();
    }
}
