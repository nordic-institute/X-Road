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

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static ee.ria.xroad.common.util.CryptoUtils.SHA256_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeHex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify hash chain builder functionality.
 */
public class HashChainBuilderTest {
    private static final Logger LOG = LoggerFactory.getLogger(
            HashChainBuilderTest.class);

    /**
     * Test to ensure hash chain builder works with varying input sizes.
     * @throws Exception in case of unexpected errors
     */
    @SuppressWarnings("squid:S2699")
    @Test
    public void treeBuilding() throws Exception {
        LOG.info("treeBuilding()");
        runBuilder(2);
        runBuilder(3);
        runBuilder(4);
        runBuilder(5);
        runBuilder(6);
        runBuilder(7);
        runBuilder(8);
        runBuilder(9);
        runBuilder(15);
    }

    private static void runBuilder(int count) throws Exception {
        LOG.debug("Running build test for N={}", count);

        HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
        for (int i = 0; i < count; ++i) {
            builder.addInputHash(new byte[] {(byte) i });
        }
        builder.finishBuilding();
        printChains(builder);
    }

    /**
     * Test to ensure that hash chains with large amount of children
     * are built correctly.
     * @throws Exception in case of unexpected errors.
     */
    @Test
    public void largeTreeBuilding() throws Exception {
        for (int treeSize = 2; treeSize < 353; ++treeSize) {
            LOG.debug("Running largeTreeBuilding test, n = {}", treeSize);

            HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
            for (int i = 0; i < treeSize; ++i) {
                builder.addInputHash(String.valueOf(i).getBytes());
            }
            builder.finishBuilding();

            String[] hashChains = builder.getHashChains("/foo");
            // Verify that all the hash chains are different.
            for (int i = 0; i < hashChains.length - 1; ++i) {
                for (int j = i + 1; j < hashChains.length; ++j) {
                    assertNotEquals("i = " + i + ", j = " + j + ", size = "
                                    + treeSize,
                            hashChains[i], hashChains[j]);
                }
            }
        }
    }

    /**
     * Tests with concrete hash values from the specification.
     * @throws Exception in case of unexpected errors
     */
    @Test
    public void hashValues() throws Exception {
        LOG.info("hashValues()");

        HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
        add(builder, "one");
        add(builder, "two");
        add(builder, "three");
        add(builder, "four");

        builder.finishBuilding();
        byte[] topHash = builder.getTreeTop();
        LOG.info("Tophash HEX: {}", encodeHex(topHash));
        LOG.info("Tophash BASE64: {}", encodeBase64(topHash));

        assertEquals("D7oIIfhfp4ToT729xyx991PvstI5XvpW+d7oeWvXw8E=",
                encodeBase64(topHash));

        LOG.debug("Hash chain result:\n{}", builder.getHashChainResult("foo"));

        printChains(builder);
    }

    private static void printChains(HashChainBuilder builder) throws Exception {
        String[] chains = builder.getHashChains("foo.xml");
        LOG.debug("Hash chains:");
        for (String chain: chains) {
            LOG.debug(chain);
        }
    }

    private static void add(HashChainBuilder builder, String data)
            throws Exception {
        builder.addInputHash(
                calculateDigest(SHA256_ID,
                        data.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Test that ensures a hash chain with multiple attachments is correct.
     * @throws Exception in case of unexpected errors
     */
    @SuppressWarnings("squid:S2699")
    @Test
    public void attachments() throws Exception {
        LOG.info("attachments()");

        HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
        builder.addInputHash(new byte[][] {new byte[] {(byte) 0 }});
        builder.addInputHash(new byte[][] {
                new byte[] {(byte) 11 },
                new byte[] {(byte) 12 },
                new byte[] {(byte) 13 },
                new byte[] {(byte) 14 },
        });
        builder.addInputHash(new byte[] {(byte) 3});
        builder.addInputHash(new byte[][] {
                new byte[] {(byte) 41 },
                new byte[] {(byte) 42 }
        });

        builder.finishBuilding();

        LOG.debug("Hash chain result:\n{}", builder.getHashChainResult("foo"));
        printChains(builder);
    }

    /**
     * Test that ensures a hash chain without input is correct.
     * @throws Exception in case of unexpected errors
     */
    @Test
    public void noInputs() throws Exception {
        LOG.info("noInputs()");

        HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
        builder.finishBuilding();

        assertNull(builder.getHashChainResult("foo"));
        assertNull(builder.getHashChains("bar"));
    }

    /**
     * Test that ensures a hash chain with a single attachment is correct.
     * @throws Exception in case of unexpected errors
     */
    @Test
    public void singleInputAttachment() throws Exception {
        LOG.info("singleInputAttachment()");

        HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
        builder.addInputHash(new byte[][] {
                new byte[] {(byte) 11 },
                new byte[] {(byte) 12 },
                new byte[] {(byte) 13 },
                new byte[] {(byte) 14 },
        });
        builder.finishBuilding();

        assertEquals("v3CBVgjKmzZT5hnJ6vj6waqBazZe334tdEoNiL2oM5E=",
                encodeBase64(builder.getTreeTop()));

        LOG.debug("Hash chain result:\n{}", builder.getHashChainResult("foo"));
        printChains(builder);
    }
}
