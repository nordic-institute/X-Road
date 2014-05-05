package ee.cyber.sdsb.common.hashchain;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HashChainBuilderTest {
    private static final Logger LOG = LoggerFactory.getLogger(
            HashChainBuilderTest.class);

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
            builder.addInputHash(new byte[] { (byte) i });
        }
        builder.finishBuilding();
        printChains(builder);
    }

    @Test
    /**
     * Tests with concrete hash values from the specification.
     */
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

        LOG.debug("Hash chain result:\n{}", builder.getHashChainResult());

        printChains(builder);
    }

    private static void printChains(HashChainBuilder builder) throws Exception {
        String[] chains = builder.getHashChains();
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

    @Test
    public void attachments() throws Exception {
        LOG.info("attachments()");

        HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
        builder.addInputHash(new byte[][] { new byte[] {(byte) 0 } });
        builder.addInputHash(new byte[][] {
                new byte[] {(byte) 11 },
                new byte[] {(byte) 12 },
                new byte[] {(byte) 13 },
                new byte[] {(byte) 14 },
        });
        builder.addInputHash(new byte[] { (byte) 3 });
        builder.addInputHash(new byte[][] {
                new byte[] {(byte) 41 },
                new byte[] {(byte) 42 }
        });

        builder.finishBuilding();

        LOG.debug("Hash chain result:\n{}", builder.getHashChainResult());
        printChains(builder);
    }

    @Test
    public void noInputs() throws Exception {
        LOG.info("noInputs()");

        HashChainBuilder builder = new HashChainBuilder(SHA256_ID);
        builder.finishBuilding();

        assertNull(builder.getHashChainResult());
        assertNull(builder.getHashChains());
    }

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

        LOG.debug("Hash chain result:\n{}", builder.getHashChainResult());
        printChains(builder);
    }
}
