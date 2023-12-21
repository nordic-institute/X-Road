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
package ee.ria.xroad.common.hashchain;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.hashchain.DigestList.digestHashStep;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmURI;
import static ee.ria.xroad.common.util.MessageFileNames.attachment;
import static java.lang.Integer.numberOfLeadingZeros;

/**
 * Builds Merkle tree from a set of hashes. Then constructs hash chains
 * for all the input hashes.
 *
 * First, call the add method to add inputs to the tree. Then call
 * finishBuilding to compute the tree.
 * After computing, three methods are available to get the results.
 * - getTreeTop -- returns topmost hash of the Merkle tree that can be
 *   signed/time-stamped.
 * - getHashChainResult -- returns XML-encoded form of the hash chain result.
 * - getHashChains -- returns array of XML-encoded hash chains, one for
 *   each input data item.
 *
 * Implementation: the binary Merkle tree is stored as an array.
 * This representation does not use pointers to child nodes, the indexes of
 * children can be calculated from the parent.
 * See http://en.wikipedia.org/wiki/Binary_tree#Arrays for details.
 *
 * Physically, the tree is stored in two separate arrays: inputs (leaf nodes)
 * and nodes (non-leaf nodes). In terms of index calculations these are
 * treated as a single array consisting of nodes+inputs.
 *
 * For incomplete binary trees, some inputs and nodes can be null.
 */
public final class HashChainBuilder {

    private static final int INTEGER_BITS = 32;

    private static final Logger LOG =
            LoggerFactory.getLogger(HashChainBuilder.class);

    /** For accessing JAXB functionality. Shared between all the builders. */
    private static JAXBContext jaxbCtx;

    /**
     * Index of the root of the tree.
     */
    private static final int ROOT_IDX = 0;

    private static final String STEP = "STEP";

    /** Hash algorithm used to hash tree nodes and inputs. */
    private final String hashAlgorithm;

    /** Hash algorithm URI used in XML. */
    private final String hashAlgorithmUri;

    /** Array of input hashes. */
    private final List<byte[]> inputs = new ArrayList<>();

    /**
     * If an input consisted of multipart (message + attachments),
     * then this map contains all the parts.
     */
    private final Map<Integer, byte[][]> multiparts = new HashMap<>();

    /** The file name to be used for data refs. */
    private String dataRefFileName;

    /** Array of intermediate Merkle tree nodes. */
    private byte[][] nodes;

    /** Maximum index a tree node can have. */
    private int maxIndex;

    /** Used for serializing XML objects. */
    private Marshaller marshaller;

    /** Factory for creating XML objects. */
    private ObjectFactory objectFactory = new ObjectFactory();

    /**
     * Constructs a hash chain builder.
     * @param hashAlgorithm Identifier (not URL) of the hash algorithm
     *                      used in the hash chain. We assume that the
     *                      input data items were created with the same
     *                      algorithm. Example: SHA-256.
     * @throws Exception in case of errors
     */
    public HashChainBuilder(String hashAlgorithm) throws Exception {
        this.hashAlgorithm = hashAlgorithm;
        hashAlgorithmUri = getDigestAlgorithmURI(hashAlgorithm);

        marshaller = jaxbCtx.createMarshaller();
        // Format the XML, good for debugging.
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    }

    /**
     * Adds new input hash to the tree.
     * @param hash input hash to add
     */
    public void addInputHash(byte[] hash) {
        if (nodes != null) {
            throw new IllegalStateException(
                    "Cannot add inputs to finished tree");
        }
        inputs.add(hash);
    }

    /**
     * Adds a set of input hashes to the tree.
     * It is assumed that all the hashes come from the same message,
     * the first one being SOAP message and the rest being attachments.
     * @param hashes set of input nashes to add
     * @throws Exception in case of errors
     */
    public void addInputHash(byte[][] hashes) throws Exception {
        if (nodes != null) {
            throw new IllegalStateException(
                    "Cannot add inputs to finished tree");
        }

        if (hashes.length == 1) {
            inputs.add(hashes[0]);
        } else {
            // Digest the attachments and add a single input.
            inputs.add(digestHashStep(hashAlgorithm, hashes));
            // Record the original inputs in separate map.
            multiparts.put(inputs.size() - 1, hashes);
        }
    }

    /**
     * Finalizes the tree and computes the intermediate nodes and top hash.
     * @throws Exception in case of errors
     */
    public void finishBuilding() throws Exception {
        // Create array for intermediate nodes.
        nodes = new byte[getNodesCount()][];

        // For special cases of 0 or 1 inputs, we behave differently.
        if (inputs.size() < 2) {
            return;
        }

        maxIndex = nodes.length + inputs.size();

        // Hash input data items to produce lowest level of non-leaf nodes.
        hashInputs();

        // Hash nodes, starting from the bottom.
        hashNodes();

        // If the tree is an incomplete binary tree, add additional nodes
        // to take care of the "orphans".
        fixTree(ROOT_IDX);
    }

    /**
     * Returns the top hash of the Merkle tree, encoded as the HashChainResult
     * XML element. This data can be signed or time-stamped.
     * @param hashChainFileName name of the file containing the hash chain
     * @return top hash of the Merkle tree, encoded as the HashChainResult
     * XML element
     * @throws Exception in case of errors
     */
    public String getHashChainResult(String hashChainFileName)
            throws Exception {
        if (nodes == null) {
            throw new IllegalStateException("Tree must be finished");
        }

        if (inputs.isEmpty()) {
            // Nothing to do for empty tree.
            return null;
        }

        if (inputs.size() == 1 && multiparts.isEmpty()) {
            throw new IllegalStateException(
                    "Hash chains cannot be constructed for single input "
                            + "without attachments.");
        }

        HashChainResultType result = new HashChainResultType();

        result.setDigestValue(getTreeTop());
        result.setDigestMethod(digestMethod());
        result.setURI(hashChainFileName + "#" + STEP + "0");

        return elementToString(objectFactory.createHashChainResult(result));
    }

    /**
     * Returns XML-encoded hash chain for every input data item.
     * @param dataFileName name of the file containing data input items
     * @return XML-encoded hash chain for every input data item
     * @throws Exception in case of any errors
     */
    public String[] getHashChains(String dataFileName) throws Exception {
        if (nodes == null) {
            throw new IllegalStateException("Tree must be finished");
        }

        if (inputs.isEmpty()) {
            return null;
        }

        if (inputs.size() == 1 && multiparts.isEmpty()) {
            throw new IllegalStateException(
                    "Hash chains cannot be constructed for single input "
                            + "without attachments.");
        }

        this.dataRefFileName = dataFileName;
        if (dataFileName == null) {
            throw new IllegalArgumentException(
                    "dataRefFileName must not be null");
        }

        String[] ret = new String[inputs.size()];

        if (inputs.size() > 1) {
            for (int i = 0; i < inputs.size(); ++i) {
                ret[i] = makeHashChain(i);
            }
        } else {
            // Special case for one input.
            ret[0] = makeSingleInputHashChain();
        }

        return ret;
    }

    /**
     * Hashes the non-leaf nodes of the tree, breadth-first, bottom-up.
     */
    private void hashNodes() throws Exception {
        // levelStart -- index of first node for this level (depth)
        for (int levelStart = nodes.length / 2; levelStart > 0;
                levelStart /= 2) {
            // End of nodes for this level.
            int levelEnd = levelStart * 2;

            LOG.trace("Combining: {}-{}", levelStart, levelEnd);
            // Walk through the pairs in this level.
            for (int i = levelStart;
                    i < levelEnd && nodes[i] != null && nodes[i + 1] != null;
                    i += 2) {
                // Combine nodes[i] and nodes[i + 1]
                LOG.trace("Nodes: Combining {} and {}", i, i + 1);
                byte[] stepDigest = digestHashStep(hashAlgorithm,
                        nodes[i], nodes[i + 1]);

                // Store the digest as parent of two inputs.
                LOG.trace("Storing at {}", parentIdx(i));
                nodes[parentIdx(i)] = stepDigest;
            }
        }
    }

    /**
     * Walks over pairs of inputs and combines them to create lowest
     * level of non-leaf nodes.
     */
    private void hashInputs() throws Exception {
        for (int i = 0; i < inputs.size() - 1; i += 2) {
            // Compute the index for nodes.
            int itemIdx = nodes.length + i;

            // Combine inputs[i] and inputs[i + 1]
            LOG.trace("Inputs: Combining {} and {}", i, i + 1);
            byte[] stepDigest = digestHashStep(hashAlgorithm,
                    inputs.get(i), inputs.get(i + 1));

            // Store the digest as parent of two inputs.
            LOG.trace("Storing at {}", parentIdx(itemIdx));
            nodes[parentIdx(itemIdx)] = stepDigest;
        }
    }

    /**
     * Returns the topmost hash of the Merkle tree.
     */
    byte[] getTreeTop() {
        if (inputs.size() == 1) {
            // For single input, we do not build the nodes array
            // and directly return the input.
            return inputs.get(0);
        } else {
            return nodes[ROOT_IDX];
        }
    }

    /**
     * For incomplete trees, the hashInputs and hashNodes methods did not
     * create the necessary intermediate nodes. This method walks the tree,
     * discovers the missing nodes and, if necessary, creates them.
     * @return the hash of the fixed tree node.
     */
    private byte[] fixTree(int nodeIdx) throws Exception {
        LOG.trace("fixTree({})", nodeIdx);

        if (nodeIdx >= maxIndex) {
            // Let's not go infinitely deep.
            return null;
        }

        if (get(nodeIdx) != null) {
            // There's nothing to fix, just return the node.
            return get(nodeIdx);
        }

        // Value of the left subtree.
        byte[] leftValue = get(leftIdx(nodeIdx));

        if (leftValue == null) {
            // No left child. In this case, we'll just go down to the
            // left subtree until we find something.
            return fixTree(leftIdx(nodeIdx));
        }

        // To get value of the right subtree, we call fixTree recursively.
        // This handles situations where there are nodes missing on some
        // levels.
        byte[] rightValue = fixTree(rightIdx(nodeIdx));
        if (rightValue == null) {
            // We fould nothing on the right subtree. Just return value
            // of the left subtree.
            LOG.trace("{} -> left({})", nodeIdx, leftIdx(nodeIdx));
            return leftValue;
        }

        // We have values from both left and right subtrees.
        // Combine them and store in the current node.
        byte[] stepDigest = digestHashStep(hashAlgorithm,
                leftValue, rightValue);
        LOG.trace("Fixing: {} + {} -> {}", new Object[] {
                leftIdx(nodeIdx), rightIdx(nodeIdx), nodeIdx });
        nodes[nodeIdx] = stepDigest;
        return stepDigest;
    }

    /**
     * Treats nodes+inputs as a single large array and returns data
     * at a given index.
     */
    private byte[] get(int index) {
        if (index < nodes.length) {
            return nodes[index];
        } else if (index < maxIndex) {
            return inputs.get(index - nodes.length);
        } else {
            return null;
        }
    }

    /**
     * Similar to get(index), but if the data is null then goes down the tree
     * until data is found.
     */
    private byte[] getDeep(int index) {
        byte[] ret = get(index);

        while (ret == null && index < maxIndex) {
            index = leftIdx(index);
            LOG.trace("getDeep() -> {}", index);
            ret = get(index);
        }

        return ret;
    }

    /**
     * Returns XML-encoded hash chain for a n-th input data item.
     */
    private String makeHashChain(int itemIndex) throws Exception {
        LOG.trace("makeHashChain({})", itemIndex);

        HashChainType hashChain = new HashChainType();
        hashChain.setDefaultDigestMethod(digestMethod());

        // Hash step count is used to generate references.
        int stepCount = 0;

        // Start with root node
        int currentNodeIdx = ROOT_IDX;
        // current level will be height of non-leaf part of the tree.
        int currentLevel = ceilingLog2(inputs.size()) - 1;

        // Walk the tree downwards from the root node.
        while (currentNodeIdx < nodes.length) {
            // Indicates whether we are interested in
            // left (0) or right (1) child.
            int myDirection = (itemIndex & (1 << currentLevel)) >> currentLevel;
            LOG.trace("Level {}, direction {}", currentLevel, myDirection);

            int myChildIdx = childIdx(currentNodeIdx, myDirection);
            int otherChildIdx = childIdx(currentNodeIdx, 1 - myDirection);

            // Ignore the missing nodes and walk down the tree until we
            // find some data.
            while (get(myChildIdx) == null) {
                // For missing nodes, always take the left child.
                myChildIdx = leftIdx(myChildIdx);
                LOG.trace("Skipping down, new index = {}", myChildIdx);
                --currentLevel;
            }

            // For the other node, we always use hash value.
            AbstractValueType otherData = hashValue(getDeep(otherChildIdx));
            AbstractValueType myData;

            // If the child is leaf node and there are no attachments,
            // use the data ref. Otherwise use the StepRef.
            if (isLeaf(myChildIdx) && !multiparts.containsKey(itemIndex)) {
                // Plain data ref.
                myData = dataRef(get(myChildIdx));
            } else {
                // Non-leaf nodes refer to other hash steps.
                myData = stepRef(stepCount + 1);
            }

            // Construct the hash step.
            HashStepType hashStep = new HashStepType();
            hashStep.setId(STEP + stepCount);
            // Create two elements.
            hashStep.getHashValueOrStepRefOrDataRef().add(null);
            hashStep.getHashValueOrStepRefOrDataRef().add(null);

            // Set the data items.
            hashStep.getHashValueOrStepRefOrDataRef().set(
                    myDirection, myData);
            hashStep.getHashValueOrStepRefOrDataRef().set(
                    1 - myDirection, otherData);

            // Add to chain
            hashChain.getHashStep().add(hashStep);

            // Update state variables.
            ++stepCount;
            currentNodeIdx = myChildIdx;
            --currentLevel;
        }

        // If the input was a multipart, we need to add final hash
        // step that references all the individual parts.
        if (multiparts.containsKey(itemIndex)) {
            LOG.trace("Adding attachments");
            hashChain.getHashStep().add(
                    multipartStep(multiparts.get(itemIndex), stepCount));
        }

        return elementToString(objectFactory.createHashChain(hashChain));
    }

    /**
     * Creates reference to input data.
     */
    private DataRefType dataRef(byte[] digest) {
        return dataRef(dataRefFileName, digest);
    }

    /**
     * Creates reference to input data with given file name.
     */
    private static DataRefType dataRef(String fileName, byte[] digest) {
        DataRefType dataRef = new DataRefType();
        dataRef.setURI(fileName);
        dataRef.setDigestValue(digest);
        return dataRef;
    }

    /**
     * Creates reference to another hash step.
     */
    private static StepRefType stepRef(int stepCount) {
        StepRefType stepRef = new StepRefType();
        stepRef.setURI("#" + STEP + stepCount);
        return stepRef;
    }

    /**
     * Creates a concrete hash value.
     */
    private static HashValueType hashValue(byte[] data) {
        HashValueType hashValue = new HashValueType();
        hashValue.setDigestValue(data);
        return hashValue;
    }

    /**
     * Makes hash chain for special case of inputs.size() == 1.
     */
    private String makeSingleInputHashChain() throws Exception {
        LOG.trace("makeSingleInputHashChain()");

        HashChainType hashChain = new HashChainType();
        hashChain.setDefaultDigestMethod(digestMethod());

        // This is a multipart input. Add single step for all
        // the input parts
        hashChain.getHashStep().add(multipartStep(multiparts.get(0), 0));

        return elementToString(objectFactory.createHashChain(hashChain));
    }

    private HashStepType multipartStep(byte[][] inputSet, int stepCount) {
        HashStepType ret = new HashStepType();
        ret.setId(STEP + stepCount);

        for (int i = 0; i < inputSet.length; ++i) {
            if (i == 0) {
                // The first input is message
                ret.getHashValueOrStepRefOrDataRef().add(dataRef(inputSet[i]));
            } else {
                // All the other inputs are attachments, starting from 1.
                ret.getHashValueOrStepRefOrDataRef().add(
                        dataRef(attachment(i), inputSet[i]));
            }
        }

        return ret;
    }

    /**
     * Creates a DigestMethod element, based on the current hash algorithm.
     */
    private DigestMethodType digestMethod() {
        DigestMethodType digestMethod = new DigestMethodType();
        digestMethod.setAlgorithm(hashAlgorithmUri);
        return digestMethod;
    }

    /**
     * Serializes the given XML element to a string.
     */
    private <T> String elementToString(JAXBElement<T> element)
            throws Exception {
        StringWriter writer = new StringWriter();
        marshaller.marshal(element, writer);
        return writer.toString();
    }

    /**
     * Returns index for parent of a node identified by childIdx.
     */
    private static int parentIdx(int childIdx) {
        return (childIdx - 1) / 2;
    }

    /**
     * Returns index for left child of a node identified by parentIdx.
     */
    private static int leftIdx(int parentIdx) {
        return childIdx(parentIdx, 0);
    }

    /**
     * Returns index for right child of a node identified by parentIdx.
     */
    private static int rightIdx(int parentIdx) {
        return childIdx(parentIdx, 1);
    }

    /**
     * Returns index for n-th child of a node identified by parentIdx.
     */
    private static int childIdx(int parentIdx, int n) {
        return 2 * parentIdx + 1 + n;
    }

    /**
     * Returns true, if node identified by nodeIdx is a leaf node.
     */
    private boolean isLeaf(int nodeIdx) {
        return nodeIdx >= nodes.length;
    }

    /**
     * Returns size of the array that will hold the non-leaf nodes
     * of the tree.
     */
    private int getNodesCount() {
        return pow2(ceilingLog2(inputs.size())) - 1;
    }

    private static int ceilingLog2(int n) {
        return INTEGER_BITS - numberOfLeadingZeros(n - 1);
    }

    private static int pow2(int n) {
        return 1 << n;
    }

    static {
        try {
            jaxbCtx = JAXBContext.newInstance(ObjectFactory.class);
        } catch (Exception ex) {
            LOG.error("Failed to initialize JAXB context", ex);
        }
    }
}
