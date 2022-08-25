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

import ee.ria.xroad.common.asic.AsicContainerNameGenerator;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;

import com.google.common.io.CountingOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveMaxFilesize;

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

    private final LinkingInfoBuilder linkingInfoBuilder;
    private final Path workingDir;

    private AsicContainerNameGenerator nameGenerator;
    private State state = State.NEW;

    private Path archiveTmpFile;
    private ZipOutputStream archiveTmp;
    private OutputStream outputStream;

    private Date minCreationTime;
    private Date maxCreationTime;
    private long archivesTotalSize;

    private final EncryptionConfig encryptionConfig;

    LogArchiveCache(LinkingInfoBuilder linkingInfoBuilder,
            EncryptionConfig encryptionConfig,
            Path workingDir) {
        this.linkingInfoBuilder = linkingInfoBuilder;
        this.encryptionConfig = encryptionConfig;
        this.workingDir = workingDir;
        resetCacheState();
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

    Path getArchiveFile() throws IOException {
        try {
            addLinkingInfoToArchive(archiveTmp);
            archiveTmp.close();
            archiveTmp = null;
            Path archive = archiveTmpFile;
            archiveTmpFile = null;
            outputStream = null;
            resetCacheState();
            return archive;
        } catch (IOException e) {
            handleCacheError(e);
            return null;
        }
    }

    private <T extends Exception> void handleCacheError(T e) throws T {
        deleteArchiveArtifacts(e);
        throw e;
    }

    private void addLinkingInfoToArchive(ZipOutputStream zipOut)
            throws IOException {
        ZipEntry linkingInfoEntry = new ZipEntry("linkinginfo");
        linkingInfoEntry.setLastModifiedTime(FileTime.from(maxCreationTime.toInstant()));
        zipOut.putNextEntry(linkingInfoEntry);
        zipOut.write(linkingInfoBuilder.build());
        zipOut.closeEntry();

    }

    boolean isRotating() {
        return state == State.ROTATING;
    }

    boolean isEmpty() {
        return state == State.NEW;
    }

    Date getStartTime() {
        return minCreationTime;
    }

    Date getEndTime() {
        return maxCreationTime;
    }

    @Override
    public void close() {
        deleteArchiveArtifacts(null);
    }

    private void validateMessageRecord(MessageRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Message record to be archived must not be null");
        }
    }

    private void handleRotation() throws IOException {
        if (state == State.ADDING) {
            return;
        }
        resetArchive();
    }

    @SuppressWarnings("checkstyle:InnerAssignment")
    private void cacheRecord(MessageRecord messageRecord) throws Exception {
        final Date creationTime = new Date(messageRecord.getTime());

        if (minCreationTime == null && maxCreationTime == null) {
            minCreationTime = maxCreationTime = creationTime;
        } else if (creationTime.before(minCreationTime)) {
            minCreationTime = creationTime;
        } else if (creationTime.after(maxCreationTime)) {
            maxCreationTime = creationTime;
        }
        addContainerToArchive(messageRecord);
    }

    private void updateState() {
        state = archiveExceedsRotationSize() ? State.ROTATING : State.ADDING;
    }

    private boolean archiveExceedsRotationSize() {
        return archivesTotalSize > getArchiveMaxFilesize();
    }

    private void addContainerToArchive(MessageRecord record) throws Exception {
        String archiveFilename = nameGenerator.getArchiveFilename(record.getQueryId(), record.isResponse(),
                record.getId());

        final MessageDigest digest = MessageDigest.getInstance(MessageLogProperties.getHashAlg());
        final ZipEntry entry = new ZipEntry(archiveFilename);
        entry.setLastModifiedTime(FileTime.from(record.getTime(), TimeUnit.MILLISECONDS));
        archiveTmp.putNextEntry(entry);
        try (CountingOutputStream cos = new CountingOutputStream(
                new DigestOutputStream(new EntryStream(archiveTmp), digest));
             OutputStream bos = new BufferedOutputStream(cos)) {
            // ZipOutputStream writing directly to a DigestOutputStream is extremely inefficient, hence the additional
            // buffering. Digesting a stream instead of an in-memory buffer because the archive can be
            // large (over 1GiB)
            record.toAsicContainer().write(bos);
            bos.flush();
            archivesTotalSize += cos.getCount();
        }
        archiveTmp.closeEntry();
        linkingInfoBuilder.addNextFile(archiveFilename, digest.digest());
    }

    private void resetArchive() throws IOException {
        deleteArchiveArtifacts(null);
        archiveTmpFile = Files.createTempFile(workingDir, "tmp-mlog-", ".tmp");
        if (encryptionConfig.isEnabled()) {
            outputStream = new GPGOutputStream(encryptionConfig.getGpgHomeDir(), archiveTmpFile,
                    encryptionConfig.getEncryptionKeys());
        } else {
            outputStream = Files.newOutputStream(archiveTmpFile);
        }
        archiveTmp = new ZipOutputStream(new BufferedOutputStream(outputStream));
        archiveTmp.setLevel(0);
    }

    private void deleteArchiveArtifacts(Exception cause) {
        if (archiveTmp != null) {
            try {
                archiveTmp.close();
            } catch (IOException e) {
                if (cause != null) {
                    cause.addSuppressed(e);
                }
            }
        }
        // in case of error during close, ZipOutputStream can fail to close the underlying OutputStream.
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                if (cause != null) {
                    cause.addSuppressed(e);
                }
            }
        }
        if (archiveTmpFile != null) {
            FileUtils.deleteQuietly(archiveTmpFile.toFile());
        }
    }

    private void resetCacheState() {
        minCreationTime = null;
        maxCreationTime = null;
        state = State.NEW;
        archivesTotalSize = 0;
        nameGenerator = new AsicContainerNameGenerator();
    }

    static class EntryStream extends FilterOutputStream {

        EntryStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() {
            //NOP
        }
    }
}
