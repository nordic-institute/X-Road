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

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.restapi.common.backup.dto.BackupFile;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;

import java.util.Arrays;
import java.util.Optional;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_GENERATION_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_BACKUP_GENERATION_FAILED;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseConfigurationBackupGenerator {
    protected static final String BACKUP_FILENAME_DATE_TIME_FORMAT = "yyyyMMdd-HHmmss";

    protected final String generateBackupScriptPath;
    protected final BackupService backupService;
    protected final BackupRepository backupRepository;

    private final ExternalProcessRunner externalProcessRunner;
    private final AuditDataHelper auditDataHelper;

    /**
     * Generate a new backup file
     *
     * @return
     * @throws InterruptedException if the thread the backup process is interrupted and the backup fails. <b>The
     *                              interrupted thread has already been handled with so you can choose to ignore this exception if you
     *                              so please.</b>
     */
    public BackupFile generateBackup() throws InterruptedException {
        String filename = generateBackupFileName();

        auditDataHelper.putBackupFilename(backupRepository.getAbsoluteBackupFilePath(filename));

        try {
            var args = getScriptArgs(filename);
            log.info("Run configuration backup with command '"
                    + generateBackupScriptPath + " " + Arrays.toString(args) + "'");

            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(generateBackupScriptPath, args);

            log.info(" --- Backup script console output - START --- ");
            log.info(String.join("\n", processResult.getProcessOutput()));
            log.info(" --- Backup script console output - END --- ");
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(ERROR_BACKUP_GENERATION_FAILED));
        }

        Optional<BackupFile> backupFile = backupService.getBackup(filename);
        if (backupFile.isEmpty()) {
            throw new ServiceException(BACKUP_GENERATION_FAILED);
        }
        return backupFile.get();
    }

    protected abstract String[] getScriptArgs(String backupFilename);

    protected abstract String generateBackupFileName();
}
