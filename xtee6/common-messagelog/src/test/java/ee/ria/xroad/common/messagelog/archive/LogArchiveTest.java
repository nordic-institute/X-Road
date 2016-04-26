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
package ee.ria.xroad.common.messagelog.archive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;

import static org.junit.Assert.assertTrue;

/**
 * Exercises entire logic of archiving log entries. Actually depends on
 * LogArchiveCacheTest.
 */
public class LogArchiveTest {

    private static final int NUM_TIMESTAMPS = 3;
    private static final int NUM_RECORDS_PER_TIMESTAMP = 25;

    private static boolean rotated;

    private ByteArrayOutputStream archiveTestOut;

    private long recordNo;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Preparations for testing log archive.
     *
     * @throws Exception - when cannot prepare for testing log archive.
     */
    @Before
    public void beforeTest() throws Exception {
        archiveTestOut = new ByteArrayOutputStream();
        recordNo = 0;

        rotated = false;

        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/tmp/");
    }

    // ------------------------------------------------------------------------

    /**
     * Writes many records and rotates to new file.
     *
     * @throws Exception - when cannot either write or rotate
     */
    @Test
    public void writeAndRotate() throws Exception {
        System.setProperty(MessageLogProperties.ARCHIVE_MAX_FILESIZE, "50");

        writeRecordsToLog();
        assertTrue(rotated);
    }

    // ------------------------------------------------------------------------

    private void writeRecordsToLog() throws Exception {
        try (LogArchiveWriter writer = getWriter()) {
            for (int i = 0; i < NUM_TIMESTAMPS; i++) {
                TimestampRecord ts = nextTimestampRecord();
                for (int j = 0; j < NUM_RECORDS_PER_TIMESTAMP; j++) {
                    MessageRecord messageRecord = nextMessageRecord();
                    messageRecord.setTimestampRecord(ts);
                    messageRecord.setTimestampHashChain("foo");

                    writer.write(messageRecord);
                }
            }
        }
    }

    private LogArchiveWriter getWriter() {
        return new LogArchiveWriter(
                Paths.get("build/slog"), dummyLogArchiveBase()) {
            @Override
            protected WritableByteChannel createArchiveOutput()
                    throws Exception {
                return Channels.newChannel(archiveTestOut);
            }
            @Override
            protected void rotate() throws Exception {
                rotated = true;
            }
        };
    }

    private LogArchiveBase dummyLogArchiveBase() {
        return new LogArchiveBase() {
            @Override
            public void markArchiveCreated(DigestEntry lastArchive) throws Exception {
                // Do nothing.
            }

            @Override
            public void markRecordArchived(LogRecord logRecord)
                    throws Exception {
                // Do nothing.
            }

            @Override
            public DigestEntry loadLastArchive() {
                return DigestEntry.empty();
            }
        };
    }

    private MessageRecord nextMessageRecord() {
        recordNo++;

        MessageRecord record = new MessageRecord("qid" + recordNo,
                "msg" + recordNo, "sig" + recordNo, false,
                ClientId.create("memberClass", "memberCode", "subsystemCode"));
        record.setId(recordNo);
        record.setTime((long) (Math.random() * 100000L));

        return record;
    }

    private TimestampRecord nextTimestampRecord() {
        recordNo++;

        TimestampRecord record = new TimestampRecord();
        record.setId(recordNo);
        record.setTimestamp("ts");
        record.setHashChainResult("foo");
        record.setTime((long) (Math.random() * 100000L));

        return record;
    }

    private static ByteArrayInputStream is(ByteArrayOutputStream os) {
        return new ByteArrayInputStream(os.toByteArray());
    }
}
