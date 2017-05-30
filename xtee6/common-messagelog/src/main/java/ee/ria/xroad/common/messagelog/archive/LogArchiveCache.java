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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.asic.AsicContainerNameGenerator;
import ee.ria.xroad.common.messagelog.MessageRecord;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ee.ria.xroad.common.ErrorCodes.X_IO_ERROR;
import static ee.ria.xroad.common.SystemProperties.getTempFilesPath;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveMaxFilesize;
import static ee.ria.xroad.common.messagelog.archive.LogArchiveWriter.MAX_RANDOM_GEN_ATTEMPTS;

/**
 * Encapsulates logic of creating log archive from ASiC containers.
 */
@Slf4j
class LogArchiveCache implements Closeable {

    private enum State {
        NEW,
        ADDING,
        ROTATING
    }

    private final Supplier<String> randomGenerator;
    private final LinkingInfoBuilder linkingInfoBuilder;

    private AsicContainerNameGenerator nameGenerator;
    private State state = State.NEW;

    private File archiveContentDir;
    private File tempArchive;

    private List<String> archiveFileNames;
    private Set<Date> creationTimes;
    private long archivesTotalSize;

    LogArchiveCache(Supplier<String> randomGenerator,
            LinkingInfoBuilder linkingInfoBuilder) {
        this.randomGenerator = randomGenerator;
        this.linkingInfoBuilder = linkingInfoBuilder;

        reset();
    }

    void add(MessageRecord messageRecord) throws Exception {
        try {
            validateMessageRecord(messageRecord);
            handleRotation();
            cacheRecord(messageRecord);
            updateState();
        } catch (Exception e) {
            handleCacheError(e);
        }
    }

    InputStream getArchiveFile() throws IOException {
        tempArchive = File.createTempFile(
                "xroad-log-archive-zip", ".tmp", new File(getTempFilesPath()));

        try (FileOutputStream fileOut = new FileOutputStream(tempArchive);
                ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            addAsicContainersToArchive(zipOut);
            addLinkingInfoToArchive(zipOut);
        } catch (Exception e) {
            handleCacheError(e);
        }

        return new FileInputStream(tempArchive);
    }

    @SneakyThrows
    private void handleCacheError(Exception e) {
        deleteArchiveArtifacts();

        throw e;
    }

    private void addAsicContainersToArchive(ZipOutputStream zipOut)
            throws IOException {
        for (String eachName : archiveFileNames) {
            ZipEntry entry = new ZipEntry(eachName);

            zipOut.putNextEntry(entry);

            try (InputStream archiveInput =
                    Files.newInputStream(createTempAsicPath(eachName))) {
                IOUtils.copy(archiveInput, zipOut);
            }

            zipOut.closeEntry();
        }
    }

    private void addLinkingInfoToArchive(ZipOutputStream zipOut)
            throws IOException {
        ZipEntry linkingInfoEntry = new ZipEntry("linkinginfo");

        zipOut.putNextEntry(linkingInfoEntry);
        zipOut.write(linkingInfoBuilder.build());
        zipOut.closeEntry();

        linkingInfoBuilder.afterArchiveCreated();
    }

    boolean isRotating() {
        return state == State.ROTATING;
    }

    Date getStartTime() {
        return (Date) creationTimes.toArray()[0];
    }

    Date getEndTime() {
        return (Date) creationTimes.toArray()[creationTimes.size() - 1];
    }

    @Override
    public void close() throws IOException {
        deleteArchiveArtifacts();
    }

    private void validateMessageRecord(MessageRecord record)
            throws IOException {
        if (record == null) {
            throw new IllegalArgumentException(
                    "Message record to be archived must not be null");
        }
    }

    private void handleRotation() throws IOException {
        if (state != State.ROTATING) {
            return;
        }

        reset();
    }

    private void cacheRecord(MessageRecord messageRecord) throws Exception {
        creationTimes.add(new Date(messageRecord.getTime()));

        addContainerToArchive(messageRecord);
    }

    private void updateState() {
        state = archiveExceedsRotationSize() ? State.ROTATING : State.ADDING;
    }

    private boolean archiveExceedsRotationSize() {
        return archivesTotalSize > getArchiveMaxFilesize();
    }

    private void addContainerToArchive(MessageRecord record) throws Exception {
        byte[] containerBytes = record.toAsicContainer().getBytes();

        String archiveFilename =
                nameGenerator.getArchiveFilename(record.getQueryId(), record.isResponse() ? "response" : "request");

        linkingInfoBuilder.addNextFile(archiveFilename, containerBytes);
        archiveFileNames.add(archiveFilename);
        archivesTotalSize += containerBytes.length;

        try (OutputStream os =
                Files.newOutputStream(createTempAsicPath(archiveFilename))) {
            os.write(containerBytes);
        }
    }

    private Path createTempAsicPath(String archiveFilename) {
        return Paths.get(archiveContentDir.getAbsolutePath(), archiveFilename);
    }

    private void reset() {
        try {
            resetArchive();
            resetCacheState();
        } catch (IOException e) {
            log.error("Resetting log archive cache failed, cause:", e);
            throw new CodedException(X_IO_ERROR,
                    "Failed to reset log archive cache");
        }
    }

    private void resetArchive() throws IOException {
        deleteArchiveArtifacts();

        archiveContentDir = Files.createTempDirectory(
                    Paths.get(getTempFilesPath()),
                    "xroad-log-archive"
                ).toFile();
    }

    private void deleteArchiveArtifacts() {
        FileUtils.deleteQuietly(archiveContentDir);
        FileUtils.deleteQuietly(tempArchive);
    }

    private void resetCacheState() {
        archiveFileNames = new ArrayList<>();
        creationTimes = new TreeSet<>();
        archivesTotalSize = 0;

        nameGenerator = new AsicContainerNameGenerator(randomGenerator,
                        MAX_RANDOM_GEN_ATTEMPTS);
    }
}
