package ee.ria.xroad.common.messagelog.archive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.messagelog.MessageRecord;

import static ee.ria.xroad.common.ErrorCodes.X_SLOG_MALFORMED_INDEX;

/**
 * This class represents a log archive index file.
 * The index file is a simple CSV file that contains message and time-stamp
 * record offsets to the archive file.
 *
 * TODO probably not necessary any more, as log is now in the format of zipped
 * ASiC containers.
 */
public class LogArchiveIndex {

    private static final int ARCHIVE_LINE_RECORD_COUNT = 3;

    private Map<String, Long> queryIdToOffset = new HashMap<>();
    private Map<Long, Long> recordNoToOffset = new HashMap<>();

    /**
     * Constructs a new  log archive index from the given input stream.
     * @param in the input stream
     * @throws Exception in case of any errors
     */
    public LogArchiveIndex(InputStream in) throws Exception {
        load(in);
    }

    /**
     * @param queryId the query ID
     * @return the offset of the given query ID in this archive index,
     * or null if the query ID is not present in this archive
     */
    public long getOffset(String queryId) {
        Long offset = queryIdToOffset.get(queryId);
        return offset != null ? offset : -1;
    }

    /**
     * @param recordNo the record number
     * @return the offset of the given record number in this archive index,
     * or null if the record number is not present in this archive
     */
    public long getOffset(long recordNo) {
        Long offset = recordNoToOffset.get(recordNo);
        return offset != null ? offset : -1;
    }

    private void load(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length != ARCHIVE_LINE_RECORD_COUNT) {
                throw new CodedException(X_SLOG_MALFORMED_INDEX, line);
            }

            Long logRecordNo = parseLong(parts[1]);
            Long offset = parseLong(parts[2]);

            queryIdToOffset.put(parts[0], offset);
            recordNoToOffset.put(logRecordNo, offset);
        }
    }

    /**
     * Create an a log archive index for the given message log record.
     * @param logRecord the log record
     * @param offset the offset at which to create the log
     * @return index as a comma-delimited String
     */
    public static String createIndex(MessageRecord logRecord, long offset) {
        String index = logRecord.getQueryId();
        index += "," + logRecord.getId() + "," + offset
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
