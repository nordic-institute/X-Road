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

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.TestGlobalConfImpl;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.MessageFileNames;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_XML;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SIGNATURE;
import static ee.ria.xroad.common.util.CryptoUtils.SHA512_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;

/**
 * Tests the signature verifier.
 */
public class SignatureVerifierTest {

    /** The date when the OCSP responses etc are valid. */
    private static final Date CORRECT_VALIDATION_DATE = createDate(30, 9, 2014);

    /** The correct member name used in the test data. */
    private static final ClientId TEST_ORG_ID = createClientId("Test Org");
    private static final ClientId CONSUMER_ID = createClientId("consumer");

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    static {
        TestSecurityUtil.initSecurity();
    }

    /**
     * Set up the test -- correct global conf location etc.
     */
    @Before
    public void setUp() {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, "../common-util/src/test/resources/globalconf_good_v2");
        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
                "../common-util/src/test/resources/configuration-anchor1.xml");

        GlobalConf.reload(new TestGlobalConfImpl() {
            @Override
            public X509Certificate getCaCert(String instanceIdentifier, X509Certificate memberCert) throws Exception {
                return TestCertUtil.getCaCert();
            }
        });
    }

    /**
     * Tests that verifying a valid signature succeeds.
     * @throws Exception if error occurs
     */
    @Test
    public void verifyValidSignature() throws Exception {
        verifyValidSignature("../common-test/src/test/signatures/sign-0.xml");
    }

    /**
     * Tests that verifying a valid signature succeeds.
     * @throws Exception if error occurs
     */
    @Test
    public void verifyValidSignatureHashChain() throws Exception {
        Resolver resolver;

        resolver = new Resolver() {
            @Override
            public InputStream resolve(String uri) throws IOException {
                if ("/attachment1".equals(uri)) {
                    // Returns the attachment content
                    return IOUtils.toInputStream("blaah");
                } else {
                    return super.resolve(uri);
                }
            }
        }.withHashChain("src/test/signatures/hash-chain-1.xml")
                .withMessage("src/test/signatures/message-1.xml");

        createSignatureVerifier("src/test/signatures/batch-sig.xml", "src/test/signatures/hash-chain-result.xml",
                resolver).verify(CONSUMER_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that verifying backward compatible (not conforming to specification) valid signature succeeds.
     * @throws Exception if error occurs
     */
    @Test
    public void verifyValidBackwardCompatibleSignature() throws Exception {
        verifyValidSignature("../common-test/src/test/signatures/sign-0-old-format.xml");
    }

    private void verifyValidSignature(String signatureFileName) throws Exception {
        List<MessagePart> hashes = new ArrayList<>();
        byte[] messageBytes = fileToBytes("../common-test/src/test/signatures/message-0.xml");

        hashes.add(new MessagePart(MessageFileNames.MESSAGE, SHA512_ID, calculateDigest(SHA512_ID, messageBytes),
                messageBytes));

        SignatureVerifier verifier = createSignatureVerifier(signatureFileName);
        verifier.addParts(hashes);

        verifier.verify(CONSUMER_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that reading an empty signature fails.
     * @throws Exception if error occurs
     */
    @Test
    public void emptySignature() throws Exception {
        thrown.expectError(X_INVALID_XML);

        createSignatureVerifier("src/test/signatures/empty.xml");
    }

    /**
     * Tests that verifying a signature without ds:Signature element fails.
     * @throws Exception if error occurs
     */
    @Test
    public void noXadesSignature() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        createSignatureVerifier("src/test/signatures/sign-0-no-signature.xml");
    }

    /**
     * Tests that verifying a signature without ObjectContainer element fails.
     * @throws Exception if error occurs
     */
    @Test
    public void noObjectContainer() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        createSignatureVerifier("src/test/signatures/sign-0-no-objectcontainer.xml");
    }

    /**
     * Tests that reading a malformed XML fails.
     * @throws Exception if error occurs
     */
    @Test
    public void malformedXml() throws Exception {
        thrown.expectError(X_INVALID_XML);

        createSignatureVerifier("src/test/signatures/sign-0-malformed-xml.xml");
    }

    /**
     * Tests that validating against the schema fails if the XML does not satisfy the schema.
     * Just changed the name of one element for now.
     * @throws Exception if error occurs
     */
    @Test
    public void schemaValidationFail() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/sign-0-schema-fail.xml");
        verifier.verify(null, null);
    }

    /**
     * Tests that verification fails if signing certificate is not in the signature.
     * @throws Exception if error occurs
     */
    @Test
    public void noSigningCertificate() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/sign-0-no-signing-cert.xml");
        verifier.verify(null, null);
    }

    /**
     * Tests that verifying the signer name fails if provided with an invalid signer name.
     * @throws Exception if error occurs
     */
    @Test
    public void invalidSignerName() throws Exception {
        thrown.expectError(X_INCORRECT_CERTIFICATE);

        SignatureVerifier verifier = createSignatureVerifier("../common-test/src/test/signatures/sign-0.xml");
        verifier.verify(createClientId("FOORBAR"), null);
    }

    /**
     * Tests that verifying the signature value fails if the value is incorrect.
     * @throws Exception if error occurs
     */
    @Test
    public void invalidSignatureValue() throws Exception {
        thrown.expectError(X_INVALID_SIGNATURE_VALUE);

        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/sign-0-invalid-signature-value.xml");
        verifier.verify(CONSUMER_ID, null);
    }

    /**
     * Test that reading encapsulated certificates works as expected.
     * @throws Exception if error occurs
     */
    //@Test
    public void extraCerts() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/extra-certs.xml");
        verifier.verify(TEST_ORG_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test that when an encapsulated certificate element has missing
     * id attribute, exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void extraCertsMissingId() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/extra-certs-missing-id.xml");
        verifier.verify(TEST_ORG_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test that when an encapsulated certificate element is missing, exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void extraCertsMissingCert() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/extra-certs-missing-cert.xml");
        verifier.verify(TEST_ORG_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test that when an encapsulated certificate has its digest mangled, exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void extraCertsDigestInvalid() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/extra-certs-digest-invalid.xml");
        verifier.verify(TEST_ORG_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that if the signature contains no OCSP responses, exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void ocspNoResponses() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);

        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/sign-0-ocsp-no-responses.xml");
        verifier.verify(CONSUMER_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that if the hashes of the attachments do not match,
     * verification fails.
     * @throws Exception if error occurs
     */
    @Test
    public void invalidAttachmentHash() throws Exception {
        thrown.expectError(X_INVALID_SIGNATURE_VALUE);

        List<MessagePart> hashes = new ArrayList<>();
        hashes.add(new MessagePart(MessageFileNames.MESSAGE, SHA512_ID, hash("foo"), hash("foo")));

        SignatureVerifier verifier = createSignatureVerifier("../common-test/src/test/signatures/sign-0.xml");
        verifier.addParts(hashes);

        verifier.verify(CONSUMER_ID, CORRECT_VALIDATION_DATE);
    }

    // ------------------------------------------------------------------------

    private static SignatureVerifier createSignatureVerifier(String signaturePath) throws Exception {
        return new SignatureVerifier(signature(signaturePath));
    }

    private static SignatureVerifier createSignatureVerifier(String signatureFileName, String hashChainResultFileName,
            HashChainReferenceResolver resolver) throws Exception {
        Signature signature = signature(signatureFileName);

        SignatureVerifier verifier = new SignatureVerifier(signature, loadFile(hashChainResultFileName), null);

        verifier.setHashChainResourceResolver(resolver);

        return verifier;
    }

    private static byte[] hash(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fileToBytes(String fileName) throws Exception {
        try (InputStream file = file(fileName)) {
            return IOUtils.toByteArray(file);
        }
    }

    private static String loadFile(String fileName) throws Exception {
        return IOUtils.toString(file(fileName));
    }

    private static InputStream file(String fileName) throws IOException {
        return new FileInputStream(fileName);
    }

    private static Signature signature(String signatureFileName) throws Exception {
        try (InputStream file = file(signatureFileName)) {
            return new Signature(file);
        }
    }

    private static Date createDate(int day, int month, int year) {
        Calendar cal = Calendar.getInstance();
        cal.clear(); // Let's clear the current time.
        cal.set(year, month, day);

        return cal.getTime();
    }

    private static ClientId createClientId(String memberCode) {
        return ClientId.Conf.create("EE", "BUSINESS", memberCode);
    }

    private static class Resolver implements HashChainReferenceResolver {

        private final Map<String, String> resources = new HashMap<>();

        Resolver withHashChain(String fileName) {
            return add(MessageFileNames.SIG_HASH_CHAIN, fileName);
        }

        Resolver withMessage(String fileName) {
            return add(MessageFileNames.MESSAGE, fileName);
        }

        Resolver add(String name, String file) {
            resources.put(name, file);

            return this;
        }

        @Override
        public InputStream resolve(String uri) throws IOException {
            if (resources.containsKey(uri)) {
                return file(resources.get(uri));
            }

            return null;
        }

        @Override
        public boolean shouldResolve(String uri, byte[] digestValue) {
            return true;
        }
    }
}
