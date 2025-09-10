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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.backupmanager.proto.BackupInfo;
import org.niis.xroad.backupmanager.proto.BackupManagerRpcClient;
import org.niis.xroad.common.core.exception.WarningDeviation;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.common.backup.service.BackupRestoreEvent;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;

import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_DELETION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_GENERATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_GENERATION_INTERRUPTED;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_RESTORATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_RESTORATION_INTERRUPTED;
import static org.niis.xroad.common.core.exception.ErrorCode.FILE_ALREADY_EXISTS;
import static org.niis.xroad.common.core.exception.ErrorCode.GPG_KEY_GENERATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_BACKUP_FILE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_FILENAME;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_GPG_KEY_GENERATION_INTERRUPTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_FILE_ALREADY_EXISTS;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.GPG_KEY_GENERATION_INTERRUPTED;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityServerBackupService {

    private final ServerConfService serverConfService;
    private final BackupManagerRpcClient backupManagerRpcClient;
    private final AuditDataHelper auditDataHelper;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final ApplicationEventPublisher eventPublisher;
    private final PersistenceUtils persistenceUtils;
    private final ApiKeyService apiKeyService;

    public Collection<BackupInfo> listBackups() {
        try {
            return backupManagerRpcClient.listBackups();
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException("Failed to list backups"));
        }
    }

    public void deleteBackup(String name) {
        auditDataHelper.put(RestApiAuditProperty.BACKUP_FILE_NAME, name);
        try {
            backupManagerRpcClient.deleteBackup(name);
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException(ce, BACKUP_DELETION_FAILED.build(ce.getFaultString())));
        }
    }

    public byte[] readBackup(String name) {
        try {
            return backupManagerRpcClient.downloadBackup(name);
        } catch (CodedException ce) {
            throw mapException(ce, new NotFoundException(BACKUP_FILE_NOT_FOUND.build(ce.getFaultString())));
        }
    }

    public BackupInfo generateBackup() {
        try {
            BackupInfo backup = backupManagerRpcClient.createBackup(serverConfService.getSecurityServerId().toShortString());
            auditDataHelper.put(RestApiAuditProperty.BACKUP_FILE_NAME, backup.name());
            return backup;
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException(ce, BACKUP_GENERATION_FAILED.build()));
        }
    }

    public BackupInfo uploadBackup(String name, byte[] data, boolean ignoreWarnings) throws UnhandledWarningsException {
        auditDataHelper.put(RestApiAuditProperty.BACKUP_FILE_NAME, name);
        try {
            return backupManagerRpcClient.uploadBackup(name, data, ignoreWarnings);
        } catch (XrdRuntimeException ce) {
            if (ce.isCausedBy(FILE_ALREADY_EXISTS)) {
                throw new UnhandledWarningsException(new WarningDeviation(WARNING_FILE_ALREADY_EXISTS, name));
            }
            throw mapException(ce, new InternalServerErrorException("Failed to upload backup", ce));
        }
    }

    public synchronized void restoreFromBackup(String fileName) {
        auditDataHelper.put(RestApiAuditProperty.BACKUP_FILE_NAME, fileName);

        try {
            eventPublisher.publishEvent(BackupRestoreEvent.START);
            backupManagerRpcClient.restoreFromBackup(fileName, currentSecurityServerId.getServerId().toShortString());

            persistenceUtils.evictPoolConnections();
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException(ce, BACKUP_RESTORATION_FAILED.build()));
        } finally {
            eventPublisher.publishEvent(BackupRestoreEvent.END);
            apiKeyService.clearApiKeyCaches();
            log.debug("Cleared api key caches");
        }
    }

    public void generateGpgKey(String keyName) {
        try {
            backupManagerRpcClient.generateGpgKey(keyName);
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException(ce, GPG_KEY_GENERATION_FAILED.build()));
        }
    }

    private DeviationAwareRuntimeException mapException(CodedException ce, DeviationAwareRuntimeException defaultEx) {
        if (ce.getFaultCode().equals(INVALID_FILENAME.code())) {
            return new BadRequestException(INVALID_FILENAME.build(ce.getFaultString()));
        } else if (ce.getFaultCode().equals(INVALID_BACKUP_FILE.code())) {
            throw new InternalServerErrorException(INVALID_BACKUP_FILE.build(ce.getFaultString()));
        } else if (ce.getFaultCode().equals(BACKUP_FILE_NOT_FOUND.code())) {
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND.build(ce.getFaultString()));
        } else if (ce.getFaultCode().equals(BACKUP_GENERATION_FAILED.code())) {
            return new InternalServerErrorException(BACKUP_GENERATION_FAILED.build());
        } else if (ce.getFaultCode().equals(BACKUP_GENERATION_INTERRUPTED.code())) {
            return new InternalServerErrorException(BACKUP_GENERATION_INTERRUPTED.build());
        } else if (ce.getFaultCode().equals(ERROR_GPG_KEY_GENERATION_INTERRUPTED)) {
            return new InternalServerErrorException(GPG_KEY_GENERATION_INTERRUPTED.build());
        } else if (ce.getFaultCode().equals(GPG_KEY_GENERATION_FAILED.code())) {
            return new InternalServerErrorException(GPG_KEY_GENERATION_FAILED.build());
        } else if (ce.getFaultCode().equals(BACKUP_RESTORATION_FAILED.code())) {
            return new InternalServerErrorException(BACKUP_RESTORATION_FAILED.build());
        } else if (ce.getFaultCode().equals(BACKUP_RESTORATION_INTERRUPTED.code())) {
            return new InternalServerErrorException(BACKUP_RESTORATION_INTERRUPTED.build());
        } else if (ce.getFaultCode().equals(BACKUP_DELETION_FAILED.code())) {
            throw new NotFoundException(BACKUP_DELETION_FAILED.build(ce.getFaultString()));
        } else {
            return defaultEx;
        }
    }

}
