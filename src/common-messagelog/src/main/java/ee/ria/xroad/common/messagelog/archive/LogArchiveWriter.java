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
import java.util.Objects;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

/**
 * Class for writing log records to zip file containing ASiC containers
 * (archive).
 */
@Slf4j
public class LogArchiveWriter implements Closeable {

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    public static final int MAX_RANDOM_GEN_ATTEMPTS = 1000;

    private static final int RANDOM_LENGTH = 10;

    private final Path outputPath;
    private final LogArchiveBase archiveBase;

    private final LinkingInfoBuilder linkingInfoBuilder;
    private final LogArchiveCache logArchiveCache;

    private Path archiveTmp;

    private final GroupingStrategy groupingStrategy =
            GroupingStrategy.valueOf(MessageLogProperties.getArchiveGrouping());

    private Grouping grouping;

    /**
     * Creates new LogArchiveWriter
     *
     * @param outputPath  directory where the log archive is created.
     * @param archiveBase interface to archive database.
     */
    public LogArchiveWriter(Path outputPath, LogArchiveBase archiveBase) {
        this.outputPath = outputPath;
        this.archiveBase = archiveBase;

        this.linkingInfoBuilder = new LinkingInfoBuilder(
                MessageLogProperties.getHashAlg(),
                archiveBase
        );

        this.logArchiveCache = new LogArchiveCache(
                () -> randomAlphanumeric(RANDOM_LENGTH),
                linkingInfoBuilder,
                outputPath
        );
    }

    /**
     * Write a message log record.
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
            grouping = groupingStrategy.forRecord(logRecord);
        } else {
            if (!grouping.includes(logRecord)) {
                rotate();
                grouping = groupingStrategy.forRecord(logRecord);
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

    @Override
    public void close() throws IOException {
        log.trace("Closing log archive writer ...");

        try {
            saveArchive();
        } finally {
            logArchiveCache.close();
            clearTempArchive();
        }
    }

    private void clearTempArchive() {
        if (archiveTmp != null) {
            deleteQuietly(archiveTmp.toFile());
        }
        archiveTmp = null;
    }

    protected String getArchiveFilename(String random) {
        final String groupName = escape(grouping.name());

        return String.format("mlog-%s%s-%s-%s.zip",
                groupName == "" ? "" : groupName + "-",
                simpleDateFormat.format(logArchiveCache.getStartTime()),
                simpleDateFormat.format(logArchiveCache.getEndTime()),
                random);
    }

    protected void rotate() throws IOException {
        log.trace("rotate()");
        saveArchive();
        archiveTmp = null;
    }

    private void saveArchive() throws IOException {
        if (logArchiveCache.isEmpty()) {
            return;
        }
        Path archiveFile = getUniqueArchiveFilename();
        archiveTmp = logArchiveCache.getArchiveFile();
        atomicMove(archiveTmp, archiveFile);
        setArchivedInDatabase(archiveFile.getFileName().toString());
        linkingInfoBuilder.afterArchiveSaved();
        archiveTmp = null;
        log.info("Created archive file {}", archiveFile);
    }

    private void setArchivedInDatabase(String archiveFilename)
            throws IOException {
        try {
            archiveBase.markArchiveCreated(
                    new DigestEntry(
                            linkingInfoBuilder.getCreatedArchiveLastDigest(),
                            archiveFilename
                    )
            );
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private Path getUniqueArchiveFilename() {
        Path archive = outputPath.resolve(getArchiveFilename(randomAlphanumeric(RANDOM_LENGTH)));
        int attempts = 0;
        while (archive.toFile().exists()) {
            if (++attempts > MAX_RANDOM_GEN_ATTEMPTS) {
                throw new IllegalStateException("Could not generate unique file in "
                        + MAX_RANDOM_GEN_ATTEMPTS + " attempts");
            }
            archive = outputPath.resolve(getArchiveFilename(randomAlphanumeric(RANDOM_LENGTH)));
        }
        return archive;
    }

    private static void atomicMove(Path source, Path destination)
            throws IOException {
        Files.move(source, destination, REPLACE_EXISTING, ATOMIC_MOVE);
    }

    private String escape(String s) {
        return s.replaceAll("[\\00\\\\<>/:|*?\\p{gc=Cc}]", "_");
    }
}

enum GroupingStrategy {
    NONE {
        @Override
        Grouping forRecord(MessageRecord record) {
            return NONE_GROUPING;
        }
    },
    BY_MEMBER {
        @Override
        Grouping forRecord(MessageRecord record) {
            return new MemberGrouping(record);
        }
    },
    BY_SUBSYSTEM {
        @Override
        Grouping forRecord(MessageRecord record) {
            return new SubsystemGrouping(record);
        }
    };

    abstract Grouping forRecord(MessageRecord record);

    private static final Grouping NONE_GROUPING = new Grouping() {
        @Override
        public boolean includes(MessageRecord record) {
            return true;
        }

        @Override
        public String name() {
            return "";
        }
    };
}

interface Grouping {
    boolean includes(MessageRecord record);

    String name();
}

final class SubsystemGrouping implements Grouping {
    private final String memberClass;
    private final String memberCode;
    private final String subsystemCode;

    SubsystemGrouping(MessageRecord record) {
        this.memberClass = record.getMemberClass();
        this.memberCode = record.getMemberCode();
        this.subsystemCode = record.getSubsystemCode();
    }

    /**
     * checks if the record belongs to this record group
     */
    @Override
    public boolean includes(MessageRecord record) {
        return Objects.equals(memberClass, record.getMemberClass())
                && Objects.equals(memberCode, record.getMemberCode())
                && Objects.equals(subsystemCode, record.getSubsystemCode());

    }

    public String name() {
        StringBuilder b = new StringBuilder();
        b.append(memberClass);
        b.append("-");
        b.append(memberCode);
        if (subsystemCode != null) {
            b.append("-");
            b.append(subsystemCode);
        }
        return b.toString();
    }
}

final class MemberGrouping implements Grouping {
    private final String memberClass;
    private final String memberCode;

    MemberGrouping(MessageRecord record) {
        this.memberClass = record.getMemberClass();
        this.memberCode = record.getMemberCode();
    }

    /**
     * checks if the record belongs to this record group
     */
    @Override
    public boolean includes(MessageRecord record) {
        return Objects.equals(memberClass, record.getMemberClass())
                && Objects.equals(memberCode, record.getMemberCode());
    }

    public String name() {
        StringBuilder b = new StringBuilder();
        b.append(memberClass);
        b.append("-");
        b.append(memberCode);
        return b.toString();
    }
}
