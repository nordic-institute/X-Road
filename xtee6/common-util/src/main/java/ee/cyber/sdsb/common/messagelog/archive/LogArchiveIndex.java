package ee.cyber.sdsb.common.messagelog.archive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.messagelog.MessageRecord;

import static ee.cyber.sdsb.common.ErrorCodes.X_SLOG_MALFORMED_INDEX;

/**
 * This class represents a log archive index file.
 * The index file is a simple CSV file that contains message and time-stamp
 * record offsets to the archive file.
 */
public class LogArchiveIndex {

    private Map<String, Long> queryIdToOffset = new HashMap<>();
    private Map<Long, Long> recordNoToOffset = new HashMap<>();

    public LogArchiveIndex(InputStream in) throws Exception {
        load(in);
    }

    public long getOffset(String queryId) {
        Long offset = queryIdToOffset.get(queryId);
        return offset != null ? offset : -1;
    }

    public long getOffset(long recordNo) {
        Long offset = recordNoToOffset.get(recordNo);
        return offset != null ? offset : -1;
    }

    private void load(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length != 3) {
                throw new CodedException(X_SLOG_MALFORMED_INDEX, line);
            }

            Long logRecordNo = parseLong(parts[1]);
            Long offset = parseLong(parts[2]);

            queryIdToOffset.put(parts[0], offset);
            recordNoToOffset.put(logRecordNo, offset);
        }
    }

    public static String createIndex(MessageRecord logRecord, long offset) {
        String index = logRecord.getQueryId();
        index += "," + logRecord.getNumber() + "," + offset
                + System.lineSeparator();
        return index;
    }

    private static Long parseLong(String string) {
        try {
            return Long.parseLong(string);
        } catch (Exception e) {
            throw new CodedException(X_SLOG_MALFORMED_INDEX, e.getMessage());
        }
    }
}
