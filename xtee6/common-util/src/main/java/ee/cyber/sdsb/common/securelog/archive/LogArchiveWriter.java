package ee.cyber.sdsb.common.securelog.archive;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.securelog.MessageRecord;
import ee.cyber.sdsb.common.securelog.SecureLogProperties;

import static ee.cyber.sdsb.common.DefaultFilepaths.createTempFile;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;

/**
 * Class for writing log records to text file (archive).
 */
@Slf4j
@RequiredArgsConstructor
public class LogArchiveWriter implements Closeable {

    public static final String INDEX_EXTENSION = ".idx";

    private static final Random LOG_FILE_RANDOM = new Random();

    private final Path outputPath;

    protected final Charset charset =
            Charset.forName(StandardCharsets.UTF_8.name());

    protected WritableByteChannel archiveOut;
    protected WritableByteChannel indexOut;

    private Path archiveTmp;
    private Path indexTmp;

    private long currentOffset = 0;
    private long startTime;
    private long endTime;

    public void write(MessageRecord logRecord) throws Exception {
        if (logRecord == null) {
            throw new IllegalArgumentException("log record must not be null");
        }

        if (archiveOut == null || indexOut == null) {
            initOutput(logRecord);
        }

        log.trace("write({})", logRecord.getNumber());

        String json = writeArchive(logRecord);
        writeIndex(logRecord);

        currentOffset += json.length();
        endTime = logRecord.getTime();

        if (needsToRotate()) {
            rotate();
        }
    }

    @Override
    public void close() throws IOException {
        closeOutputs();

        if (archiveTmp != null || indexTmp != null) {
            writeToFiles();
            archiveTmp = null;
            indexTmp = null;
        }
    }

    protected boolean needsToRotate() {
        return currentOffset >= SecureLogProperties.getArchiveMaxFilesize();
    }

    protected WritableByteChannel createArchiveOutput() throws Exception {
        archiveTmp = createTempFile(outputPath, "slogtmp", null);
        return createOutputToTempFile(archiveTmp);
    }

    protected WritableByteChannel createIndexOutput() throws Exception {
        indexTmp = createTempFile(outputPath, "slogtmpidx", null);
        return createOutputToTempFile(indexTmp);
    }

    protected String getArchiveFilename(long startTime, long endTime,
            int random) {
        return String.format("slog-%d-%d-%d", startTime, endTime, random);
    }

    protected String getIndexFilename(long startTime, long endTime,
            int random) {
        return getArchiveFilename(startTime, endTime, random) + INDEX_EXTENSION;
    }

    protected void rotate() throws Exception {
        log.trace("rotate(currentOffset = {})", currentOffset);

        currentOffset = 0;

        closeOutputs();
        archiveOut = null;
        indexOut = null;

        writeToFiles();
        archiveTmp = null;
        indexTmp = null;
    }

    private void closeOutputs() throws IOException {
        try {
            if (archiveOut != null) {
                archiveOut.close();
            }
        } finally {
            if (indexOut != null) {
                indexOut.close();
            }
        }
    }

    private void initOutput(MessageRecord logRecord) throws Exception {
        log.trace("initOutput()");

        try {
            closeOutputs();
        } catch (Exception ignored) {
        }

        archiveOut = createArchiveOutput();
        indexOut = createIndexOutput();

        startTime = logRecord.getTime();
    }

    private void writeToFiles() throws IOException {
        int random = Math.abs(LOG_FILE_RANDOM.nextInt());

        Path archiveFile = Paths.get(outputPath.toString(),
                getArchiveFilename(startTime, endTime, random));
        Path indexFile = Paths.get(outputPath.toString(),
                getIndexFilename(startTime, endTime, random));

        Files.move(archiveTmp, archiveFile, REPLACE_EXISTING, ATOMIC_MOVE);
        Files.move(indexTmp, indexFile, REPLACE_EXISTING, ATOMIC_MOVE);

        log.info("Created archive file {}", archiveFile);
    }

    private String writeArchive(MessageRecord logRecord) throws IOException {
        String json = JsonUtils.getObjectMapper().writeValueAsString(logRecord);
        archiveOut.write(charset.encode(json));
        return json;
    }

    private void writeIndex(MessageRecord logRecord) throws IOException {
        String index = LogArchiveIndex.createIndex(logRecord, currentOffset);
        indexOut.write(charset.encode(index));
    }

    private static WritableByteChannel createOutputToTempFile(Path tmp)
            throws Exception {
        return Files.newByteChannel(tmp, CREATE, WRITE, TRUNCATE_EXISTING);
    }
}
