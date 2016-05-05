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
package ee.ria.xroad.common.hashchain;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.SchemaValidator;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.*;

/**
 * Verification of hash chains.
 */
public final class HashChainVerifier {
    private static final Logger LOG = LoggerFactory.getLogger(
            HashChainVerifier.class);

    /** For accessing JAXB functionality. Shared between all the verifiers. */
    private static JAXBContext jaxbCtx;

    private InputStream hashChainResultXml;
    private HashChainReferenceResolver referenceResolver;
    private Map<String, DigestValue> inputs;

    /**
     * Caches already retrieved and parsed hash chains.
     * Map from URI to contents.
     */
    private Map<String, HashChainType> hashChainCache = new HashMap<>();

    /** Records set of inputs that were used by the hash chain calculation. */
    private Set<String> usedInputs = new HashSet<>();

    /**
     * Verifies a set of inputs with respect to hash chain result.
     * Silently returns when all the inputs are correctly referenced by the
     * hash chain. Throws exception on error.
     * @param hashChainResultXml hash chain result that is the starting point
     *                           of the verification.
     * @param referenceResolver used to resolve references from hash chain
     *                          result and hash chains.
     *                          The resolver is not called for file names
     *                          that are given as inputs that have the
     *                          target present.
     * @param inputs set of inputs that are to be verified with respect to the
     *               hash chain result. All the inputs must be referenced by
     *               the hash chain in order to the verification to succeed.
     *               The parameter should contain mapping from file names
     *               to input hashes. The Input object (key value) can be null,
     *               in which case the reference resolver is used to
     *               download the input data for hashing.
     * @throws Exception in case of any errors
     */
    public static void verify(InputStream hashChainResultXml,
            HashChainReferenceResolver referenceResolver,
            Map<String, DigestValue> inputs) throws Exception {
        new HashChainVerifier(hashChainResultXml, referenceResolver,
                inputs).verify();
    }

    private HashChainVerifier(InputStream hashChainResultXml,
            HashChainReferenceResolver referenceResolver,
            Map<String, DigestValue> inputs) {
        this.hashChainResultXml = hashChainResultXml;
        this.referenceResolver = referenceResolver;
        this.inputs = inputs;
    }

    private void verify() throws Exception {
        LOG.trace("verify()");

        HashChainResultType hashChainResult =
                parseHashChainResult(hashChainResultXml);

        // Resolve last hash step in chain.
        byte[] hashStepData = resolveHashStep(hashChainResult.getURI(), null);

        // Digest the last hash step result.
        byte[] digestedData = calculateDigest(getAlgorithmId(
                        hashChainResult.getDigestMethod().getAlgorithm()),
                        hashStepData);

        // Compare with the signed hash chain result.
        if (!Arrays.equals(digestedData, hashChainResult.getDigestValue())) {
            throw new CodedException(X_INVALID_HASH_CHAIN_RESULT,
                    "Hash chain result does not match hash chain calculation");
        }

        checkReferencedInputs();
    }

    private void checkReferencedInputs() {
        LOG.trace("checkReferencedInputs(). Used = {}", usedInputs);
        Set<String> untouchedInputs = new HashSet<>(inputs.keySet());
        untouchedInputs.removeAll(usedInputs);

        if (!untouchedInputs.isEmpty()) {
            throw new CodedException(X_HASHCHAIN_UNUSED_INPUTS,
                    "Some inputs were not referenced by hash chain: %s",
                    StringUtils.join(untouchedInputs, ", "));
        }
    }

    @SuppressWarnings("unchecked")
    private static HashChainResultType parseHashChainResult(InputStream xml)
            throws Exception {
        Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
        JAXBElement<HashChainResultType> element =
                (JAXBElement<HashChainResultType>) unmarshaller.unmarshal(xml);

        HashChainValidator.validate(new JAXBSource(jaxbCtx, element));

        return element.getValue();
    }

    @SuppressWarnings("unchecked")
    private static HashChainType parseHashChain(InputStream xml)
            throws Exception {
        Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
        JAXBElement<HashChainType> element =
                (JAXBElement<HashChainType>) unmarshaller.unmarshal(xml);

        HashChainValidator.validate(new JAXBSource(jaxbCtx, element));

        return element.getValue();
    }

    /**
     * Downloads hash step, calculates all the values and concatenates the
     * results to DigestList data structure.
     * @return DER-encoding of the DigestList data structure.
     */
    private byte[] resolveHashStep(String uri, HashChainType currentChain)
            throws Exception {
        LOG.trace("resolveHashStep({})", uri);

        Pair<HashStepType, HashChainType> hashStep =
                fetchHashStep(uri, currentChain);

        List<AbstractValueType> values =
                hashStep.getLeft().getHashValueOrStepRefOrDataRef();

        DigestValue[] digests = new DigestValue[values.size()];

        // Calculate digests of all the individual values in the hash step
        for (int i = 0; i < digests.length; ++i) {
            digests[i] = resolveValue(values.get(i), hashStep.getRight());
        }

        return DigestList.concatDigests(digests);
    }

    /** Calculates digest of the value in a hash step. */
    private DigestValue resolveValue(AbstractValueType value,
            HashChainType currentChain) throws Exception {
        LOG.trace("resolveValue({})", value);

        if (value instanceof DataRefType) {
            return resolveDataRef((DataRefType) value, currentChain);
        } else if (value instanceof StepRefType) {
            return resolveStepRef((StepRefType) value, currentChain);
        } else if (value instanceof HashValueType) {
            return resolveHashValue((HashValueType) value, currentChain);
        } else {
            throw new IllegalArgumentException("Unknown value type");
        }
    }

    private DigestValue resolveHashValue(HashValueType hashValue,
            HashChainType currentChain) {
        // Everything is already done for us.
        String digestMethodUri =
                getValueDigestMethodUri(hashValue, currentChain);
        return new DigestValue(digestMethodUri, hashValue.getDigestValue());
    }

    private DigestValue resolveStepRef(StepRefType stepRef,
            HashChainType currentChain) throws Exception {
        // Calculate result of the referenced hash step
        byte[] resolved = resolveHashStep(stepRef.getURI(), currentChain);
        String digestMethodUri = getValueDigestMethodUri(stepRef, currentChain);

        // Digest the hash step result.
        return new DigestValue(
                digestMethodUri,
                calculateDigest(getAlgorithmId(digestMethodUri), resolved));
    }

    private DigestValue resolveDataRef(DataRefType dataRef,
            HashChainType currentChain) throws Exception {
        if (isInputRef(dataRef)) {
            // Hash chain referenced given input
            usedInputs.add(dataRef.getURI());
        }

        // Check if we can use the DigestValue in the input.
        DigestValue inputDigest = getMatchingInputHash(dataRef, currentChain);
        if (inputDigest != null) {
            return inputDigest;
        }

        // We'll have to retrieve and hash the data.

        String digestMethodUri =
                getValueDigestMethodUri(dataRef, currentChain);

        if (!referenceResolver.shouldResolve(dataRef.getURI(),
                dataRef.getDigestValue())) {
            return new DigestValue(digestMethodUri, dataRef.getDigestValue());
        }

        InputStream toDigest;

        // Check if we need to run transforms on the input data.
        if (dataRef.getTransforms() != null) {
            toDigest = performTransforms(
                    dataRef.getURI(), dataRef.getTransforms());
        } else {
            // No transforms, just resolve the URI.
            toDigest = referenceResolver.resolve(dataRef.getURI());
        }

        if (toDigest == null) {
            throw new CodedException(X_INVALID_REFERENCE,
                    "Cannot resolve URI: %s", dataRef.getURI());
        }

        byte[] digest =
                calculateDigest(getAlgorithmId(digestMethodUri), toDigest);

        // Compare the calculated digest with the digest in the hash chain
        if (!Arrays.equals(digest, dataRef.getDigestValue())) {
            LOG.debug("Calculated: {}", encodeBase64(digest));
            throw new CodedException(X_INVALID_HASH_CHAIN_REF,
                    "Invalid digest value in hash chain reference to %s",
                    dataRef.getURI());
        }

        return new DigestValue(digestMethodUri, digest);
    }

    /** Returns DigestValue from input if it exactly matches the reference. */
    private DigestValue getMatchingInputHash(DataRefType dataRef,
            HashChainType currentChain) {
        DigestValue inputDigest = inputs.get(dataRef.getURI());
        if (inputDigest != null
                && inputDigest.getDigestMethod().equals(
                    getValueDigestMethodUri(dataRef, currentChain))) {
            return inputDigest;
        } else {
            return null;
        }
    }

    private boolean isInputRef(DataRefType dataRef) {
        return inputs.containsKey(dataRef.getURI());
    }

    /** Downloads data from the URI and runs transforms on it. */
    private InputStream performTransforms(String uri, TransformsType transforms)
            throws Exception {
        LOG.trace("performTransforms({}, {})", uri, transforms);

        JAXBElement<TransformsType> transformsElement =
                new ObjectFactory().createTransforms(transforms);

        // Create the Document
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.newDocument();

        Marshaller marshaller = jaxbCtx.createMarshaller();
        marshaller.marshal(transformsElement, document);

        Transforms tr = new Transforms(document.getDocumentElement(), null);

        XMLSignatureInput before = new XMLSignatureInput(
                referenceResolver.resolve(uri));

        XMLSignatureInput after = tr.performTransforms(before);

        return after.getOctetStream();
    }

    /**
     * Returns either the digest method from value or the hash chain default.
     */
    private String getValueDigestMethodUri(AbstractValueType value,
            HashChainType currentChain) {
        if (value.getDigestMethod() != null
                && value.getDigestMethod().getAlgorithm() != null) {
            return value.getDigestMethod().getAlgorithm();
        } else {
            return currentChain.getDefaultDigestMethod().getAlgorithm();
        }
    }

    /** Retrieve hash step based on the URI. */
    private Pair<HashStepType, HashChainType> fetchHashStep(
            String uri, HashChainType currentChain) throws Exception {
        // Find the fragment separator.
        int hashIndex = uri.indexOf('#');

        if (hashIndex < 0) {
            throw new CodedException(X_MALFORMED_HASH_CHAIN,
                    "Invalid hash step URI: %s", uri);
        }

        String baseUri = uri.substring(0, hashIndex);
        String fragment = uri.substring(hashIndex + 1);

        if (fragment.isEmpty()) {
            // Hash step must be indicated by a fragment in a hash chain.
            throw new CodedException(X_MALFORMED_HASH_CHAIN,
                    "Invalid hash step URI: %s", uri);
        }

        HashChainType hashChain;

        if (baseUri.isEmpty()) {
            hashChain = currentChain;
        } else {
            hashChain = getHashChain(baseUri);
        }

        // Found the hash chain. Look for a step with given ID.
        for (HashStepType step: hashChain.getHashStep()) {
            if (fragment.equals(step.getId())) {
                return new ImmutablePair<>(step, hashChain);
            }
        }

        // No hash step with given fragment ID found.
        throw new CodedException(X_MALFORMED_HASH_CHAIN,
                "Invalid hash step URI: %s", uri);
    }

    private HashChainType getHashChain(String uri) throws Exception {
        HashChainType ret = hashChainCache.get(uri);
        if (ret == null) {
            InputStream is = referenceResolver.resolve(uri);
            if (is == null) {
                throw new CodedException(X_INVALID_REFERENCE,
                        "Cannot resolve URI: %s", uri);
            }
            ret = parseHashChain(is);
            hashChainCache.put(uri, ret);
        }

        return ret;
    }

    static class HashChainValidator extends SchemaValidator {
        private static Schema schema;

        static {
            schema = createSchema("hashchain.xsd");
        }

        public static void validate(Source source) throws Exception {
            validate(schema, source, X_MALFORMED_HASH_CHAIN);
        }
    }

    static {
        try {
            jaxbCtx = JAXBContext.newInstance(ObjectFactory.class);
        } catch (Exception ex) {
            LOG.error("Failed to initialize JAXB context", ex);
        }
    }
}
