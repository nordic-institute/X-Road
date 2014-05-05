package ee.cyber.sdsb.logreader;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ee.cyber.sdsb.logreader.RecordType.*;
import static org.junit.Assert.*;

public class TestLogFiles {
    private static final Logger LOG = LoggerFactory.getLogger(
            TestLogFiles.class);

    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Test
    public void testGetFileForDate() throws Exception {
        Files files = new Files("src/test/resources/slog_a");
        files.readDirectory();

        checkFileDate(files, "2013-10-01 00:00:00", "slog");
        checkFileDate(files, "2012-11-01 00:00:00", "slog");
        checkFileDate(files, "2012-10-01 22:22:22",
                "slog.20121001222222.222.4343432");
        checkFileDate(files, "2012-10-01 11:11:11",
                "slog.20121001222222.222.4343432");
        checkFileDate(files, "2012-10-01 00:00:00",
                "slog.20121001000000.000.4343432");
        checkFileDate(files, "2012-09-01 00:00:00",
                "slog.20121001000000.000.4343432");
        checkFileDate(files, "2000-10-01 00:00:00",
                "slog.20110101000000.000.4343432");
    }

    private void checkFileDate(Files files, String date, String fileName)
            throws Exception {
        Date searchDate = DATE_FORMAT.parse(date);
        LogFile file = files.getFileForDate(searchDate.getTime() / 1000);
        assertEquals(fileName, file.getName());
    }

    @Test
    public void testSeekToTime() throws Exception {
        LogFile logFile = new LogFile(new File(
                "src/test/resources/slog_a/slog.20121031235959.999.434342"));

        // Smaller than file
        checkSeekToTime(logFile, "2012-10-31 10:00:00",
                new LogPosition(logFile, 0));
        // Larger than file
        checkSeekToTime(logFile, "2012-10-31 22:00:00", null);
        // In file
        checkSeekToTime(logFile, "2012-10-31 11:12:31",
                new LogPosition(logFile, 454227));
        // Between two records
        checkSeekToTime(logFile, "2012-10-31 11:12:34",
                new LogPosition(logFile, 501055));
    }

    private void checkSeekToTime(LogFile logFile, String dateString,
            LogPosition expectedPos) throws Exception {
        long date = getDate(dateString);
        assertEquals(expectedPos, logFile.seekToTime(date));
    }

    private long getDate(String dateString) throws ParseException {
        return DATE_FORMAT.parse(dateString).getTime() / 1000;
    }

    @Test
    public void testBinSearch() throws Exception {
        Files files = new Files("src/test/resources/slog_a");
        files.readDirectory();

        LogRecord ret;

        // Search from entire file.
        ret = files.binSearch(0, Long.MAX_VALUE, SOAP, 6,
                "xxxb76c86b7782463e2a4a7df3060cce");
        assertEquals(116, ret.getRecordNumber());

        // Search everything for non-existant value.
        ret = files.binSearch(0, Long.MAX_VALUE, SOAP, 6,
                "zzzb76c86b7782463e2a4a7df3060cce");
        assertNull(ret);

        // give more narrow date range
        ret = files.binSearch(0, Long.MAX_VALUE, SOAP, 6,
                "xxxb76c86b7782463e2a4a7df3060cce");
        assertEquals(116, ret.getRecordNumber());

        // date range is before item
        ret = files.binSearch(0, getDate("2012-10-31 11:12:31"),
                SOAP, 6, "xxxb76c86b7782463e2a4a7df3060cce");
        assertNull(ret);

        // date range is after item
        ret = files.binSearch(getDate("2012-10-31 11:12:36"), Long.MAX_VALUE,
                SOAP, 6, "xxxb76c86b7782463e2a4a7df3060cce");
        assertNull(ret);
    }

    @Test
    public void testFindByNumber() throws Exception {
        LOG.info("testFindByNumber()");
        Files files = new Files("src/test/resources/slog_a");
        files.readDirectory();

        LogRecord begin = files.binSearch(0, Long.MAX_VALUE, FIRST_ROW, 2, "0");
        LogRecord middle = files.binSearch(0, Long.MAX_VALUE, SOAP, 2, "116");
        LogRecord end = files.binSearch(0, Long.MAX_VALUE, SOAP, 2, "136");

        // Search forward from beginning.
        checkFindByNumber("e1dca9", files, begin, SOAP, 10);
        // Not a SOAP record
        checkFindByNumber(null, files, begin, SOAP, 11);

        // Search forward from the middle
        checkFindByNumber("1dde90", files, middle, SOAP, 125);
        checkFindByNumber("6cbb9d", files, middle, SIGNATURE, 124);
        // Search backwards from the middle
        checkFindByNumber("21d08d", files, middle, SOAP, 100);
        checkFindByNumber("cfa36e", files, middle, TIMESTAMP, 101);

        // Search forward from the end
        checkFindByNumber(null, files, end, SOAP, 200);
        // Search backwards from the end
        checkFindByNumber("1dde90", files, end, SOAP, 125);

        // Search for the current position.
        checkFindByNumber("7c8fb9", files, middle, SOAP, 116);

        // Search from the next file.
        checkFindByNumber("fffcd2", files, middle, SOAP, 137);
    }

    private void checkFindByNumber(String expectedHash,
            Files files, LogRecord startRecord, RecordType type,
            int number) throws Exception {
        LogRecord found = files.findByNumber(startRecord, type, number);
        if (expectedHash == null) {
            assertNull(found);
        } else {
            assertNotNull(found);
            String hash = found.file.readField(found.pos, 4);
            assertEquals(expectedHash, hash.substring(0, 6));
        }
    }
}

