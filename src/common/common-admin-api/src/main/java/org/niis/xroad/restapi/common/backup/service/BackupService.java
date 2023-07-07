/**
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
package org.niis.xroad.restapi.common.backup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.restapi.common.backup.dto.BackupFile;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_FILE_ALREADY_EXISTS;

/**
 * Backups service.
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BackupService {
    private final BackupRepository backupRepository;
    private final AuditDataHelper auditDataHelper;

    /**
     * Return a list of available backup files
     *
     * @return list of backup files
     */
    public List<BackupFile> getBackupFiles() {
        return backupRepository.getBackupFiles();
    }

    /**
     * Delete a backup file or throws an exception if the file does not exist
     *
     * @param filename backup file name to delete
     * @throws NotFoundException if backup file is not found
     */
    public void deleteBackup(String filename) throws NotFoundException {
        auditDataHelper.putBackupFilename(backupRepository.getAbsoluteBackupFilePath(filename));
        if (getBackup(filename).isEmpty()) {
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND, filename);
        }
        backupRepository.deleteBackupFile(filename);
    }

    /**
     * Read backup file's content
     *
     * @param filename backup file name to retrieve
     * @return raw contents of file
     * @throws NotFoundException if backup file is not found
     */
    public byte[] readBackupFile(String filename) throws NotFoundException {
        if (getBackup(filename).isEmpty()) {
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND, filename);
        }
        return backupRepository.readBackupFile(filename);
    }

    /**
     * Write uploaded backup file to disk. If ignoreWarnings=false, an exception is thrown when a file with
     * the same name already exists. If ignoreWarnings=true, the existing file is overwritten.
     *
     * @param ignoreWarnings should the warning be ignored
     * @param filename       backup file name
     * @param fileBytes      file content
     * @return newly created backup
     * @throws UnhandledWarningsException if backup file with the same name already exists
     *                                    and ignoreWarnings is false
     * @throws ValidationFailureException if backup file is not a valid tar file
     *                                    or the first entry of the tar file
     *                                    does not match to the first entry if the Security Server generated backup tar files
     */
    public BackupFile uploadBackup(boolean ignoreWarnings, String filename, byte[] fileBytes)
            throws UnhandledWarningsException, ValidationFailureException {
        auditDataHelper.putBackupFilename(backupRepository.getAbsoluteBackupFilePath(filename));

        if (!ignoreWarnings && backupRepository.fileExists(filename)) {
            throw new UnhandledWarningsException(new WarningDeviation(WARNING_FILE_ALREADY_EXISTS, filename));
        }

        OffsetDateTime createdAt = backupRepository.writeBackupFile(filename, fileBytes);

        return new BackupFile(filename, createdAt);
    }

    /**
     * Get a backup file with the given filename
     *
     * @param filename backup file name
     * @return backup, if available
     */
    public Optional<BackupFile> getBackup(String filename) {
        return getBackupFiles().stream()
                .filter(b -> b.getFilename().equals(filename))
                .findFirst();
    }
}
