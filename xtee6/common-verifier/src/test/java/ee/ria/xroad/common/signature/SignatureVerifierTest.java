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
import java.util.*;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.SHA512_ID;

/**
 * Tests the signature verifier.
 */
public class SignatureVerifierTest {

    /** The date when the the OCSP responses etc are valid. */
    private static final Date CORRECT_VALIDATION_DATE = createDate(30, 9, 2014);

    /** The correct member name used in the test data. */
    private static final ClientId CORRECT_MEMBER = createClientId("Test Org");

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
        System.setProperty(SystemProperties.CONFIGURATION_PATH,
                "../common-util/src/test/resources/globalconf_good");
        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
                "../common-util/src/test/resources/configuration-anchor1.xml");
        GlobalConf.reload(new TestGlobalConfImpl(false) {
            @Override
            public X509Certificate getCaCert(String instanceIdentifier,
                    X509Certificate memberCert) throws Exception {
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
        List<MessagePart> hashes = new ArrayList<>();
        hashes.add(new MessagePart(MessageFileNames.MESSAGE, SHA512_ID,
                fileToBytes("message-0.xml")));

        SignatureVerifier verifier = createSignatureVerifier("sig-0.xml");
        verifier.addParts(hashes);

        verifier.verify(createClientId("consumer"), CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that verifying a valid signature succeeds.
     * @throws Exception if error occurs
     */
    @Test
    public void verifyValidSignatureHashChain() throws Exception {
        Resolver resolver = null;

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
        }
            .withHashChain("hash-chain-1.xml")
            .withMessage("message-1.xml");

        createSignatureVerifier("batch-sig.xml",
                "hash-chain-result.xml", resolver)
            .verify(createClientId("consumer"), CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that reading an empty signature fails.
     * @throws Exception if error occurs
     */
    @Test
    public void emptySignature() throws Exception {
        thrown.expectError(X_INVALID_XML);
        createSignatureVerifier("empty.xml");
    }

    /**
     * Tests that verifying a signature without ds:Signature element fails.
     * @throws Exception if error occurs
     */
    @Test
    public void noXadesSignature() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        createSignatureVerifier("no-signature.xml");
    }

    /**
     * Tests that verifying a signature without ObjectContainer element fails.
     * @throws Exception if error occurs
     */
    @Test
    public void noObjectContainer() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        createSignatureVerifier("no-objectcontainer.xml");
    }

    /**
     * Tests that reading a malformed XML fails.
     * @throws Exception if error occurs
     */
    @Test
    public void malformedXml() throws Exception {
        thrown.expectError(X_INVALID_XML);
        createSignatureVerifier("malformed-xml.xml");
    }

    /**
     * Tests that validating against the schema fails if the XML does not
     * satisfy the schema. Just changed the name of one element for now.
     * @throws Exception if error occurs
     */
    @Test
    public void schemaValidationFail() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        SignatureVerifier verifier = createSignatureVerifier("schema-fail.xml");
        verifier.verify(null, null);
    }

    /**
     * Tests that verification fails if signing certificate is
     * not in the signature.
     * @throws Exception if error occurs
     */
    @Test
    public void noSigningCertificate() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        SignatureVerifier verifier =
                createSignatureVerifier("no-signing-cert.xml");
        verifier.verify(null, null);
    }

    /**
     * Tests that verifying the signer name fails if provided with an invalid
     * signer name.
     * @throws Exception if error occurs
     */
    @Test
    public void invalidSignerName() throws Exception {
        thrown.expectError(X_INCORRECT_CERTIFICATE);
        SignatureVerifier verifier = createSignatureVerifier("good.xml");
        verifier.verify(createClientId("FOORBAR"), null);
    }

    /**
     * Tests that verifying the signature value fails if the value is incorrect.
     * @throws Exception if error occurs
     */
    @Test
    public void invalidSignatureValue() throws Exception {
        thrown.expectError(X_INVALID_SIGNATURE_VALUE);
        SignatureVerifier verifier =
                createSignatureVerifier("invalid-signature-value.xml");
        verifier.verify(CORRECT_MEMBER, null);
    }

    /**
     * Test that reading encapsulated certificates works as expected.
     * @throws Exception if error occurs
     */
    //@Test
    public void extraCerts() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("extra-certs.xml");
        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test that when an encapsulated certificate element has missing
     * id attribute, exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void extraCertsMissingId() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        SignatureVerifier verifier = createSignatureVerifier(
                "extra-certs-missing-id.xml");
        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test that when an encapsulated certificate element is missing,
     * exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void extraCertsMissingCert() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        SignatureVerifier verifier = createSignatureVerifier(
                "extra-certs-missing-cert.xml");
        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test that when an encapsulated certificate has its digest mangled,
     * exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void extraCertsDigestInvalid() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        SignatureVerifier verifier = createSignatureVerifier(
                "extra-certs-digest-invalid.xml");
        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that if the signature contains no OCSP responses,
     * exception is thrown.
     * @throws Exception if error occurs
     */
    @Test
    public void ocspNoResponses() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        SignatureVerifier verifier = createSignatureVerifier(
                "ocsp-no-responses.xml");
        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
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
        hashes.add(new MessagePart(MessageFileNames.MESSAGE, SHA512_ID,
                hash("foo")));

        SignatureVerifier verifier = createSignatureVerifier("sig-0.xml");
        verifier.addParts(hashes);

        verifier.verify(createClientId("consumer"), CORRECT_VALIDATION_DATE);
    }

    // ------------------------------------------------------------------------

    private static SignatureVerifier createSignatureVerifier(String file)
            throws Exception {
        return new SignatureVerifier(signature(file));
    }

    private static SignatureVerifier createSignatureVerifier(
            String signatureFileName, String hashChainResultFileName,
            HashChainReferenceResolver resolver) throws Exception {
        Signature signature = signature(signatureFileName);

        SignatureVerifier verifier = new SignatureVerifier(signature,
                loadFile(hashChainResultFileName), null);

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
        return new FileInputStream("src/test/signatures/" + fileName);
    }

    private static Signature signature(String signatureFileName)
            throws Exception {
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
        return ClientId.create("EE", "BUSINESS", memberCode);
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
