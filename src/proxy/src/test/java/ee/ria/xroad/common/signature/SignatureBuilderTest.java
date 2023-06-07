/**
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
package ee.ria.xroad.common.signature;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MessageFileNames;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test to verify correct signature builder behavior.
 */
public class SignatureBuilderTest {
    // Set true, if want to save generated test data.
    private static final boolean WRITE_TEST_DATA = false;

    private static final String TEST_DATA_DIR = "../common/common-test/src/test/signatures/";

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
        subjectCert = TestCertUtil.getConsumer().certChain[0];
        subjectKey = TestCertUtil.getConsumer().key;
        issuerCert = TestCertUtil.getCaCert();
        signerCert = TestCertUtil.getOcspSigner().certChain[0];
        signerKey = TestCertUtil.getOcspSigner().key;
    }

    private static byte[] fileToBytes(String fileName) throws Exception {
        try (InputStream input = new FileInputStream(getFilePath(fileName).toFile())) {
            return IOUtils.toByteArray(input);
        }
    }

    private static Path getFilePath(String fileName) {
        return Paths.get(TEST_DATA_DIR, fileName);
    }

    /**
     * Test to ensure signature with no extra certificates is built successfully.
     * @throws Exception in case of any unexpected error
     */
    @Test
    public void buildSuccessfullyNoExtraCerts() throws Exception {
        byte[] messageBytes = fileToBytes("message-0.xml");

        MessagePart hash = new MessagePart(MessageFileNames.MESSAGE, CryptoUtils.SHA512_ID,
                CryptoUtils.calculateDigest(CryptoUtils.SHA512_ID, messageBytes), messageBytes);

        SignatureBuilder builder = new SignatureBuilder();
        builder.addPart(hash);

        Date thisUpdate = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subjectCert, issuerCert, signerCert, signerKey,
                CertificateStatus.GOOD, thisUpdate, null);

        builder.setSigningCert(subjectCert);
        builder.addOcspResponses(Collections.singletonList(ocsp));

        SignatureData data = builder.build(new TestSigningKey(subjectKey), CryptoUtils.SHA512_ID);

        assertNotNull(data);
        assertNotNull(data.getSignatureXml());
        assertNull(data.getHashChainResult());
        assertNull(data.getHashChain());

        if (WRITE_TEST_DATA) {
            Files.write(getFilePath("sign-0.xml"), data.getSignatureXml().getBytes("UTF-8"),
                    CREATE, WRITE, TRUNCATE_EXISTING);
        }
    }

    /**
     * Test to ensure signature with extra certificates is built successfully.
     * @throws Exception in case of any unexpected error
     */
    @Test
    public void buildSuccessfullyWithExtraCerts() throws Exception {
        SignatureBuilder builder = new SignatureBuilder();

        final Date thisUpdate = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subjectCert, issuerCert, signerCert, signerKey,
                CertificateStatus.GOOD, thisUpdate, null);

        // note that we don't really care which certificates and ocsp responses
        // we include in the signature for this test case
        builder.addExtraCertificates(Arrays.asList(subjectCert, subjectCert));
        builder.addOcspResponses(Arrays.asList(ocsp, ocsp));

        builder.setSigningCert(subjectCert);

        SignatureData data = builder.build(new TestSigningKey(subjectKey), CryptoUtils.SHA512_ID);

        assertNotNull(data);
        assertNotNull(data.getSignatureXml());
        assertNotNull(data.getHashChainResult());
        assertNotNull(data.getHashChain());

        if (WRITE_TEST_DATA) {
            Files.write(getFilePath("sign-0-extra-certs.xml"), data.getSignatureXml().getBytes("UTF-8"),
                    CREATE, WRITE, TRUNCATE_EXISTING);
            Files.write(getFilePath("sign-0-extra-certs-hash-chain-result.xml"),
                    data.getHashChainResult().getBytes("UTF-8"), CREATE, WRITE, TRUNCATE_EXISTING);
            Files.write(getFilePath("sign-0-extra-certs-hash-chain.xml"),
                    data.getHashChain().getBytes("UTF-8"), CREATE, WRITE, TRUNCATE_EXISTING);
        }
    }
}
