package ee.cyber.sdsb.proxy.securelog;

import java.io.BufferedReader;
import java.io.FileReader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.ExpectedCodedException;

import static org.junit.Assert.fail;

public class LogRecordTest {

    private static final String LOG_FILE = "src/test/slog/test.slog";

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void parseSignatureRecord() throws Exception {
        SignatureRecord.parseTodoRecord(getLineFromLog(1));
    }

    @Test
    public void parseBrokenSignatureRecord() throws Exception {
        thrown.expectError(ErrorCodes.X_SLOG_MALFORMED_RECORD);

        SignatureRecord.parseTodoRecord(getLineFromLog(3));
    }

    @Test
    public void parseSignatureRecordInvalidNr() throws Exception {
        thrown.expectError(ErrorCodes.X_SLOG_MALFORMED_RECORD);

        SignatureRecord.parseTodoRecord(getLineFromLog(5));
    }

    @Test
    public void parseTimestampRecord() throws Exception {
        TimestampRecord.parseNumbersList(getLineFromLog(15));
    }

    @Test
    public void parseBrokenTimestampRecord() throws Exception {
        thrown.expectError(ErrorCodes.X_SLOG_MALFORMED_RECORD);

        TimestampRecord.parseNumbersList(getLineFromLog(14));
    }

    @Test
    public void parseFaultyLine() throws Exception {
        thrown.expectError(ErrorCodes.X_SLOG_MALFORMED_RECORD);

        TimestampRecord.parseNumbersList(getLineFromLog(16));
    }


    @Test
    public void logFileParseBrokenLog() throws Exception {
        thrown.expectError(ErrorCodes.X_SLOG_MALFORMED_RECORD);

        new LogFile(LOG_FILE);
    }

    private static String getLineFromLog(int lineNum) throws Exception {
        LineIterator it = IOUtils.lineIterator(new BufferedReader(
                new FileReader(LOG_FILE)));
        for (int lineNumber = 0; it.hasNext(); lineNumber++) {
            String line = it.next();
            if (lineNumber == lineNum) {
                return line;
            }
        }

        fail("Could not find line " + lineNum + " from " + LOG_FILE);
        return null;
    }
}
