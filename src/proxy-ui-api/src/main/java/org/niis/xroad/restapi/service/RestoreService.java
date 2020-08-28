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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.repository.BackupRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * service class for restoring security server configuration from a backup
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class RestoreService {
    @Setter
    private String configurationRestoreScriptPath;
    @Setter
    private String configurationRestoreScriptArgs;

    private final ExternalProcessRunner externalProcessRunner;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final BackupRepository backupRepository;
    private final ApiKeyService apiKeyService;
    private final AuditDataHelper auditDataHelper;
    private final PersistenceUtils persistenceUtils;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RestoreService(ExternalProcessRunner externalProcessRunner,
            @Value("${script.restore-configuration.path}") String configurationRestoreScriptPath,
            @Value("${script.restore-configuration.args}") String configurationRestoreScriptArgs,
            CurrentSecurityServerId currentSecurityServerId, BackupRepository backupRepository,
            ApiKeyService apiKeyService, AuditDataHelper auditDataHelper, PersistenceUtils persistenceUtils,
            ApplicationEventPublisher publisher) {
        this.externalProcessRunner = externalProcessRunner;
        this.configurationRestoreScriptPath = configurationRestoreScriptPath;
        this.configurationRestoreScriptArgs = configurationRestoreScriptArgs;
        this.currentSecurityServerId = currentSecurityServerId;
        this.backupRepository = backupRepository;
        this.apiKeyService = apiKeyService;
        this.auditDataHelper = auditDataHelper;
        this.persistenceUtils = persistenceUtils;
        this.eventPublisher = publisher;
    }

    /**
     * Restores the security server configuration from a backup. Any tokens that are not software tokens are logged
     * out by the current restore script.
     * @param fileName name of the backup file
     * @throws BackupFileNotFoundException
     * @throws InterruptedException          execution of the restore script was interrupted
     * @throws RestoreProcessFailedException if the restore script fails or does not execute
     */
    public synchronized void restoreFromBackup(String fileName) throws BackupFileNotFoundException,
            InterruptedException, RestoreProcessFailedException {
        auditDataHelper.putBackupFilename(backupRepository.getFilePath(fileName));
        String configurationBackupPath = backupRepository.getConfigurationBackupPath();
        String backupFilePath = configurationBackupPath + fileName;
        File backupFile = new File(backupFilePath);
        if (!backupFile.isFile()) {
            throw new BackupFileNotFoundException("backup file " + backupFilePath + " does not exist");
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
            throw new RestoreProcessFailedException(e, "restoring from a backup failed");
        } finally {
            eventPublisher.publishEvent(BackupRestoreEvent.END);
            apiKeyService.clearApiKeyCaches();
            log.debug("Cleared api key caches");
        }
    }

    /**
     * Encodes args with base64 and returns all options and args as an array
     * @param backupFilePath
     * @return
     */
    private String[] buildArguments(String backupFilePath) {
        SecurityServerId securityServerId = currentSecurityServerId.getServerId();
        String encodedOwner = FormatUtils.encodeStringToBase64(securityServerId.toShortString());
        String encodedBackupPath = FormatUtils.encodeStringToBase64(backupFilePath);
        String argumentsString = String
                .format(configurationRestoreScriptArgs, encodedOwner, encodedBackupPath)
                .trim();
        return argumentsString.split("\\s+");
    }
}
