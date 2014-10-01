package ee.cyber.sdsb.logreader;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.asic.AsicContainer;
import ee.cyber.sdsb.common.securelog.MessageRecord;
import ee.cyber.sdsb.common.securelog.SecureLogProperties;
import ee.cyber.sdsb.common.securelog.TimestampRecord;
import ee.cyber.sdsb.common.securelog.archive.LogArchiveWriter;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.asic.AsicContainerEntries.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;
import static org.junit.Assert.*;

public class LogReaderTest {

    private static final String MSG_START_DATE = "02-04-2014 12:00:00.000";
    private static final String TS_START_DATE = "02-04-2014 12:34:56.200";

    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");

    // Test data is *generated* in this folder
    private static final Path DIR = Paths.get("build/slog");

    private static final int NUM_MESSAGES_PER_TS = 200;
    private static final int NUM_TS = 5;

    private static final int MSG_WITH_HASHCHAIN = 101;
    private static final int MSG_WITHOUT_SIGNATURE = 102;
    private static final int TS_WITHOUT_DER = 205;
    private static final int TS_WITHOUT_HCR = 206;
    private static final int TS_SINGLE = 207;

    private static String message;
    private static String signature;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Tests ASiC container extraction from log file.
     */
    @Test
    public void extractSignature() throws Exception {
        String queryId = "qid100";
        Date begin = getDate(MSG_START_DATE).plusMinutes(99).toDate();
        Date end = getDate(MSG_START_DATE).plusHours(2).toDate();

        AsicContainer asic = read(queryId, begin, end);
        assertTrue(asic.hasEntry(ENTRY_MESSAGE));
        assertTrue(asic.hasEntry(ENTRY_MIMETYPE));
        assertTrue(asic.hasEntry(ENTRY_MANIFEST));
        assertTrue(asic.hasEntry(ENTRY_SIGNATURE));

        assertNotNull(asic.getSignature().getSignatureXml());
        assertNull(asic.getSignature().getHashChainResult());
        assertNull(asic.getSignature().getHashChain());

        // Different time-frame
        begin = getDate(MSG_START_DATE).minusMonths(1).toDate();
        end = getDate(MSG_START_DATE).plusMonths(1).toDate();
        read(queryId, begin, end);

        queryId = "qid" + TS_SINGLE;

        asic = read(queryId, begin, end);
        assertTrue(asic.hasEntry(ENTRY_MESSAGE));
        assertTrue(asic.hasEntry(ENTRY_MIMETYPE));
        assertTrue(asic.hasEntry(ENTRY_MANIFEST));
        assertTrue(asic.hasEntry(ENTRY_SIGNATURE));
        assertFalse(asic.hasEntry(ENTRY_TS_HASH_CHAIN_RESULT));
        assertFalse(asic.hasEntry(ENTRY_TS_HASH_CHAIN));
        assertNull(asic.getTimestamp());
    }

    /**
     * Tests ASiC container (signature with hash chain) extraction from log file.
     */
    @Test
    public void extractSignatureHashChain() throws Exception {
        String queryId = "qid" + MSG_WITH_HASHCHAIN;
        Date begin = getDate(MSG_START_DATE).plusMinutes(99).toDate();
        Date end = getDate(MSG_START_DATE).plusHours(2).toDate();

        AsicContainer asic = read(queryId, begin, end);

        assertTrue(asic.hasEntry(ENTRY_MESSAGE));
        assertTrue(asic.hasEntry(ENTRY_MIMETYPE));
        assertTrue(asic.hasEntry(ENTRY_MANIFEST));
        assertTrue(asic.hasEntry(ENTRY_SIGNATURE));
        assertTrue(asic.hasEntry(ENTRY_SIG_HASH_CHAIN_RESULT));
        assertTrue(asic.hasEntry(ENTRY_SIG_HASH_CHAIN));

        assertNotNull(asic.getSignature().getSignatureXml());
        assertNotNull(asic.getSignature().getHashChainResult());
        assertNotNull(asic.getSignature().getHashChain());
    }

    /**
     * Message record not found.
     */
    @Test
    public void messageRecordNotFound() throws Exception {
        thrown.expectError(X_SLOG_RECORD_NOT_FOUND);

        String queryId = "foobarbaz";
        Date begin = getDate(MSG_START_DATE).plusMinutes(99).toDate();
        Date end = getDate(MSG_START_DATE).plusHours(2).toDate();

        read(queryId, begin, end);
    }

    /**
     * Message record is missing signature.
     */
    @Test
    public void messageRecordMissingSignature() throws Exception {
        thrown.expectError(X_SLOG_MALFORMED_RECORD);

        String queryId = "qid" + MSG_WITHOUT_SIGNATURE;
        Date begin = getDate(MSG_START_DATE).plusMinutes(99).toDate();
        Date end = getDate(MSG_START_DATE).plusHours(2).toDate();

        read(queryId, begin, end);
    }

    /**
     * Timestamp record is missing timestamp DER.
     */
    @Test
    public void timestampRecordMissingDer() throws Exception {
        thrown.expectError(X_SLOG_MALFORMED_RECORD);

        String queryId = "qid" + TS_WITHOUT_DER;
        read(queryId, null, null);
    }

    /**
     * Timestamp record is missing hash chain result.
     */
    @Test
    public void timestampRecordMissingHashChainResult() throws Exception {
        thrown.expectError(X_ASIC_HASH_CHAIN_RESULT_NOT_FOUND);

        String queryId = "qid" + TS_WITHOUT_HCR;
        read(queryId, null, null);
    }

    /**
     * Begin date after end date.
     */
    @Test
    public void beginDateAfterEndDate() throws Exception {
        thrown.expectError(X_INTERNAL_ERROR);

        String queryId = "qid" + TS_WITHOUT_HCR;
        Date begin = getDate(MSG_START_DATE).plusMinutes(61).toDate();
        Date end = getDate(MSG_START_DATE).plusHours(1).toDate();
        read(queryId, begin, end);
    }

    // ------------------------------------------------------------------------

    @BeforeClass
    public static void prepareTestData() throws Exception {
        System.setProperty(SecureLogProperties.ARCHIVE_MAX_FILESIZE, "500000");

        prepareOutputDirectory(DIR);

        DateTime tsTime = getDate(TS_START_DATE);
        DateTime msgTime = getDate(MSG_START_DATE);

        long m = 0;
        try (LogArchiveWriter writer = new LogArchiveWriter(DIR)) {
            for (int i = 0; i < NUM_TS; i++) {
                tsTime = tsTime.plusDays(1);

                for (int j = 0; j < NUM_MESSAGES_PER_TS; j++) {
                    TimestampRecord ts = nextTimestampRecord(i, tsTime);

                    MessageRecord msg = nextMessageRecord(++m, msgTime);
                    msg.setTimestampRecord(ts);

                    if (m == MSG_WITH_HASHCHAIN) {
                        msg.setHashChain(encodeBase64("hc"));
                        msg.setHashChainResult(encodeBase64("hcr"));
                    } else if (m == MSG_WITHOUT_SIGNATURE) {
                        msg.setSignature(null);
                    } else if (m == TS_WITHOUT_DER) {
                        ts.setTimestamp(null);
                    } else if (m == TS_WITHOUT_HCR) {
                        ts.setHashChainResult(null);
                    } else if (m == TS_SINGLE) {
                        ts.setHashChainResult(null);
                        msg.setTimestampHashChain(null);
                    }

                    msgTime = msgTime.plusMinutes(1);

                    writer.write(msg);
                }
            }
        }
    }

    private static AsicContainer read(String queryId, Date begin, Date end)
            throws Exception {
        AsicContainer asic = new LogReader(DIR).read(queryId, begin, end);
        assertNotNull(asic);
        return checkAsic(asic);
    }

    private static void prepareOutputDirectory(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
            return;
        }

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile()) {
                    Files.delete(file);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static MessageRecord nextMessageRecord(long recordNo,
            DateTime time) throws Exception {
        String qid = "qid" + recordNo;
        String message = encodeBase64(createMessage(qid));
        String signature = encodeBase64(createSignature());

        MessageRecord record = new MessageRecord(
                MessageRecord.hashQueryId(qid), message, signature);
        record.setNumber(recordNo);
        record.setTime(time.getMillis());
        record.setTimestampHashChain(encodeBase64("foobar"));

        return record;
    }

    private static TimestampRecord nextTimestampRecord(long recordNo,
            DateTime time) {
        TimestampRecord record = new TimestampRecord();
        record.setNumber(recordNo);
        record.setTimestamp(encodeBase64(new byte[] {0x01, 0x02, 0x03}));
        record.setHashChainResult(encodeBase64("foobar"));
        record.setTime(time.getMillis());

        return record;
    }

    private static DateTime getDate(String dateStr) throws Exception {
        return new DateTime(DATE_FORMAT.parse(dateStr));
    }

    private static AsicContainer checkAsic(AsicContainer asic)
            throws Exception {
        return AsicContainer.read(new ByteArrayInputStream(asic.getBytes()));
    }

   private static String createMessage(String queryId) throws Exception {
        if (message == null) {
            try (InputStream in = new FileInputStream(
                    "../proxy/src/test/queries/simple.query")) {
                message = IOUtils.toString(in);
            }
        }

        return message.replaceAll("<sdsb:id>1234567890</sdsb:id>",
                "<sdsb:id>" + queryId + "</sdsb:id>");
    }

    private static String createSignature() throws Exception {
        if (signature == null) {
            try (InputStream in = new FileInputStream(
                    "../proxy/src/test/queries/signature.xml")) {
                signature = IOUtils.toString(in);
            }
        }

        return signature;
    }

}
