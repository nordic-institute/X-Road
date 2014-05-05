package ee.cyber.sdsb.logreader;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import ee.cyber.sdsb.common.asic.AsicContainer;

import static org.junit.Assert.*;

public class LogReaderTest {

    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Tests ASiC container extraction from log file.
     */
    @Test
    public void extractSignature() throws Exception {
        LogReader r = new LogReader(new Files("src/test/resources/slog_a"));

        String queryId = "411d6755661409fed365ad8135f8210be07613da";
        Date begin = DATE_FORMAT.parse("2010-10-31 11:12:00");
        Date end = DATE_FORMAT.parse("2013-10-31 11:12:33");

        AsicContainer asic = r.extractSignature(queryId, begin, end);
        assertNotNull(asic);
        asic = checkAsic(asic);

        assertTrue(asic.hasEntry(AsicContainer.ENTRY_MESSAGE));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_MIMETYPE));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_SIGNATURE));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_HASH_CHAIN_RESULT));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_HASH_CHAIN));

        assertNotNull(asic.getSignature().getSignatureXml());
        assertNull(asic.getSignature().getHashChainResult());
        assertNull(asic.getSignature().getHashChain());
    }

    /**
     * Tests ASiC container (signature with hash chain) extraction from log file.
     */
    @Test
    public void extractSignatureHashChain() throws Exception {
        LogReader r = new LogReader(new Files("src/test/resources/slog_b"));

        String queryId = "7113f3a6e47b76a91055d9d9a9ccaa87";
        long begin = 1386679304;
        long end = begin + 5;

        AsicContainer asic = r.extractSignature(queryId, begin, end);
        assertNotNull(asic);
        asic = checkAsic(asic);

        assertTrue(asic.hasEntry(AsicContainer.ENTRY_MESSAGE));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_MIMETYPE));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_SIGNATURE));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_HASH_CHAIN_RESULT));
        assertTrue(asic.hasEntry(AsicContainer.ENTRY_HASH_CHAIN));

        assertNotNull(asic.getSignature().getSignatureXml());
        assertNotNull(asic.getSignature().getHashChainResult());
        assertNotNull(asic.getSignature().getHashChain());
    }

    /**
     * Tests ASiC container creation from base64 input data (message,
     * signature, timestamp).
     */
    @Test
    public void createAsicFromBase64Data() throws Exception {
        List<String> lines = IOUtils.readLines(
                new FileInputStream("src/test/resources/test-data"));
        assertEquals(4, lines.size());

        AsicContainer asic = LogReader.createAsic(
                lines.get(0), lines.get(1), lines.get(2), lines.get(3));
        assertNotNull(asic);
        checkAsic(asic);
    }

    private static AsicContainer checkAsic(AsicContainer asic)
            throws Exception {
        return AsicContainer.read(new ByteArrayInputStream(asic.getBytes()));
    }
}
