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
package org.niis.xroad.globalconf.impl.signature;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.Signature;
import ee.ria.xroad.common.util.MessageFileNames;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.test.globalconf.TestGlobalConfImpl;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SIGNATURE;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA512;
import static ee.ria.xroad.common.util.MessageFileNames.MESSAGE;
import static ee.ria.xroad.common.util.MessageFileNames.attachmentOfIdx;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the signature verifier.
 */
class SignatureVerifierTest {

    /**
     * The date when the OCSP responses etc are valid.
     */
    private static final Date CORRECT_VALIDATION_DATE = createDate(30, 9, 2014);

    /**
     * The correct member name used in the test data.
     */
    private static final ClientId TEST_ORG_ID = createClientId("Test Org");
    private static final ClientId CONSUMER_ID = createClientId("consumer");

    private GlobalConfProvider globalConfProvider;

    @BeforeAll
    public static void init() {
        TestSecurityUtil.initSecurity();

        //Additional logging for debugging
        SLF4JBridgeHandler.install();
    }

    /**
     * Set up the test -- correct global conf location etc.
     */
    @BeforeEach
    void setUp() {
        loadGlobalConf("../globalconf-core/src/test/resources/globalconf_good_v4",
                "../globalconf-core/src/test/resources/configuration-anchor1.xml", true);
    }

    /**
     * Tests that verifying a valid signature succeeds.
     *
     * @throws Exception if error occurs
     */
    @Test
    void verifyValidSignature() throws Exception {
        verifyValidSignature("../../common/common-test/src/test/signatures/sign-0.xml");
    }

    /**
     * Tests that verifying a valid signature succeeds.
     *
     * @throws Exception if error occurs
     */
    @Test
    void verifyValidSignatureHashChain() throws Exception {
        Resolver resolver;

        resolver = new Resolver() {
            @Override
            public InputStream resolve(String uri) throws IOException {
                if ("/attachment1".equals(uri)) {
                    // Returns the attachment content
                    return IOUtils.toInputStream("blaah", StandardCharsets.UTF_8);
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
     *
     * @throws Exception if error occurs
     */
    @Test
    void verifyValidBackwardCompatibleSignature() throws Exception {
        verifyValidSignature("../../common/common-test/src/test/signatures/sign-0-old-format.xml");
    }

    private void verifyValidSignature(String signatureFileName) throws Exception {
        List<MessagePart> hashes = new ArrayList<>();
        byte[] messageBytes = fileToBytes("../../common/common-test/src/test/signatures/message-0.xml");

        hashes.add(new MessagePart(MESSAGE, SHA512, calculateDigest(SHA512, messageBytes),
                messageBytes));

        SignatureVerifier verifier = createSignatureVerifier(signatureFileName);
        verifier.addParts(hashes);

        verifier.verify(CONSUMER_ID, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that reading an empty signature fails.
     */
    @Test
    void emptySignature() {
        assertThatThrownBy(() -> createSignatureVerifier("src/test/signatures/empty.xml"))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(ErrorCode.INVALID_XML.code());
    }

    /**
     * Tests that verifying a signature without ds:Signature element fails.
     */
    @Test
    void noXadesSignature() {
        assertThatThrownBy(() -> createSignatureVerifier("src/test/signatures/sign-0-no-signature.xml"))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Tests that verifying a signature without ObjectContainer element fails.
     */
    @Test
    void noObjectContainer() {
        assertThatThrownBy(() -> createSignatureVerifier("src/test/signatures/sign-0-no-objectcontainer.xml"))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Tests that reading a malformed XML fails.
     */
    @Test
    void malformedXml() {
        assertThatThrownBy(() -> createSignatureVerifier("src/test/signatures/sign-0-malformed-xml.xml"))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(ErrorCode.INVALID_XML.code());
    }

    /**
     * Tests that validating against the schema fails if the XML does not satisfy the schema.
     * Just changed the name of one element for now.
     */
    @Test
    void schemaValidationFail() {
        assertThatThrownBy(() -> createSignatureVerifier("src/test/signatures/sign-0-schema-fail.xml"))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Tests that verification fails if signing certificate is not in the signature.
     *
     * @throws Exception if error occurs
     */
    @Test
    void noSigningCertificate() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/sign-0-no-signing-cert.xml");
        assertThatThrownBy(() -> verifier.verify(null, null))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Tests that verifying the signer name fails if provided with an invalid signer name.
     *
     * @throws Exception if error occurs
     */
    @Test
    void invalidSignerName() throws Exception {
        var clientId = createClientId("FOORBAR");
        SignatureVerifier verifier = createSignatureVerifier("../../common/common-test/src/test/signatures/sign-0.xml");
        assertThatThrownBy(() -> verifier.verify(clientId, null))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_INCORRECT_CERTIFICATE);
    }

    /**
     * Tests that verifying the signature value fails if the value is incorrect.
     *
     * @throws Exception if error occurs
     */
    @Test
    void invalidSignatureValue() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/sign-0-invalid-signature-value.xml");
        assertThatThrownBy(() -> verifier.verify(CONSUMER_ID, null))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_INVALID_SIGNATURE_VALUE);
    }

    /**
     * Test that when an encapsulated certificate element has missing
     * id attribute, exception is thrown.
     *
     * @throws Exception if error occurs
     */
    @Test
    void extraCertsMissingId() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/extra-certs-missing-id.xml");
        assertThatThrownBy(() -> verifier.verify(TEST_ORG_ID, CORRECT_VALIDATION_DATE))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Test that when an encapsulated certificate element is missing, exception is thrown.
     *
     * @throws Exception if error occurs
     */
    @Test
    void extraCertsMissingCert() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/extra-certs-missing-cert.xml");
        assertThatThrownBy(() -> verifier.verify(TEST_ORG_ID, CORRECT_VALIDATION_DATE))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Test that when an encapsulated certificate has its digest mangled, exception is thrown.
     *
     * @throws Exception if error occurs
     */
    @Test
    void extraCertsDigestInvalid() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/extra-certs-digest-invalid.xml");
        assertThatThrownBy(() -> verifier.verify(TEST_ORG_ID, CORRECT_VALIDATION_DATE))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Tests that if the signature contains no OCSP responses, exception is thrown.
     *
     * @throws Exception if error occurs
     */
    @Test
    void ocspNoResponses() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("src/test/signatures/sign-0-ocsp-no-responses.xml");
        assertThatThrownBy(() -> verifier.verify(CONSUMER_ID, CORRECT_VALIDATION_DATE))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_MALFORMED_SIGNATURE);
    }

    /**
     * Tests that if the hashes of the attachments do not match,
     * verification fails.
     *
     * @throws Exception if error occurs
     */
    @Test
    void invalidAttachmentHash() throws Exception {
        List<MessagePart> hashes = new ArrayList<>();
        hashes.add(new MessagePart(MESSAGE, SHA512, hash("foo"), hash("foo")));

        SignatureVerifier verifier = createSignatureVerifier("../../common/common-test/src/test/signatures/sign-0.xml");
        verifier.addParts(hashes);

        assertThatThrownBy(() -> verifier.verify(CONSUMER_ID, CORRECT_VALIDATION_DATE))
                .isInstanceOf(CodedException.class)
                .hasMessageContaining(X_INVALID_SIGNATURE_VALUE);
    }

    @Nested
    class NonBatchSignature {
        private static final String NON_BATCH_SIG = "src/test/signatures/non-batch-sig/signatures.xml";
        private static final Date VALIDATION_DATE = createDate(9, 6, 2024);

        static final ClientId DEV_CLIENT = ClientId.Conf.create("DEV", "COM", "4321");
        private final byte[] messageBytes = fileToBytes("src/test/signatures/non-batch-sig/message.xml");
        private final byte[] attachmentBytes = fileToBytes("src/test/signatures/non-batch-sig/attachment1");

        @BeforeEach
        void before() {
            loadGlobalConf("../globalconf-core/src/test/resources/globalconf_good2_v3",
                    "../globalconf-core/src/test/resources/configuration-anchor1.xml", false);
        }

        @Test
        void verifyValid() throws Exception {
            List<MessagePart> hashes = new ArrayList<>();
            hashes.add(new MessagePart(MESSAGE, SHA512, calculateDigest(SHA512, messageBytes), messageBytes));
            hashes.add(new MessagePart(attachmentOfIdx(1), SHA512, calculateDigest(SHA512, attachmentBytes), null));

            SignatureVerifier verifier = createSignatureVerifier(NON_BATCH_SIG);
            verifier.addParts(hashes);

            verifier.verify(DEV_CLIENT, VALIDATION_DATE);
        }

        @Test
        void failOnInvalidHash() throws Exception {
            List<MessagePart> hashes = new ArrayList<>();
            hashes.add(new MessagePart(MESSAGE, SHA512, calculateDigest(SHA512, messageBytes), messageBytes));
            hashes.add(new MessagePart(attachmentOfIdx(1), SHA512, calculateDigest(SHA512, new byte[]{1}), null));

            SignatureVerifier verifier = createSignatureVerifier(NON_BATCH_SIG);
            verifier.addParts(hashes);

            assertThatThrownBy(() -> verifier.verify(DEV_CLIENT, VALIDATION_DATE))
                    .isInstanceOf(CodedException.class)
                    .hasMessageContaining(X_INVALID_SIGNATURE_VALUE);
        }

    }

    private SignatureVerifier createSignatureVerifier(String signaturePath) throws Exception {
        return new SignatureVerifier(globalConfProvider, signature(signaturePath));
    }

    private SignatureVerifier createSignatureVerifier(String signatureFileName, String hashChainResultFileName,
                                                      HashChainReferenceResolver resolver) throws Exception {
        Signature signature = signature(signatureFileName);

        SignatureVerifier verifier = new SignatureVerifier(globalConfProvider, signature, loadFile(hashChainResultFileName), null);

        verifier.setHashChainResourceResolver(resolver);

        return verifier;
    }

    private static byte[] hash(String input) {
        return input.getBytes(StandardCharsets.UTF_8);
    }

    @SneakyThrows
    private static byte[] fileToBytes(String fileName) {
        try (InputStream file = file(fileName)) {
            return IOUtils.toByteArray(file);
        }
    }

    private static String loadFile(String fileName) throws Exception {
        return IOUtils.toString(file(fileName), StandardCharsets.UTF_8);
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

    @SuppressWarnings("checkstyle:FinalClass")
    private static class Resolver implements HashChainReferenceResolver {

        private final Map<String, String> resources = new HashMap<>();

        Resolver withHashChain(String fileName) {
            return add(MessageFileNames.SIG_HASH_CHAIN, fileName);
        }

        Resolver withMessage(String fileName) {
            return add(MESSAGE, fileName);
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

    void loadGlobalConf(String globalConfPath, String configurationAnchorFile, boolean useTestCaCert) {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, globalConfPath);
        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE, configurationAnchorFile);

        globalConfProvider = new TestGlobalConfImpl() {
            @Override
            public X509Certificate getCaCert(String instanceIdentifier, X509Certificate memberCert)
                    throws CertificateEncodingException, IOException {
                if (useTestCaCert) {
                    return TestCertUtil.getCaCert();
                } else {
                    return super.getCaCert(instanceIdentifier, memberCert);
                }
            }
        };
    }
}
