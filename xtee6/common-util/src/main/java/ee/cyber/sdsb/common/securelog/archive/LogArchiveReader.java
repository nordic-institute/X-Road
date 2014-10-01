package ee.cyber.sdsb.common.securelog.archive;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.securelog.MessageRecord;

import static ee.cyber.sdsb.common.ErrorCodes.X_SLOG_MALFORMED_ARCHIVE;
import static ee.cyber.sdsb.common.ErrorCodes.X_SLOG_MALFORMED_RECORD;
import static ee.cyber.sdsb.common.securelog.archive.JsonUtils.getObjectMapper;

/**
 * Class for reading message records from text file (archive).
 */
@Slf4j
public class LogArchiveReader implements Closeable {

    private final InputStream archiveIn;

    private final JsonParser parser;

    LogArchiveReader(InputStream archiveIn) throws Exception {
        this.archiveIn = archiveIn;
        this.parser = getObjectMapper().getFactory().createParser(archiveIn);
    }

    MessageRecord read() throws Exception {
        log.trace("read()");

        return parser.readValueAs(MessageRecord.class);
    }

    void skipToOffset(long offset) throws Exception {
        log.trace("skipToOffset({})", offset);

        try {
            while (parser.nextToken() != null) {
                if (parser.getCurrentLocation().getByteOffset() > offset) {
                    parser.nextToken();
                    break;
                }
            }
        } catch (JsonParseException e) {
            throw new CodedException(X_SLOG_MALFORMED_ARCHIVE, e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        archiveIn.close();
    }

    /**
     * Reads the message record from the provided input stream. The input
     * stream is assumed to contain relevant time-stamp records.
     *
     * If the message record is found at the given offset, its corresponding
     * time-stamp record is searched for starting from the given offset.
     *
     * @param archiveFile the archive file
     * @param index the index file
     * @param queryId the message query id
     * @return the message or null if the message is not found in the archive
     * @throws Exception if an error occurs
     */
    public static MessageRecord read(Path archiveFile, LogArchiveIndex index,
            String queryId) throws Exception {
        return read(Files.newInputStream(archiveFile), index, queryId);
    }

    /**
     * Reads the message record from the provided input stream. The input
     * stream is assumed to contain relevant time-stamp records.
     *
     * If the message record is found at the given offset, its corresponding
     * time-stamp record is searched for starting from the given offset.
     *
     * @param archiveIn the archive input stream
     * @param index the index file
     * @param queryId the message query id
     * @return the message or null if the message is not found in the archive
     * @throws Exception if an error occurs
     */
    public static MessageRecord read(InputStream archiveIn,
            LogArchiveIndex index, String queryId) throws Exception {
        long offset = index.getOffset(queryId);
        if (offset == -1) {
            return null;
        }

        return read(archiveIn, index, offset);
    }

    /**
     * Reads the message record from the provided input stream. The input
     * stream is assumed to contain relevant time-stamp records.
     *
     * If the message record is found at the given offset, its corresponding
     * time-stamp record is searched for starting from the given offset.
     *
     * @param archiveIn the archive input stream
     * @param index the index file
     * @param the message offset in the archive file
     * @return the message or null if the message is not found in the archive
     * @throws Exception if an error occurs
     */
    public static MessageRecord read(InputStream archiveIn,
            LogArchiveIndex index, long offset) throws Exception {
        try (LogArchiveReader reader = new LogArchiveReader(archiveIn)) {
            reader.skipToOffset(offset);

            MessageRecord message = read(reader);
            if (message != null) {
                verifyIntegrity(message);
                return message;
            } else {
                return null;
            }
        }
    }

    static void verifyIntegrity(MessageRecord message) {
        verifyValueExists(message.getNumber(), "Log record number missing");
        verifyValueExists(message.getTime(), "Time missing");
        verifyValueExists(message.getQueryId(), "Query ID missing");
        verifyValueExists(message.getMessage(), "Message missing");
        verifyValueExists(message.getSignature(), "Signature missing");

        verifyValueExists(message.getTimestampRecord(),
                "Timestamp record missing");
        verifyValueExists(message.getTimestampRecord().getTimestamp(),
                "Timestamp missing");
    }

    private static void verifyValueExists(Object value, String message) {
        if (value == null || (value instanceof String
                && StringUtils.isBlank((String) value))) {
            throw new CodedException(X_SLOG_MALFORMED_RECORD, message);
        }
    }

    private static MessageRecord read(LogArchiveReader reader)
            throws Exception {
        try {
            return reader.read();
        } catch (Exception e) {
            throw new CodedException(X_SLOG_MALFORMED_RECORD, e);
        }
    }
}
