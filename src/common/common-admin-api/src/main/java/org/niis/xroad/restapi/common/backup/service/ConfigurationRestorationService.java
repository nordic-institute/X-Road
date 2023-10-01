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
package org.niis.xroad.restapi.common.backup.service;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.File;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_RESTORATION_FAILED;

/**
 * service class for restoring security server configuration from a backup
 */
@Slf4j
@PreAuthorize("isAuthenticated()")
@AllArgsConstructor
public abstract class ConfigurationRestorationService {

    private final ExternalProcessRunner externalProcessRunner;
    private final BackupRepository backupRepository;
    private final ApiKeyService apiKeyService;
    private final AuditDataHelper auditDataHelper;
    private final PersistenceUtils persistenceUtils;
    private final ApplicationEventPublisher eventPublisher;

    @Setter
    private String configurationRestoreScriptPath;

    /**
     * Restores the security server configuration from a backup. Any tokens that are not software tokens are logged
     * out by the current restore script.
     *
     * @param fileName name of the backup file
     * @throws InterruptedException execution of the restore script was interrupted
     * @throws ServiceException     if the restore script fails or does not execute
     */
    public synchronized void restoreFromBackup(String fileName) throws
            InterruptedException, ServiceException {
        auditDataHelper.putBackupFilename(backupRepository.getAbsoluteBackupFilePath(fileName));
        String configurationBackupPath = backupRepository.getConfigurationBackupPath();
        String backupFilePath = configurationBackupPath + fileName;
        File backupFile = new File(backupFilePath);
        if (!backupFile.isFile()) {
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND, backupFilePath);
        }
        String[] arguments = buildArguments(backupFilePath);
        try {
            eventPublisher.publishEvent(BackupRestoreEvent.START);
            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(configurationRestoreScriptPath, arguments);

            int exitCode = processResult.getExitCode();

            if (log.isInfoEnabled()) {
                String restoreFinishedLogMsg =
                        String.format("Restoring configuration finished with exit status %s", exitCode);
                log.info(restoreFinishedLogMsg);
                log.info(" --- Restore script console output - START --- ");
                log.info(ExternalProcessRunner.processOutputToString(processResult.getProcessOutput()));
                log.info(" --- Restore script console output - END --- ");
            }

            persistenceUtils.evictPoolConnections();

        } catch (ProcessFailedException | ProcessNotExecutableException e) {
            throw new ServiceException(BACKUP_RESTORATION_FAILED, e);
        } finally {
            eventPublisher.publishEvent(BackupRestoreEvent.END);
            apiKeyService.clearApiKeyCaches();
            log.debug("Cleared api key caches");
        }
    }

    /**
     * Encodes args with base64 and returns all options and args as an array
     *
     * @param backupFilePath
     * @return
     */
    protected abstract String[] buildArguments(String backupFilePath);
}
