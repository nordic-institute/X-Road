package ee.cyber.sdsb.common.signature;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.operator.DigestCalculator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.TestSecurityUtil;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.hashchain.HashChainReferenceResolver;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.MessageFileNames;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

public class SignatureVerifierTest {

    /** The date when the the OCSP responses etc are valid. */
    private static final Date CORRECT_VALIDATION_DATE = createDate(1, 9, 2012);

    /** The correct member name used in the test data. */
    private static final ClientId CORRECT_MEMBER = createClientId("Test Org");

    private static final ClientId SUBSYSTEM =
            createClientId("Test Org", "subsys1");

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    static {
        TestSecurityUtil.initSecurity();
    }

    @Before
    public void setUp() {
        System.setProperty(SystemProperties.GLOBAL_CONFIGURATION_FILE,
                "src/test/globalconftest.xml");
        GlobalConf.reload();
    }

    /**
     * Tests that verifying a valid signature succeeds.
     */
    @Test
    public void verifyValidSignature() throws Exception {
        List<PartHash> hashes = new ArrayList<>();
        hashes.add(new PartHash(MessageFileNames.MESSAGE, SHA512_ID,
                hashFile("message-0.xml", SHA512_ID)));

        SignatureVerifier verifier = createSignatureVerifier("sig-0.xml");
        verifier.addParts(hashes);

        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that verifying a valid signature succeeds.
     */
    @Test
    public void verifyValidSignatureHashChain() throws Exception {
        Resolver resolver = null;

        resolver = new Resolver()
            .withHashChain("hash-chain-1.xml")
            .withMessage("message-1.xml");

        createSignatureVerifier("batch-sig.xml",
                "hash-chain-result.xml", resolver)
            .verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);

        resolver = new Resolver()
            .withHashChain("hash-chain-2.xml")
            .withMessage("message-2.xml");

        createSignatureVerifier("batch-sig.xml",
                "hash-chain-result.xml", resolver)
            .verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test signature from subsystem.
     */
    @Test
    public void verifyValidSubsystem() throws Exception {
        List<PartHash> hashes = new ArrayList<>();
        hashes.add(new PartHash("rnd1-message.xml", SHA512_ID, hash("xxx")));
        hashes.add(new PartHash("rnd2-message.xml", SHA512_ID, hash("yyy")));

        SignatureVerifier verifier = createSignatureVerifier("sig.xml");
        verifier.addParts(hashes);

        verifier.verify(SUBSYSTEM, CORRECT_VALIDATION_DATE);
    }

    /**
     * Tests that reading an empty signature fails.
     */
    @Test
    public void emptySignature() throws Exception {
        thrown.expectError(X_INVALID_XML);
        createSignatureVerifier("empty.xml");
    }

    /**
     * Tests that verifying a signature without ds:Signature element fails.
     */
    @Test
    public void noXadesSignature() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        createSignatureVerifier("no-signature.xml");
    }

    /**
     * Tests that verifying a signature without ObjectContainer element fails.
     */
    @Test
    public void noObjectContainer() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE);
        createSignatureVerifier("no-objectcontainer.xml");
    }

    /**
     * Tests that reading a malformed XML fails.
     */
    @Test
    public void malformedXml() throws Exception {
        thrown.expectError(X_INVALID_XML);
        createSignatureVerifier("malformed-xml.xml");
    }

    /**
     * Tests that validating against the schema fails if the XML does not
     * satisfy the schema. Just changed the name of one element for now.
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
     */
    @Test
    public void invalidSignerName() throws Exception {
        thrown.expectError(X_INCORRECT_CERTIFICATE);
        SignatureVerifier verifier = createSignatureVerifier("good.xml");
        verifier.verify(createClientId("FOORBAR"), null);
    }

    /**
     * Tests that verifying the signature value fails if the value is incorrect.
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
     */
    @Test
    public void extraCerts() throws Exception {
        SignatureVerifier verifier = createSignatureVerifier("extra-certs.xml");
        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    /**
     * Test that when an encapsulated certificate element has missing
     * id attribute, exception is thrown.
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
     */
    @Test
    public void invalidAttachmentHash() throws Exception {
        thrown.expectError(X_INVALID_SIGNATURE_VALUE);
        List<PartHash> hashes = new ArrayList<>();
        hashes.add(new PartHash(MessageFileNames.MESSAGE, SHA512_ID,
                hash("foo")));

        SignatureVerifier verifier = createSignatureVerifier("sig-0.xml");
        verifier.addParts(hashes);

        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    private static SignatureVerifier createSignatureVerifier(String file)
            throws Exception {
        Signature signature = new Signature(file(file));
        return new SignatureVerifier(signature);
    }

    private static SignatureVerifier createSignatureVerifier(
            String signatureFileName, String hashChainResultFileName,
            HashChainReferenceResolver resolver) throws Exception {
        Signature signature = new Signature(file(signatureFileName));

        SignatureVerifier verifier = new SignatureVerifier(signature,
                loadFile(hashChainResultFileName), null);

        verifier.setHashChainResourceResolver(resolver);

        return verifier;
    }

    private static String hash(String input) {
        return encodeBase64(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String hashFile(String fileName, String algorithm)
            throws Exception {
        DigestCalculator calc = createDigestCalculator(algorithm);

        IOUtils.copy(file(fileName), calc.getOutputStream());

        return encodeBase64(calc.getDigest());
    }

    private static String loadFile(String fileName) throws Exception {
        return IOUtils.toString(file(fileName));
    }

    private static InputStream file(String fileName) throws IOException {
        return new FileInputStream("src/test/signatures/" + fileName);
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

    private static ClientId createClientId(String memberCode,
            String subsystemCode) {
        return ClientId.create("EE", "BUSINESS", memberCode, subsystemCode);
    }

    private static class Resolver implements HashChainReferenceResolver {

        private final Map<String, String> resources = new HashMap<>();

        Resolver withHashChain(String fileName) {
            return add(MessageFileNames.HASH_CHAIN, fileName);
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
