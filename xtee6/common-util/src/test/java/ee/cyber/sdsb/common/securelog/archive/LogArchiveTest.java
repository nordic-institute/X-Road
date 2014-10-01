package ee.cyber.sdsb.common.securelog.archive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.securelog.MessageRecord;
import ee.cyber.sdsb.common.securelog.SecureLogProperties;
import ee.cyber.sdsb.common.securelog.TimestampRecord;
import ee.cyber.sdsb.common.util.ExpectedCodedException;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static org.junit.Assert.*;

public class LogArchiveTest {

    private static final int NUM_TIMESTAMPS = 3;
    private static final int NUM_RECORDS_PER_TIMESTAMP = 25;

    private static boolean rotated;

    private ByteArrayOutputStream archiveTestOut;
    private ByteArrayOutputStream indexTestOut;

    private long recordNo;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Before
    public void beforeTest() throws Exception {
        archiveTestOut = new ByteArrayOutputStream();
        indexTestOut = new ByteArrayOutputStream();
        recordNo = 0;

        rotated = false;
    }

    // ------------------------------------------------------------------------

    /**
     * Writes 2 messages and time-stamp. Must successfully read archived
     * messages and time-stamp.
     */
    @Test
    public void writeAndReadTimestampRecords() throws Exception {
        MessageRecord msg1 = nextMessageRecord();
        MessageRecord msg2 = nextMessageRecord();
        TimestampRecord ts = nextTimestampRecord();

        msg1.setTimestampRecord(ts);
        msg2.setTimestampRecord(ts);
        try (LogArchiveWriter writer = getWriter()) {
            writer.write(msg1);
            writer.write(msg2);
        }

        LogArchiveIndex index = new LogArchiveIndex(is(indexTestOut));

        MessageRecord record1 = read(index, "qid1");
        assertEquals(msg1, record1);
        assertEquals(ts, record1.getTimestampRecord());

        MessageRecord record2 = read(index, "qid2");
        assertEquals(msg2, record2);
        assertEquals(ts, record2.getTimestampRecord());
    }

    /**
     * Attempt to read message with query id that is not in index.
     */
    @Test
    public void readMessageNotFound() throws Exception {
        MessageRecord msg1 = nextMessageRecord();
        MessageRecord msg2 = nextMessageRecord();
        TimestampRecord ts = nextTimestampRecord();

        msg1.setTimestampRecord(ts);
        msg2.setTimestampRecord(ts);
        try (LogArchiveWriter writer = getWriter()) {
            writer.write(msg1);
            writer.write(msg2);
        }

        LogArchiveIndex index = new LogArchiveIndex(is(indexTestOut));
        assertNull(read(index, "foo"));
    }

    /**
     * Writes many records and rotates to new file.
     */
    @Test
    public void writeAndRotate() throws Exception {
        System.setProperty(SecureLogProperties.ARCHIVE_MAX_FILESIZE, "50");

        writeRecordsToLog();
        assertTrue(rotated);
    }

    /**
     * Index file corrupted, must not read messages.
     */
    @Test
    public void readIndexFileCorrupted() throws Exception {
        thrown.expectError(X_SLOG_MALFORMED_INDEX);

        writeRecordsToLog();

        byte[] indexBytes = indexTestOut.toByteArray();
        byte[] malformedBytes = repeatBytes((byte) 0x24, 150);
        System.arraycopy(malformedBytes, 0, indexBytes, 195,
                malformedBytes.length);
        new LogArchiveIndex(new ByteArrayInputStream(indexBytes));
    }

    /**
     * Archive file corrupted, must not read messages.
     */
    @Test
    public void readArchiveFileCorrupted() throws Exception {
        thrown.expectError(X_SLOG_MALFORMED_ARCHIVE);

        writeRecordsToLog();
        initArchive(new byte[] {0x01});

        LogArchiveIndex index = new LogArchiveIndex(is(indexTestOut));
        read(index, "qid40");
    }

    /**
     * Message record is corrupted, must not read message.
     */
    @Test
    public void readMessageRecordCorrupted() throws Exception {
        thrown.expectError(X_SLOG_MALFORMED_RECORD);

        writeRecordsToLog();

        byte[] archiveBytes = archiveTestOut.toByteArray();

        LogArchiveIndex index = new LogArchiveIndex(is(indexTestOut));
        long offset = index.getOffset("qid2");
        assertNotEquals(-1, offset);

        byte[] malformedBytes = repeatBytes((byte) 0x24, 2);
        System.arraycopy(malformedBytes, 0, archiveBytes, (int) offset + 20,
                malformedBytes.length);
        System.out.println(new String(archiveBytes));
        initArchive(archiveBytes);

        read(index, "qid2");
    }

    /**
     * Time-stamp record is corrupted, must not read message.
     */
    @Test
    public void readTimestampRecordCorrupted() throws Exception {
        thrown.expectError(X_SLOG_MALFORMED_RECORD);

        MessageRecord msg1 = nextMessageRecord();
        MessageRecord msg2 = nextMessageRecord();
        TimestampRecord ts = nextTimestampRecord();

        msg1.setTimestampRecord(ts);
        msg2.setTimestampRecord(ts);

        try (LogArchiveWriter writer = getWriter()) {
            writer.write(msg1);
            writer.write(msg2);
        }

        LogArchiveIndex index = new LogArchiveIndex(is(indexTestOut));
        byte[] archiveBytes = archiveTestOut.toByteArray();
        byte[] malformedBytes = repeatBytes((byte) 0x24, 5);
        System.arraycopy(malformedBytes, 0, archiveBytes, 130,
                malformedBytes.length);
        initArchive(archiveBytes);

        read(index, "qid1");
    }

    /**
     * Message records are not time-stamped.
     */
    @Test
    public void readNoTimestamp() throws Exception {
        thrown.expectError(X_SLOG_MALFORMED_RECORD);

        MessageRecord msg1 = nextMessageRecord();
        MessageRecord msg2 = nextMessageRecord();

        try (LogArchiveWriter writer = getWriter()) {
            writer.write(msg1);
            writer.write(msg2);
        }

        LogArchiveIndex index = new LogArchiveIndex(is(indexTestOut));
        read(index, "qid1");
    }

    @Test
    public void readMessageRecordMissingValues() throws Exception {
        assertMessageRecordMalformed(new MessageRecord(null, null, null));
        assertMessageRecordMalformed(new MessageRecord("foo", null, null));
        assertMessageRecordMalformed(new MessageRecord("foo", "foo", null));
        assertMessageRecordMalformed(new MessageRecord("foo", "foo", "foo"));
    }

    // ------------------------------------------------------------------------

    private static byte[] repeatBytes(byte b, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = b;
        }

        return bytes;
    }

    private void initArchive(byte[] bytes) throws Exception {
        archiveTestOut = new ByteArrayOutputStream();
        archiveTestOut.write(bytes);
    }

    private void writeRecordsToLog() throws Exception {
        try (LogArchiveWriter writer = getWriter()) {
            for (int i = 0; i < NUM_TIMESTAMPS; i++) {
                TimestampRecord ts = nextTimestampRecord();
                for (int j = 0; j < NUM_RECORDS_PER_TIMESTAMP; j++) {
                    MessageRecord messageRecord = nextMessageRecord();
                    messageRecord.setTimestampRecord(ts);
                    writer.write(messageRecord);
                }
            }
        }
    }

    private LogArchiveWriter getWriter() {
        return new LogArchiveWriter(Paths.get("build/slog")) {
            @Override
            protected WritableByteChannel createArchiveOutput()
                    throws Exception {
                return Channels.newChannel(archiveTestOut);
            }
            @Override
            protected WritableByteChannel createIndexOutput()
                    throws Exception {
                return Channels.newChannel(indexTestOut);
            }
            @Override
            protected void rotate() throws Exception {
                rotated = true;
            }
        };
    }

    private MessageRecord read(LogArchiveIndex index, String queryId)
            throws Exception {
        return LogArchiveReader.read(is(archiveTestOut), index, queryId);
    }

    private MessageRecord nextMessageRecord() {
        recordNo++;

        MessageRecord record = new MessageRecord("qid" + recordNo,
                "msg" + recordNo, "sig" + recordNo);
        record.setNumber(recordNo);
        record.setTime((long) (Math.random() * 100000L));

        return record;
    }

    private TimestampRecord nextTimestampRecord() {
        recordNo++;

        TimestampRecord record = new TimestampRecord();
        record.setNumber(recordNo);
        record.setTimestamp("ts");
        record.setHashChainResult("foo");
        record.setTime((long) (Math.random() * 100000L));

        return record;
    }

    private static void assertMessageRecordMalformed(
            MessageRecord messageRecord) throws Exception {
        try {
            String json = JsonUtils.getObjectMapper().writeValueAsString(
                    messageRecord);
            MessageRecord result = new LogArchiveReader(
                    new ByteArrayInputStream(
                            json.getBytes(StandardCharsets.UTF_8))).read();
            LogArchiveReader.verifyIntegrity(result);
            fail("Expected message record to be malformed");
        } catch (CodedException e) {
            assertEquals(X_SLOG_MALFORMED_RECORD, e.getFaultCode());
        }
    }

    private static ByteArrayInputStream is(ByteArrayOutputStream os) {
        return new ByteArrayInputStream(os.toByteArray());
    }
}
