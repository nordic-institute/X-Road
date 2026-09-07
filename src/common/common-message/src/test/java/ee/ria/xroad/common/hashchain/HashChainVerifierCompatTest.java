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

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.util.MessageFileNames.MESSAGE;
import static ee.ria.xroad.common.util.MessageFileNames.attachmentOfIdx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backward-compatibility safety net: verifies that legitimate hash chains
 * produced by HashChainBuilder pass through HashChainVerifier with the
 * expected, bounded resolution cost.
 */
class HashChainVerifierCompatTest {

    private static final String CHAIN_FILE = "/sig-hashchain.xml";
    private static final int LARGE_BATCH_SIZE = 4096;
    private static final int MEDIUM_BATCH_SIZE = 7;

    static {
        org.apache.xml.security.Init.init();
    }

    @Test
    void singleMessageWithAttachmentVerifies() throws Exception {
        byte[] messageHash = digest("message-body");
        byte[] attachment1Hash = digest("attachment-1");
        byte[] attachment2Hash = digest("attachment-2");

        HashChainBuilder builder = new HashChainBuilder(DigestAlgorithm.SHA256);
        builder.addInputHash(new byte[][]{messageHash, attachment1Hash, attachment2Hash});
        builder.finishBuilding();

        String chainResult = builder.getHashChainResult(CHAIN_FILE);
        String[] chains = builder.getHashChains(MESSAGE);

        Map<String, DigestValue> inputs = new HashMap<>();
        inputs.put(MESSAGE, new DigestValue(DigestAlgorithm.SHA256, messageHash));
        inputs.put(attachmentOfIdx(1), new DigestValue(DigestAlgorithm.SHA256, attachment1Hash));
        inputs.put(attachmentOfIdx(2), new DigestValue(DigestAlgorithm.SHA256, attachment2Hash));

        CountingResolver resolver = new CountingResolver(CHAIN_FILE, chains[0]);

        HashChainVerifier.verify(stream(chainResult), resolver, inputs);

        assertThat(resolver.totalResolveCalls()).isEqualTo(1);
        assertThat(resolver.resolveCallsFor(CHAIN_FILE)).isEqualTo(1);
    }

    @Test
    void singleNonMultipartMessageCannotBuildHashChain() throws Exception {
        HashChainBuilder builder = new HashChainBuilder(DigestAlgorithm.SHA256);
        builder.addInputHash(digest("only-message"));
        builder.finishBuilding();

        assertThatThrownBy(() -> builder.getHashChainResult(CHAIN_FILE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("single input");
        assertThatThrownBy(() -> builder.getHashChains(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("single input");
    }

    @Test
    void multiBatchWithMultipartLeafVerifies() throws Exception {
        byte[] msg0Hash = digest("batch-msg-0");
        byte[] msg1Body = digest("batch-msg-1-body");
        byte[] msg1Att1 = digest("batch-msg-1-att1");
        byte[] msg2Hash = digest("batch-msg-2");
        byte[] msg3Hash = digest("batch-msg-3");

        HashChainBuilder builder = new HashChainBuilder(DigestAlgorithm.SHA256);
        builder.addInputHash(msg0Hash);
        builder.addInputHash(new byte[][]{msg1Body, msg1Att1});
        builder.addInputHash(msg2Hash);
        builder.addInputHash(msg3Hash);
        builder.finishBuilding();

        String chainResult = builder.getHashChainResult(CHAIN_FILE);
        String[] chains = builder.getHashChains(MESSAGE);

        Map<String, DigestValue> inputs = new HashMap<>();
        inputs.put(MESSAGE, new DigestValue(DigestAlgorithm.SHA256, msg1Body));
        inputs.put(attachmentOfIdx(1), new DigestValue(DigestAlgorithm.SHA256, msg1Att1));

        CountingResolver resolver = new CountingResolver(CHAIN_FILE, chains[1]);

        HashChainVerifier.verify(stream(chainResult), resolver, inputs);

        int expectedDepth = ceilingLog2(4) + 1;
        int stepsInChain = countSteps(chains[1]);
        assertThat(stepsInChain)
                .as("multipart leaf chain depth = ceil(log2(4)) + 1 attachment step")
                .isEqualTo(expectedDepth);
        assertThat(resolver.resolveCallsFor(CHAIN_FILE)).isEqualTo(1);
    }

    @Test
    void multiBatchSeveralLeavesVerify() throws Exception {
        int[] verifyIndices = {0, 1, MEDIUM_BATCH_SIZE / 2, MEDIUM_BATCH_SIZE - 1};
        byte[][] hashes = buildHashes(MEDIUM_BATCH_SIZE);

        HashChainBuilder builder = new HashChainBuilder(DigestAlgorithm.SHA256);
        for (byte[] hash : hashes) {
            builder.addInputHash(hash);
        }
        builder.finishBuilding();

        String chainResult = builder.getHashChainResult(CHAIN_FILE);
        String[] chains = builder.getHashChains(MESSAGE);

        for (int leafIndex : verifyIndices) {
            Map<String, DigestValue> inputs = new HashMap<>();
            inputs.put(MESSAGE, new DigestValue(DigestAlgorithm.SHA256, hashes[leafIndex]));

            CountingResolver resolver = new CountingResolver(CHAIN_FILE, chains[leafIndex]);

            HashChainVerifier.verify(stream(chainResult), resolver, inputs);

            assertThat(resolver.totalResolveCalls())
                    .as("leaf %d chain file fetches", leafIndex)
                    .isEqualTo(1);
            assertThat(resolver.resolveCallsFor(CHAIN_FILE))
                    .as("leaf %d chain file fetched exactly once", leafIndex)
                    .isEqualTo(1);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void largebatchVerifiesWithBoundedDepthAndCost() throws Exception {
        byte[][] hashes = buildHashes(LARGE_BATCH_SIZE);

        HashChainBuilder builder = new HashChainBuilder(DigestAlgorithm.SHA256);
        for (byte[] hash : hashes) {
            builder.addInputHash(hash);
        }
        builder.finishBuilding();

        String chainResult = builder.getHashChainResult(CHAIN_FILE);
        String[] chains = builder.getHashChains(MESSAGE);

        int firstLeaf = 0;
        int midLeaf = LARGE_BATCH_SIZE / 2;
        int lastLeaf = LARGE_BATCH_SIZE - 1;

        for (int leafIndex : new int[]{firstLeaf, midLeaf, lastLeaf}) {
            Map<String, DigestValue> inputs = new HashMap<>();
            inputs.put(MESSAGE, new DigestValue(DigestAlgorithm.SHA256, hashes[leafIndex]));

            CountingResolver resolver = new CountingResolver(CHAIN_FILE, chains[leafIndex]);

            HashChainVerifier.verify(stream(chainResult), resolver, inputs);

            int expectedDepth = ceilingLog2(LARGE_BATCH_SIZE);
            int stepsInChain = countSteps(chains[leafIndex]);

            assertThat(stepsInChain)
                    .as("leaf %d: chain depth should equal ceil(log2(%d))", leafIndex, LARGE_BATCH_SIZE)
                    .isEqualTo(expectedDepth);

            assertThat(resolver.resolveCallsFor(CHAIN_FILE))
                    .as("leaf %d: chain file resolved exactly once", leafIndex)
                    .isEqualTo(1);
        }
    }

    private static byte[][] buildHashes(int count) throws Exception {
        byte[][] hashes = new byte[count][];
        for (int i = 0; i < count; i++) {
            hashes[i] = digest("message-" + i);
        }
        return hashes;
    }

    private static byte[] digest(String data) throws Exception {
        return calculateDigest(DigestAlgorithm.SHA256,
                data.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static int ceilingLog2(int n) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(n - 1);
    }

    private static int countSteps(String chainXml) {
        int count = 0;
        int idx = 0;
        String marker = "id=\"STEP";
        while ((idx = chainXml.indexOf(marker, idx)) >= 0) {
            count++;
            idx += marker.length();
        }
        return count;
    }

    /**
     * HashChainReferenceResolver that counts calls per URI, enabling assertions
     * on resolution cost for legitimate chains.
     */
    static class CountingResolver implements HashChainReferenceResolver {

        private final Map<String, String> chainContents;
        private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

        CountingResolver(String chainUri, String chainXml) {
            chainContents = new HashMap<>();
            chainContents.put(chainUri, chainXml);
        }

        @Override
        public InputStream resolve(String uri) throws IOException {
            callCounts.computeIfAbsent(uri, k -> new AtomicInteger()).incrementAndGet();
            String content = chainContents.get(uri);
            if (content == null) {
                throw new IOException("Unexpected resolve call for URI: " + uri);
            }
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public boolean shouldResolve(String uri, byte[] digestValue) {
            return chainContents.containsKey(uri);
        }

        int resolveCallsFor(String uri) {
            AtomicInteger counter = callCounts.get(uri);
            return counter == null ? 0 : counter.get();
        }

        int totalResolveCalls() {
            return callCounts.values().stream().mapToInt(AtomicInteger::get).sum();
        }
    }
}
