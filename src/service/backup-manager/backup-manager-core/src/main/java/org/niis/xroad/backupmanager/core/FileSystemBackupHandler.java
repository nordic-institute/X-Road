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

package org.niis.xroad.backupmanager.core;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.backupmanager.core.repository.BackupRepository;
import org.niis.xroad.restapi.util.FormatUtils;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_GENERATION_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_GENERATION_INTERRUPTED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_RESTORATION_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_RESTORATION_INTERRUPTED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.GPG_KEY_GENERATION_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_GPG_KEY_GENERATION_INTERRUPTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_FILE_ALREADY_EXISTS;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class FileSystemBackupHandler {
    private static final String BACKUP_FILENAME_DATE_TIME_FORMAT = "yyyyMMdd-HHmmss";

    private final ExternalProcessRunner externalProcessRunner;
    private final BackupManagerProperties backupManagerProperties;
    private final BackupRepository backupRepository;

    public BackupItem performBackup(String securityServerId) {
        log.info("Creating new backup for Security Server: {}", securityServerId);
        String name = generateBackupFileName();

        try {
            String[] args = createBackupArgs(securityServerId, name);
            log.info("Run configuration backup with command '{} {} ",
                    backupManagerProperties.backupScriptPath(), Arrays.toString(args));
            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(backupManagerProperties.backupScriptPath(), args);

            log.info(" --- Backup script console output - START --- ");
            log.info(String.join("\n", processResult.getProcessOutput()));
            log.info(" --- Backup script console output - END --- ");
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            throw new CodedException(BACKUP_GENERATION_FAILED.code(), e);
        } catch (InterruptedException e) {
            throw new CodedException(BACKUP_GENERATION_INTERRUPTED.code(), e);
        }

        return getBackupItem(name)
                .map(backupItem -> new BackupItem(backupItem.name(), backupItem.createdAt()))
                .orElseThrow(() -> new CodedException(BACKUP_GENERATION_FAILED.code()));
    }

    private String[] createBackupArgs(String securityServerId, String name) {
        String fullPath = backupRepository.getAbsoluteBackupFilePath(name).toString();
        return new String[]{"-s", securityServerId, "-f", fullPath};
    }

    public void performRestore(String name, String securityServerId) {
        log.info("Restoring from backup: {}", name);
        String[] args = createRestoreArgs(securityServerId, name);
        try {
            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(backupManagerProperties.restoreScriptPath(), args);

            int exitCode = processResult.getExitCode();

            if (log.isInfoEnabled()) {
                log.info("Restoring configuration finished with exit status {}", exitCode);
                log.info(" --- Restore script console output - START --- ");
                log.info(ExternalProcessRunner.processOutputToString(processResult.getProcessOutput()));
                log.info(" --- Restore script console output - END --- ");
            }
        } catch (ProcessFailedException | ProcessNotExecutableException e) {
            throw new CodedException(BACKUP_RESTORATION_FAILED.code(), e);
        } catch (InterruptedException e) {
            throw new CodedException(BACKUP_RESTORATION_INTERRUPTED.code(), e);
        }
    }

    private String[] createRestoreArgs(String securityServerId, String name) {
        String fullPath = backupRepository.getAbsoluteBackupFilePath(name).toString();
        String encodedOwner = FormatUtils.encodeStringToBase64(securityServerId);
        String encodedBackupPath = FormatUtils.encodeStringToBase64(fullPath);

        return "-b -s %s -f %s"
                .formatted(encodedOwner, encodedBackupPath)
                .trim()
                .split("\\s+");
    }

    public void deleteBackup(String name) {
        log.info("Delete backup: {}", name);
        backupRepository.deleteBackup(name);
    }

    public Collection<BackupItem> listBackups() {
        log.debug("Listing backups");
        return backupRepository.listBackups();
    }

    public byte[] readBackup(String name) {
        log.debug("Get backup: {}", name);
        return backupRepository.readBackupFile(name);
    }

    public BackupItem saveBackup(String name, byte[] content, boolean ignoreWarnings) {
        log.info("Saving uploaded backup: {}", name);
        if (!ignoreWarnings && getBackupItem(name).isPresent()) {
            throw new CodedException(WARNING_FILE_ALREADY_EXISTS, "Backup with this name already exists");
        }
        return backupRepository.storeBackup(name, content);
    }

    public void generateGpgKey(String keyName) {
        String[] args = new String[]{backupManagerProperties.gpgKeysHomePath(), keyName};
        try {
            log.info("Generating GPG keypair with command '{} {}'", backupManagerProperties.generateGpgKeypairScriptPath(),
                    Arrays.toString(args));

            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(backupManagerProperties.generateGpgKeypairScriptPath(), args);

            log.info(" --- Generate GPG keypair script console output - START --- ");
            log.info(String.join("\n", processResult.getProcessOutput()));
            log.info(" --- Generate GPG keypair script console output - END --- ");
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            throw new CodedException(GPG_KEY_GENERATION_FAILED.code(), e);
        } catch (InterruptedException e) {
            throw new CodedException(ERROR_GPG_KEY_GENERATION_INTERRUPTED, e);
        }
    }

    private Optional<BackupItem> getBackupItem(String name) {
        return backupRepository.listBackups().stream()
                .filter(b -> b.name().equals(name))
                .findFirst();
    }

    private String generateBackupFileName() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(BACKUP_FILENAME_DATE_TIME_FORMAT);
        return "conf_backup_" + TimeUtils.localDateTimeNow().format(dtf) + ".gpg";
    }

}
