/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.BackupsConverter;
import org.niis.xroad.restapi.openapi.model.Backup;
import org.niis.xroad.restapi.repository.BackupsRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Backups service.
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class BackupsService {

    private final BackupsRepository backupsRepository;
    private final BackupsConverter backupsConverter;

    /**
     * BackupsService constructor
     * @param backupsRepository
     */
    @Autowired
    public BackupsService(BackupsRepository backupsRepository, BackupsConverter backupsConverter) {
        this.backupsRepository = backupsRepository;
        this.backupsConverter = backupsConverter;
    }

    /**
     * Return a list of available backup files
     * @return
     */
    public List<Backup> getBackupFiles() {
        List<File> backupFiles = backupsRepository.getBackupFiles();
        List<Backup> backups = backupsConverter.convert(backupFiles);
        setCreatedAt(backups);
        return backups;
    }

    /**
     * Delete a backup file or throws an exception if the file does not exist
     * @param filename
     * @throws BackupFileNotFoundException
     */
    public void deleteBackup(String filename) throws BackupFileNotFoundException {
        if (!backupExists(filename)) {
            throw new BackupFileNotFoundException(getFileNotFoundExceptionMessage(filename));
        }
        backupsRepository.deleteBackupFile(filename);
    }

    /**
     * Read backup file's content
     * @param filename
     * @return
     * @throws BackupFileNotFoundException
     */
    public byte[] readBackupFile(String filename) throws BackupFileNotFoundException {
        if (!backupExists(filename)) {
            throw new BackupFileNotFoundException(getFileNotFoundExceptionMessage(filename));
        }
        return backupsRepository.readBackupFile(filename);
    }

    /**
     * Check if a backup file with the given filename exists
     * @param filename
     * @return
     */
    private boolean backupExists(String filename) {
        Optional<Backup> backup = getBackupFiles().stream()
                .filter(b -> b.getFilename().equals(filename))
                .findFirst();
        return backup.isPresent();
    }

    /**
     * Set the "createdAt" property to a list of backups
     * @param backups
     */
    private void setCreatedAt(List<Backup> backups) {
        backups.stream().forEach(b -> {
            Date createdAt = backupsRepository.getCreatedAt(b.getFilename());
            b.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(createdAt));
        });
    }

    private String getFileNotFoundExceptionMessage(String filename) {
        StringBuilder sb = new StringBuilder();
        sb.append("Backup file with name ").append(filename).append(" not found");
        return sb.toString();
    }
}
