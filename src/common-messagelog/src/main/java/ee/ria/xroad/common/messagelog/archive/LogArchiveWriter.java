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

import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.io.FileUtils.deleteQuietly;

/**
 * Class for writing log records to zip file containing ASiC containers
 * (archive).
 */
@Slf4j
public class LogArchiveWriter implements Closeable {

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private final Path outputPath;
    private final LogArchiveBase archiveBase;

    private final LinkingInfoBuilder linkingInfoBuilder;

    private LogArchiveCache logArchiveCache;

    private Path archiveTmp;

    private final GroupingStrategy groupingStrategy = MessageLogProperties.getArchiveGrouping();
    private final EncryptionConfigProvider encryptionConfigProvider;
    private Grouping grouping;

    /**
     * Creates new LogArchiveWriter
     *
     * @param outputPath  directory where the log archive is created.
     * @param archiveBase interface to archive database.
     */
    public LogArchiveWriter(Path outputPath, LogArchiveBase archiveBase) throws IOException {
        this.outputPath = outputPath;
        this.archiveBase = archiveBase;
        this.linkingInfoBuilder = new LinkingInfoBuilder(MessageLogProperties.getHashAlg());
        this.encryptionConfigProvider = EncryptionConfigProvider.getInstance(groupingStrategy);
    }

    /**
     * Write a message log record to an archive. If message record grouping is enabled, assumes that the records are
     * ordered by the group and starts a new archive accordingly. If encryption is enabled, the archives will be
     * encrypted.
     *
     * @param logRecord the log record
     * @return true if the a archive file was rotated
     * @throws Exception in case of any errors
     */
    public boolean write(MessageRecord logRecord) throws Exception {
        if (logRecord == null) {
            throw new IllegalArgumentException("log record must not be null");
        }

        if (log.isTraceEnabled()) log.trace("write({})", logRecord.getId());
        boolean rotated = false;

        if (grouping == null) {
            prepareGrouping(logRecord);
        } else {
            if (!grouping.includes(logRecord)) {
                rotate();
                logArchiveCache.close();
                prepareGrouping(logRecord);
                rotated = true;
            }
        }
        logArchiveCache.add(logRecord);

        archiveBase.markRecordArchived(logRecord);

        if (logArchiveCache.isRotating()) {
            rotate();
            return true;
        }
        return rotated;
    }

    private void prepareGrouping(MessageRecord logRecord) throws IOException {
        grouping = groupingStrategy.forRecord(logRecord);
        linkingInfoBuilder.reset(archiveBase.loadLastArchive(grouping.name()));
        logArchiveCache = new LogArchiveCache(
                linkingInfoBuilder,
                encryptionConfigProvider.forGrouping(grouping),
                outputPath);
    }

    @Override
    public void close() throws IOException {
        log.trace("Closing log archive writer ...");

        try {
            saveArchive();
        } finally {
            if (logArchiveCache != null) {
                logArchiveCache.close();
            }
            clearTempArchive();
        }
    }

    private void clearTempArchive() {
        if (archiveTmp != null) {
            deleteQuietly(archiveTmp.toFile());
        }
        archiveTmp = null;
    }

    protected String getArchiveFilename(String digest) {
        final String groupName = escape(grouping.name());
        final String suffix = encryptionConfigProvider.isEncryptionEnabled() ? "zip.gpg" : "zip";

        return String.format("mlog-%.200s%s-%s-%.16s.%s",
                groupName == null ? "" : groupName + "-",
                simpleDateFormat.format(logArchiveCache.getStartTime()),
                simpleDateFormat.format(logArchiveCache.getEndTime()),
                digest,
                suffix);
    }

    protected void rotate() throws IOException {
        log.trace("rotate()");
        saveArchive();
        archiveTmp = null;
    }

    private void saveArchive() throws IOException {
        if (logArchiveCache == null || logArchiveCache.isEmpty()) {
            return;
        }
        final String digest = linkingInfoBuilder.getLastDigest();
        Path archiveFile = getUniqueArchiveFilename(digest);
        archiveTmp = logArchiveCache.getArchiveFile();
        atomicMove(archiveTmp, archiveFile);
        final DigestEntry digestEntry = new DigestEntry(digest, archiveFile.getFileName().toString());
        archiveBase.markArchiveCreated(grouping.name(), digestEntry);
        linkingInfoBuilder.reset(digestEntry);
        archiveTmp = null;
        log.info("Created archive file {}", archiveFile);
    }

    private Path getUniqueArchiveFilename(String digest) {
        Path archive = outputPath.resolve(getArchiveFilename(digest));
        if (archive.toFile().exists()) {
            log.warn("Existing archive file {} will be replaced", archive);
        }
        return archive;
    }

    private static void atomicMove(Path source, Path destination) throws IOException {
        Files.move(source, destination, REPLACE_EXISTING, ATOMIC_MOVE);
    }

    private String escape(String s) {
        return s == null ? null : s.replaceAll("[\\00\\\\<>/:|*?\\p{gc=Cc}]", "_");
    }
}

