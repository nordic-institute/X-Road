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
package ee.ria.xroad.common.hashchain;

import ee.ria.xroad.common.util.ExpectedCodedException;

import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.crypto.dsig.DigestMethod;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_HASHCHAIN_UNUSED_INPUTS;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HASH_CHAIN_REF;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HASH_CHAIN_RESULT;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_HASH_CHAIN;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getAlgorithmId;
import static ee.ria.xroad.common.util.MessageFileNames.MESSAGE;
import static ee.ria.xroad.common.util.MessageFileNames.attachment;

/**
 * Tests to verify that hash chain verification is correct.
 */
public class HashChainVerifierTest {
    private static final Logger LOG = LoggerFactory.getLogger(
            HashChainVerifierTest.class);

    private static final String HASH_CHAIN = "/hashchain.xml";

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Simple test case, input is detached.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleCorrect() throws Exception {
        LOG.info("simpleCorrect()");

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier1-hashchain.xml",
                MESSAGE, "hc-verifier1-message.xml");

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, null);

        HashChainVerifier.verify(
                load("hc-verifier1-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Simple test case, input is attached.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleAttachedInput() throws Exception {
        LOG.info("simpleAttachedInput()");

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier1-hashchain.xml");

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, "hc-verifier1-message.xml");

        HashChainVerifier.verify(
                load("hc-verifier1-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Simple test case with unused inputs.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleUnusedInputs() throws Exception {
        LOG.info("simpleUnusedInputs()");

        thrown.expectErrorSuffix(X_HASHCHAIN_UNUSED_INPUTS);

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier1-hashchain.xml");

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, "hc-verifier1-message.xml",
                "/unused1", null,
                "/unused2", null);

        HashChainVerifier.verify(
                load("hc-verifier1-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Simple test case, hash chain result does not match
     * the result of hash chain calculation.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleDigestMismatch() throws Exception {
        LOG.info("simpleDigestMismatch()");

        thrown.expectErrorSuffix(X_INVALID_HASH_CHAIN_RESULT);

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier1-hashchain.xml");

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, "hc-verifier1-hashchain.xml");

        HashChainVerifier.verify(
                load("hc-verifier1-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Simple test case, hash chain is split into two files.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void simpleTwoHashChains() throws Exception {
        LOG.info("simpleTwoHashChains()");

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier4-hashchain1.xml",
                "/hashchain2.xml", "hc-verifier4-hashchain2.xml",
                MESSAGE, "hc-verifier4-message.xml");

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, null);

        HashChainVerifier.verify(
                load("hc-verifier4-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Test case with multiple attachments.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void attachments() throws Exception {
        LOG.info("attachments()");

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier2-hashchain.xml");

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, new DigestValue(
                    DigestMethod.SHA256, new byte[] {(byte) 11 }),
                attachment(1), new DigestValue(
                    DigestMethod.SHA256, new byte[] {(byte) 12 }),
                attachment(2), new DigestValue(
                    DigestMethod.SHA256, new byte[] {(byte) 13 }),
                attachment(3), new DigestValue(
                    DigestMethod.SHA256, new byte[] {(byte) 14 }));

        HashChainVerifier.verify(
                load("hc-verifier2-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Test case with unresolvable attachments.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void attachmentsCannotResolve() throws Exception {
        LOG.info("attachments()");

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier2-hashchain.xml") {
                    @Override
                    public boolean shouldResolve(String uri, byte[] digestValue) {
                        switch (uri) {
                            case "/attachment1":
                            case "/attachment2":
                            case "/attachment3":
                                return false;
                            default:
                                return true;
                        }
                    }
        };

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, new DigestValue(
                    DigestMethod.SHA256, new byte[] {(byte) 11 }));

        HashChainVerifier.verify(
                load("hc-verifier2-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Test case with a valid transformation.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void transforms() throws Exception {
        LOG.info("transforms()");

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier3-hashchain.xml",
                MESSAGE, "hc-verifier3-message.xml");

        Map<String, DigestValue> inputs = makeInputs(MESSAGE, null);

        HashChainVerifier.verify(
                load("hc-verifier3-hashchainresult.xml"),
                resolver, inputs);
    }

    /**
     * Test case with an invalid transformation.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void transformsError() throws Exception {
        LOG.info("transformsError()");

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier3-hashchain-invalid.xml",
                MESSAGE, "hc-verifier3-message.xml");

        Map<String, DigestValue> inputs = makeInputs(MESSAGE, null);

        thrown.expectErrorSuffix(X_INVALID_HASH_CHAIN_REF);

        HashChainVerifier.verify(
                load("hc-verifier3-hashchainresult.xml"),
                resolver, inputs);
    }

    @Test
    public void invalidSchema() throws Exception {
        thrown.expectError(X_MALFORMED_HASH_CHAIN);

        Resolver resolver = new Resolver(
                HASH_CHAIN, "hc-verifier5-hashchain-invalid-schema.xml",
                MESSAGE, "hc-verifier3-message.xml");

        Map<String, DigestValue> inputs = makeInputs(
                MESSAGE, null);

        HashChainVerifier.verify(
                load("hc-verifier3-hashchainresult.xml"),
                resolver, inputs);
    }

    private static Map<String, DigestValue> makeInputs(Object... items)
            throws Exception {
        Map<String, DigestValue> ret = new HashMap<>();

        for (int i = 0; i < items.length; i += 2) {
            String uri = (String) items[i];
            if (items[i + 1] == null) {
                ret.put(uri, null);
            } else if (items[i + 1] instanceof String) {
                String fileName = (String) items[i + 1];
                ret.put(uri,
                        new DigestValue(
                                DigestMethod.SHA256,
                                calculateDigest(
                                        getAlgorithmId(DigestMethod.SHA256),
                                        load(fileName))));
            } else if (items[i + 1] instanceof DigestValue) {
                ret.put(uri, (DigestValue) items[i + 1]);
            } else {
                byte[] data = (byte[]) items[i + 1];
                ret.put(uri,
                        new DigestValue(
                                DigestMethod.SHA256,
                                calculateDigest(
                                        getAlgorithmId(DigestMethod.SHA256),
                                        data)));
            }
        }

        return ret;
    }

    private static class Resolver implements HashChainReferenceResolver {

        private final Map<String, String> resources = new HashMap<>();

        Resolver(String... items) {
            for (int i = 0; i < items.length; i += 2) {
                resources.put(items[i], items[i + 1]);
            }
        }

        @Override
        public InputStream resolve(String uri) {
            LOG.debug("resolve({})", uri);
            if (resources.containsKey(uri)) {
                String fileName = resources.get(uri);
                LOG.debug("Returning file {}", fileName);
                return load(fileName);
            } else {
                throw new IllegalArgumentException("Invalid URI: " + uri);
            }
        }

        @Override
        public boolean shouldResolve(String uri, byte[] digestValue) {
            LOG.debug("shouldResolve({})", uri);
            return true;
        }
    }

    private static InputStream load(String fileName) {
        LOG.debug("load({})", fileName);
        return Thread.currentThread()
                .getContextClassLoader()
                    .getResourceAsStream(fileName);
    }

    static {
        org.apache.xml.security.Init.init();
    }
}
