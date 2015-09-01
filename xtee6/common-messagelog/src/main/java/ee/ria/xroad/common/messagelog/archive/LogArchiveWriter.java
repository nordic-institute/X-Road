package ee.ria.xroad.common.messagelog.archive;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;

import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;

import static ee.ria.xroad.common.DefaultFilepaths.createTempFile;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchivePath;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;
import static org.apache.commons.io.FileUtils.deleteQuietly;

/**
 * Class for writing log records to zip file containing ASiC containers
 *  (archive).
 */
@Slf4j
public class LogArchiveWriter implements Closeable {

    public static final int MAX_RANDOM_GEN_ATTEMPTS = 1000;

    private static final int RANDOM_LENGTH = 10;

    private final Path outputPath;
    private final LogArchiveBase archiveBase;

    private final LinkingInfoBuilder linkingInfoBuilder;
    private final LogArchiveCache logArchiveCache;

    protected final Charset charset =
            Charset.forName(StandardCharsets.UTF_8.name());

    protected WritableByteChannel archiveOut;

    private Path archiveTmp;
    private Path lastHashStepTmp;

    /**
     * Creates new LogArchiveWriter
     * @param outputPath - directory where the log archive is created.
     * @param archiveBase - interface to archive database.
     */
    public LogArchiveWriter(Path outputPath, LogArchiveBase archiveBase) {
        this.outputPath = outputPath;
        this.archiveBase = archiveBase;

        this.linkingInfoBuilder = new LinkingInfoBuilder(
                MessageLogProperties.getHashAlg(), archiveBase);
        this.logArchiveCache = new LogArchiveCache(
                LogArchiveWriter::generateRandom, linkingInfoBuilder);

    }

    /**
     * Write a message log record.
     * @param logRecord the log record
     * @throws Exception in case of any errors
     */
    public void write(LogRecord logRecord) throws Exception {
        if (logRecord == null) {
            throw new IllegalArgumentException("log record must not be null");
        }

        if (archiveOut == null) {
            initOutput();
        }

        log.trace("write({})", logRecord.getId());

        if (logRecord instanceof MessageRecord) {
            logArchiveCache.add((MessageRecord) logRecord);
        }

        archiveBase.markRecordArchived(logRecord);

        if (logArchiveCache.isRotating()) {
            rotate();
        }
    }

    @Override
    public void close() throws IOException {
        log.trace("Closing log archive writer ...");

        try {
            if (archiveAsicContainers()) {
                closeOutputs();

                saveArchive();

                logArchiveCache.close();
            }
        } finally {
            clearTempArchive();
        }
    }

    private void clearTempArchive() {
        if (archiveTmp == null) {
            return;
        }

        // Without it, temp file remains on the disk even after closing.
        deleteQuietly(archiveTmp.toFile());
        deleteQuietly(lastHashStepTmp.toFile());

        archiveTmp = null;
        lastHashStepTmp = null;
    }

    protected WritableByteChannel createArchiveOutput() throws Exception {
        archiveTmp = createTempFile(outputPath, "mlogtmp", null);
        lastHashStepTmp = createTempFile(outputPath, "lasthashsteptmp", null);

        return createOutputToTempFile(archiveTmp);
    }

    protected String getArchiveFilename(String random) {
        return String.format("mlog-%s-%s-%s.zip",
                formatFilenameDate(logArchiveCache.getStartTime()),
                formatFilenameDate(logArchiveCache.getEndTime()),
                random);
    }

    private static String formatFilenameDate(Date date) {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(date);
    }

    protected void rotate() throws Exception {
        log.trace("rotate()");
        archiveAsicContainers();

        closeOutputs();
        archiveOut = null;

        saveArchive();

        archiveTmp = null;
        lastHashStepTmp = null;
    }

    private boolean archiveAsicContainers() {
        try (InputStream input = logArchiveCache.getArchiveFile();
                OutputStream output = Channels.newOutputStream(archiveOut)) {
            IOUtils.copy(input, output);
        } catch (IOException e) {
            log.error("Failed to archive ASiC containers due to IO error", e);
            return false;
        }

        return true;
    }

    private void closeOutputs() throws IOException {
        if (archiveOut != null) {
            archiveOut.close();
        }
    }

    private void initOutput() throws Exception {
        log.trace("initOutput()");

        try {
            closeOutputs();
        } catch (Exception e) {
            log.trace("Failed to close output files", e);
        }

        archiveOut = createArchiveOutput();
    }

    private void saveArchive() throws IOException {
        if (archiveTmp == null) {
            return;
        }

        String random = generateRandom();

        String archiveFilename = getArchiveFilename(random);
        Path archiveFile = Paths.get(outputPath.toString(),
                archiveFilename);

        atomicMove(archiveTmp, archiveFile);

        setArchivedInDatabase(archiveFilename);

        linkingInfoBuilder.afterArchiveSaved();

        log.info("Created archive file {}", archiveFile);
    }

    private void setArchivedInDatabase(String archiveFilename)
            throws IOException {
        DigestEntry lastArchive = new DigestEntry(
                linkingInfoBuilder.getCreatedArchiveLastDigest(),
                archiveFilename);

        try {
            archiveBase.markArchiveCreated(lastArchive);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    private static String generateRandom() {
        String random = getRandomAlphanumeric();

        int attempts = 0;
        while (!filenameRandomUnique(random)) {
            if (++attempts > MAX_RANDOM_GEN_ATTEMPTS) {
                throw new RuntimeException(
                        "Could not generate unique random in "
                        + MAX_RANDOM_GEN_ATTEMPTS + " attempts");
            }

            random = getRandomAlphanumeric();
        }

        return random;
    }

    private static boolean filenameRandomUnique(String random) {
        String filenameEnd = String.format("-%s.zip", random);

        String[] fileNamesWithSameRandom = new File(
                getArchivePath()).list((file, name) ->
                        name.startsWith("mlog-") && name.endsWith(filenameEnd));

        return fileNamesWithSameRandom == null
                || fileNamesWithSameRandom.length == 0;
    }

    private static String getRandomAlphanumeric() {
        return RandomStringUtils.randomAlphanumeric(RANDOM_LENGTH);
    }

    private static WritableByteChannel createOutputToTempFile(Path tmp)
            throws Exception {
        return Files.newByteChannel(tmp, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    private static void atomicMove(Path source, Path destination)
            throws IOException {
        Files.move(source, destination, REPLACE_EXISTING, ATOMIC_MOVE);
    }
}
