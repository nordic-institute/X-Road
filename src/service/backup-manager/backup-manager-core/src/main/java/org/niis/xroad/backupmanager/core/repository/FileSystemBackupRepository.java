/*
 * The MIT License
 *
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

package org.niis.xroad.backupmanager.core.repository;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.backupmanager.core.BackupItem;
import org.niis.xroad.backupmanager.core.BackupManagerProperties;
import org.niis.xroad.backupmanager.core.BackupValidator;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.NotFoundException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_DELETION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_BACKUP_FILE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_FILENAME;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class FileSystemBackupRepository implements BackupRepository {

    // Set maximum number of levels of directories to visit, subdirectories are excluded
    private static final int DIR_MAX_DEPTH = 1;

    private final BackupManagerProperties backupManagerProperties;
    private final BackupValidator backupValidator;

    @Override
    public Collection<BackupItem> listBackups() {
        String backupLocation = backupManagerProperties.backupLocation();
        Path backupPath = Paths.get(backupLocation);
        if (!Files.exists(backupPath)) {
            log.warn("Backup directory [{}] does not exist.",
                    backupManagerProperties.backupLocation());
            return Collections.emptyList();
        }
        try (Stream<Path> walk = Files.walk(backupPath, DIR_MAX_DEPTH)) {
            return walk
                    .filter(path -> backupValidator.isValidBackupFilename(path.getFileName().toString()))
                    .map(path -> {
                        var file = path.toFile();
                        return new BackupItem(file.getName(), getCreatedAt(file.toPath()));
                    })
                    .collect(Collectors.toList());
        } catch (IOException ioe) {
            log.error("can't read backup files from configuration path ({})", backupLocation, ioe);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteBackup(String name) {
        if (!backupValidator.isValidBackupFilename(name)) {
            throw XrdRuntimeException.systemException(BACKUP_FILE_NOT_FOUND, name);
        }
        var path = getAbsoluteBackupFilePath(name);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ioe) {
            log.error("can't delete backup file ({})", path);
            throw XrdRuntimeException.systemException(BACKUP_DELETION_FAILED, name);
        }
    }

    @Override
    public BackupItem storeBackup(String name, byte[] content) {
        if (!backupValidator.isValidBackupFilename(name)) {
            throw XrdRuntimeException.systemException(INVALID_FILENAME, name);
        }
        var backupDirectoryPath = new File(backupManagerProperties.backupLocation());
        if (backupDirectoryPath.mkdirs()) {
            log.info("backup directory was created");
        }

        var path = getAbsoluteBackupFilePath(name);
        try {
            Files.write(path, content);
            return new BackupItem(name, getCreatedAt(path));
        } catch (IOException ioe) {
            log.error("can't write backup file's content ({})", path);
            throw XrdRuntimeException.systemException(INVALID_BACKUP_FILE, name);
        }
    }

    @Override
    public byte[] readBackupFile(String filename) throws NotFoundException {
        if (!backupValidator.isValidBackupFilename(filename)) {
            throw XrdRuntimeException.systemException(BACKUP_FILE_NOT_FOUND, filename);
        }
        var path = getAbsoluteBackupFilePath(filename);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("can't read backup file's content ({})", path);
            throw XrdRuntimeException.systemException(BACKUP_FILE_NOT_FOUND, filename);
        }
    }

    @Override
    public Path getAbsoluteBackupFilePath(String name) {
        final var resolved = Paths.get(backupManagerProperties.backupLocation()).resolve(name);
        if (!resolved.normalize().startsWith(Paths.get(backupManagerProperties.backupLocation()))) {
            throw XrdRuntimeException.systemException(INVALID_FILENAME, name);
        }
        return resolved;
    }

    private Instant getCreatedAt(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return attr.creationTime().toInstant();
        } catch (IOException ioe) {
            log.error("can't read backup file's creation time ({})", path);
            return null;
        }
    }
}
