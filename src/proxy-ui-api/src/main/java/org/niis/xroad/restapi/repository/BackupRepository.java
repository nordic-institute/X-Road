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
package org.niis.xroad.restapi.repository;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Backup repository
 */
@Slf4j
@Repository
public class BackupRepository {

    private static final String CONFIGURATION_BACKUP_PATH = SystemProperties.getConfBackupPath();
    // Set maximum number of levels of directories to visit, subdirectories are excluded
    private static final int DIR_MAX_DEPTH = 1;
    private static final String INVALID_BACKUP_FILENAME = "invalid backup filename";

    /**
     * Read backup files from configuration backup path
     * @return
     */
    public List<File> getBackupFiles() {
        try (Stream<Path> walk = Files.walk(Paths.get(CONFIGURATION_BACKUP_PATH), DIR_MAX_DEPTH)) {
            return walk.filter(this::isValidBackupFilename).map(this::getFile).collect(Collectors.toList());
        } catch (IOException ioe) {
            log.error("can't read backup files from configuration path (" + CONFIGURATION_BACKUP_PATH + ")");
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Get the creation date/time of a backup file
     * @param filename
     * @return
     */
    public OffsetDateTime getCreatedAt(String filename) {
        Path path = getFilePath(filename);
        try {
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return attr.creationTime().toInstant().atOffset(ZoneOffset.UTC);
        } catch (IOException ioe) {
            log.error("can't read backup file's creation time (" + path.toString() + ")");
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Delete a backup file
     * @param filename
     */
    public void deleteBackupFile(String filename) {
        if (!FormatUtils.isValidBackupFilename(filename)) {
            throw new RuntimeException(INVALID_BACKUP_FILENAME);
        }
        Path path = getFilePath(filename);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ioe) {
            log.error("can't delete backup file (" + path.toString() + ")");
            throw new RuntimeException("deleting backup file failed");
        }
    }

    /**
     * Read backup file's content
     * @param filename
     * @return
     */
    public byte[] readBackupFile(String filename) {
        if (!FormatUtils.isValidBackupFilename(filename)) {
            throw new RuntimeException(INVALID_BACKUP_FILENAME);
        }
        Path path = getFilePath(filename);
        try {
            return Files.readAllBytes(path);
        } catch (IOException ioe) {
            log.error("can't read backup file's content (" + path.toString() + ")");
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Writes backup file's content to disk
     * @param filename
     * @param content
     * @return
     */
    public OffsetDateTime writeBackupFile(String filename, byte[] content) {
        if (!FormatUtils.isValidBackupFilename(filename)) {
            throw new RuntimeException(INVALID_BACKUP_FILENAME);
        }
        Path path = getFilePath(filename);
        try {
            Files.write(path, content);
            return getCreatedAt(path.getFileName().toString());
        } catch (IOException ioe) {
            log.error("can't write backup file's content (" + path.toString() + ")");
            throw new RuntimeException(ioe);
        }
    }
    /**
     * Return configuration backup path with a trailing slash
     * @return
     */
    public String getConfigurationBackupPath() {
        return CONFIGURATION_BACKUP_PATH  + (CONFIGURATION_BACKUP_PATH.endsWith(File.separator) ? "" : File.separator);
    }

    private File getFile(Path path) {
        return new File(path.toString());
    }

    /**
     * Return absolute path to (possible) backup file with given filename
     * @param filename
     * @return
     */
    public Path getFilePath(String filename) {
        return Paths.get(getConfigurationBackupPath(), filename);
    }

    /**
     * Check if the given filename is valid and meets the defined criteria
     * @param path
     * @return
     */
    private boolean isValidBackupFilename(Path path) {
        return FormatUtils.isValidBackupFilename(path.getFileName().toString());
    }

    /**
     * Check if a backup file with the given name already exists
     * @param filename
     * @return
     */
    public boolean fileExists(String filename) {
        return getFilePath(filename).toFile().exists();
    }
}
