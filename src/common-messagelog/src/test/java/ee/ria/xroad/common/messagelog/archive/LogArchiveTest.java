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
package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Exercises entire logic of archiving log entries. Actually depends on
 * LogArchiveCacheTest.
 */
@RunWith(Parameterized.class)
public class LogArchiveTest {

    private static final int NUM_TIMESTAMPS = 3;
    private static final int NUM_RECORDS_PER_TIMESTAMP = 5;

    private static boolean rotated;
    private long recordNo;

    @Parameterized.Parameter(0)
    public boolean encrypted;

    @Parameterized.Parameter(1)
    public GroupingStrategy groupingStrategy;


    @Parameterized.Parameters(name = "encrypted = {0}, grouping = {1}")
    public static Object[][] params() {
        return new Object[][] {
                {Boolean.FALSE, GroupingStrategy.NONE},
                {Boolean.FALSE, GroupingStrategy.MEMBER},
                {Boolean.FALSE, GroupingStrategy.SUBSYSTEM},
                {Boolean.TRUE, GroupingStrategy.NONE},
                {Boolean.TRUE, GroupingStrategy.MEMBER},
                {Boolean.TRUE, GroupingStrategy.SUBSYSTEM}
        };
    }

    /**
     * Preparations for testing log archive.
     * @throws Exception - when cannot prepare for testing log archive.
     */
    @Before
    public void beforeTest() throws Exception {
        GlobalConf.reload(new EmptyGlobalConf() {
            @Override
            public String getInstanceIdentifier() {
                return "INSTANCE";
            }
        });

        if (encrypted) {
            Assume.assumeTrue(Files.isExecutable(Paths.get("/usr/bin/gpg")));
        }
        recordNo = 0;
        rotated = false;
        Files.createDirectory(Paths.get("build/slog"));
        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/tmp");
        System.setProperty(MessageLogProperties.ARCHIVE_GPG_HOME_DIRECTORY, "build/gpg");
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG, "build/gpg/keys.ini");
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_ENABLED, String.valueOf(encrypted));
        System.setProperty(MessageLogProperties.ARCHIVE_GROUPING, groupingStrategy.name());
    }

    @After
    public void afterTest() {
        System.clearProperty(MessageLogProperties.ARCHIVE_GPG_HOME_DIRECTORY);
        System.clearProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG);
        System.clearProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_ENABLED);
        System.clearProperty(MessageLogProperties.ARCHIVE_MAX_FILESIZE);
        System.clearProperty(MessageLogProperties.ARCHIVE_GROUPING);
        FileUtils.deleteQuietly(Paths.get("build/slog").toFile());
    }

    // ------------------------------------------------------------------------

    /**
     * Writes many records and rotates to new file.
     * @throws Exception - when cannot either write or rotate
     */
    @Test
    public void writeAndRotate() throws Exception {
        System.setProperty(MessageLogProperties.ARCHIVE_MAX_FILESIZE, "50");
        writeRecordsToLog(false);
        assertTrue(rotated);
    }

    /**
     * Writes records, simulates a situation where archving is finished just after rotate.
     * (XRDDEV-85)
     */
    @Test
    public void testFinishAfterRotate() throws Exception {
        System.setProperty(MessageLogProperties.ARCHIVE_MAX_FILESIZE, "3000");
        writeRecordsToLog(true);
        assertTrue(rotated);
    }

    // ------------------------------------------------------------------------

    private void writeRecordsToLog(boolean finishAfterRotate) throws Exception {
        try (LogArchiveWriter writer = getWriter()) {
            outer:
            for (int i = 0; i < NUM_TIMESTAMPS; i++) {
                TimestampRecord ts = nextTimestampRecord();
                for (int j = 0; j < NUM_RECORDS_PER_TIMESTAMP; j++) {
                    MessageRecord messageRecord = nextMessageRecord();
                    messageRecord.setTimestampRecord(ts);
                    messageRecord.setTimestampHashChain("foo");

                    if (writer.write(messageRecord) && finishAfterRotate) {
                        break outer;
                    }
                }
            }
        }
    }

    private LogArchiveWriter getWriter() throws IOException {
        return new LogArchiveWriter(
                Paths.get("build/slog"),
                dummyLogArchiveBase()) {

            @Override
            protected void rotate() throws IOException {
                super.rotate();
                rotated = true;
            }
        };
    }

    private LogArchiveBase dummyLogArchiveBase() {
        return new LogArchiveBase() {
            @Override
            public void markArchiveCreated(String entryName, DigestEntry lastArchive) {
                // Do nothing.
            }

            @Override
            public void markRecordArchived(LogRecord logRecord) {
                // Do nothing.
            }

            @Override
            public DigestEntry loadLastArchive(String entryName) {
                return DigestEntry.empty();
            }
        };
    }

    private MessageRecord nextMessageRecord() {
        recordNo++;

        MessageRecord record = new MessageRecord("qid" + recordNo,
                "msg" + recordNo, "sig" + recordNo, false,
                ClientId.Conf.create(GlobalConf.getInstanceIdentifier(), "memberClass", "memberCode", "subsystemCode"),
                "92060130-3ba8-4e35-89e2-41b90aac074b");
        record.setId(recordNo);
        record.setTime((long)(Math.random() * 100000L));

        return record;
    }

    private TimestampRecord nextTimestampRecord() {
        recordNo++;

        TimestampRecord record = new TimestampRecord();
        record.setId(recordNo);
        record.setTimestamp("ts");
        record.setHashChainResult("foo");
        record.setTime((long)(Math.random() * 100000L));

        return record;
    }
}
