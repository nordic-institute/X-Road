/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.BackupFile;
import org.niis.xroad.cs.admin.core.config.BackupConfig;
import org.niis.xroad.cs.admin.core.service.BackupValidator;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_DELETION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_BACKUP_FILE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_FILENAME;

/**
 * Backup repository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BackupRepository {
    // Set maximum number of levels of directories to visit, subdirectories are excluded
    private static final int DIR_MAX_DEPTH = 1;

    private final BackupValidator backupValidator;
    private final BackupConfig backupConfig;

    /**
     * Read backup files from configuration backup path
     * @return list of backup files
     */
    public List<BackupFile> getBackupFiles() {
        var backupPath = Paths.get(backupConfig.getConfBackupPath());
        if (!Files.exists(backupPath)) {
            log.warn("Backup directory [{}] does not exist.",
                    backupConfig.getConfBackupPath());
            return Collections.emptyList();
        }

        try (Stream<Path> walk = Files.walk(backupPath, DIR_MAX_DEPTH)) {
            return walk
                    .filter(path -> backupValidator.isValidBackupFilename(path.getFileName().toString()))
                    .map(path -> {
                        var file = path.toFile();
                        return new BackupFile(file.getName(), getCreatedAt(file.toPath()));
                    })
                    .collect(Collectors.toList());
        } catch (IOException ioe) {
            log.error("can't read backup files from configuration path ({})", backupConfig.getConfBackupPath(), ioe);
            return Collections.emptyList();
        }
    }

    private OffsetDateTime getCreatedAt(Path path) {
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return attr.creationTime().toInstant().atOffset(ZoneOffset.UTC);
        } catch (IOException ioe) {
            log.error("can't read backup file's creation time ({})", path);
            return null;
        }
    }

    /**
     * Delete a backup file
     * @param filename backup filename to delete
     */
    public void deleteBackupFile(String filename) throws NotFoundException {
        if (!backupValidator.isValidBackupFilename(filename)) {
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND.build(filename));
        }
        var path = getAbsoluteBackupFilePath(filename);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ioe) {
            log.error("can't delete backup file ({})", path);
            throw new NotFoundException(BACKUP_DELETION_FAILED.build(filename));
        }
    }

    /**
     * Read backup file's content
     * @param filename backup filename
     * @return backup content
     */
    public byte[] readBackupFile(String filename) throws NotFoundException {
        if (!backupValidator.isValidBackupFilename(filename)) {
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND.build(filename));
        }
        var path = getAbsoluteBackupFilePath(filename);
        try {
            return Files.readAllBytes(path);
        } catch (IOException ioe) {
            log.error("can't read backup file's content ({})", path);
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND.build(filename));
        }
    }

    /**
     * Writes backup file's content to disk
     * @param filename backup filename
     * @param content  backup contents
     * @return backup creation date
     */
    public OffsetDateTime writeBackupFile(String filename, byte[] content) {
        if (!backupValidator.isValidBackupFilename(filename)) {
            throw new BadRequestException(INVALID_FILENAME.build(filename));
        }
        var backupDirectoryPath = new File(getConfigurationBackupPath());
        if (backupDirectoryPath.mkdirs()) {
            log.info("backup directory was created");
        }

        var path = getAbsoluteBackupFilePath(filename);
        try {
            Files.write(path, content);
            return getCreatedAt(path);
        } catch (IOException ioe) {
            log.error("can't write backup file's content ({})", path);
            throw new InternalServerErrorException(ioe, INVALID_BACKUP_FILE.build());
        }
    }

    /**
     * Return configuration backup path with a trailing slash
     */
    public String getConfigurationBackupPath() {
        return backupConfig.getConfBackupPath() + (backupConfig.getConfBackupPath().endsWith(File.separator) ? "" : File.separator);
    }

    /**
     * Return absolute path to (possible) backup file with given filename
     * @param filename backup filename
     * @return path to the file
     */
    public Path getAbsoluteBackupFilePath(String filename) {
        final var resolved = Paths.get(backupConfig.getConfBackupPath()).resolve(filename);
        if (!resolved.normalize().startsWith(Paths.get(backupConfig.getConfBackupPath()))) {
            throw new BadRequestException(INVALID_FILENAME.build(filename));
        }
        return resolved;
    }


    /**
     * Check if a backup file with the given name already exists
     * @param filename backup filename
     * @return true if file exists
     */
    public boolean fileExists(String filename) {
        return getAbsoluteBackupFilePath(filename).toFile().exists();
    }
}
