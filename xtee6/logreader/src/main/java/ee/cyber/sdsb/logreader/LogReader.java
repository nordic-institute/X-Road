package ee.cyber.sdsb.logreader;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.asic.AsicContainer;
import ee.cyber.sdsb.common.asic.TimestampData;
import ee.cyber.sdsb.common.securelog.MessageRecord;
import ee.cyber.sdsb.common.securelog.TimestampRecord;
import ee.cyber.sdsb.common.securelog.archive.LogArchiveIndex;
import ee.cyber.sdsb.common.securelog.archive.LogArchiveReader;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.CryptoUtils;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.securelog.archive.LogArchiveWriter.INDEX_EXTENSION;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class LogReader {

    private static final long PERIOD_UNSPECIFIED = -1;

    private final Path path;

    LogReader() {
        this(Paths.get("log"));
    }

    LogReader(String path) {
        this(Paths.get(path));
    }

    /**
     * Creates ASIC container for query with ID queryId. The logs are
     * searched for dates starting from <code>begin</code> and ending
     * at <code>end</code>.
     *
     * @param queryId the query ID
     * @param begin the begin date
     * @param end the end date
     */
    AsicContainer read(String queryId, Date begin, Date end) throws Exception {
        long beginTime = begin != null ? begin.getTime() : PERIOD_UNSPECIFIED;
        long endTime = end != null ? end.getTime() : PERIOD_UNSPECIFIED;
        return read(queryId, beginTime, endTime);
    }

    /**
     * Creates ASIC container for query with ID queryId. The logs are
     * searched for dates starting from <code>begin</code> and ending
     * at <code>end</code>.
     *
     * @param queryId the query ID
     * @param begin the begin time in milliseconds
     * @param end the end time in milliseconds
     */
    AsicContainer read(String queryId, long begin, long end) throws Exception {
        log.trace("extractSignature({}, {}, {})",
                new Object[] {queryId, begin, end});

        if (begin > end) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Begin date must be before end date");
        }

        MessageRecord messageRecord = findMessageRecord(queryId, begin, end);
        if (messageRecord == null) {
            throw new CodedException(X_SLOG_RECORD_NOT_FOUND,
                    "Cannot find SOAP record with ID " + queryId);
        }

        return createAsic(messageRecord);
    }

    static AsicContainer createAsic(MessageRecord messageRecord)
            throws Exception {
        TimestampRecord timestampRecord = messageRecord.getTimestampRecord();

        String messageXml = decodeBase64(messageRecord.getMessage());

        String signatureXml = decodeBase64(messageRecord.getSignature());
        String signatureHashChainResult =
                decodeBase64(messageRecord.getHashChainResult());
        String signatureHashChain = decodeBase64(messageRecord.getHashChain());

        String timestampDerBase64 = timestampRecord.getTimestamp();
        String timestamphashChainResult =
                decodeBase64(timestampRecord.getHashChainResult());
        String timestamphashChain =
                decodeBase64(messageRecord.getTimestampHashChain());

        return new AsicContainer(messageXml,
                new SignatureData(signatureXml, signatureHashChainResult,
                        signatureHashChain),
                new TimestampData(timestampDerBase64, timestamphashChainResult,
                        timestamphashChain));
    }

    static String decodeBase64(String base64Encoded) {
        if (base64Encoded != null && !base64Encoded.isEmpty() &&
                !base64Encoded.equals("-")) {
            return new String(CryptoUtils.decodeBase64(base64Encoded));
        }

        return null;
    }

    private MessageRecord findMessageRecord(String queryId, long begin,
            long end) throws Exception {
        List<Path> files = listArchiveFiles(path);
        for (Path file : files) {
            if (matchesPeriod(file, begin, end)) {
                MessageRecord messageRecord = findMessageRecord(file, queryId);
                if (messageRecord != null) {
                    return messageRecord;
                }
            } else {
                log.trace("Ignoring {}", file);
            }
        }

        return null;
    }

    private MessageRecord findMessageRecord(Path archiveFile, String queryId) {
        log.trace("findMessageRecord({}, {})", archiveFile.getFileName(),
                queryId);

        Path indexFile = Paths.get(archiveFile + INDEX_EXTENSION);
        if (!Files.exists(indexFile)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Cannot read archive file %s, index file is missing",
                    archiveFile);
        }

        LogArchiveIndex index;
        try (InputStream indexIn = Files.newInputStream(indexFile)) {
            index = new LogArchiveIndex(indexIn);
        } catch (Exception e) {
            throw translateException(e);
        }

        log.debug("Searching for {} in {}", queryId, archiveFile.getFileName());
        try {
            String hashedQueryId = MessageRecord.hashQueryId(queryId);
            return LogArchiveReader.read(archiveFile, index, hashedQueryId);
        } catch (Exception e) {
            log.error("Failed to find message record", e);
            throw translateException(e);
        }
    }

    private static List<Path> listArchiveFiles(Path directory)
            throws Exception {
        if (!Files.isDirectory(directory)) {
            throw new Exception(directory + " must be a directory");
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p) && matchesArchiveFilePattern(p)) {
                    files.add(p);
                }
            }
        }

        return files;
    }

    private static boolean matchesArchiveFilePattern(Path p) {
        return p.getFileName().toString().matches(
                "slog-([0-9]+)-([0-9]+)-([0-9]+)");
    }

    private static boolean matchesPeriod(Path file, long begin, long end) {
        log.trace("matchesPeriod({}, {}, {})",
                new Object[] {file.getFileName(), begin, end});
        if (begin == PERIOD_UNSPECIFIED && end == PERIOD_UNSPECIFIED) {
            return true;
        }

        String[] parts = file.getFileName().toString().split("-");
        if (parts.length != 4) {
            return false;
        }

        try {
            long fileBegin = Long.parseLong(parts[1]);
            long fileEnd = Long.parseLong(parts[2]);
            return !(fileEnd < begin || fileBegin > end);
        } catch (NumberFormatException ignore) {
            return false;
        }
    }
}
