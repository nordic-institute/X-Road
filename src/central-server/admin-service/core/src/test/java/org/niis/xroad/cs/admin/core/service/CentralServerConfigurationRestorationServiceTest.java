/*
 * The MIT License
 * <p>
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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.common.backup.service.BackupRestoreEvent;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CentralServerConfigurationRestorationServiceTest {

    @Mock
    private ExternalProcessRunner externalProcessRunner;
    @Mock
    private BackupRepository backupRepository;
    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private PersistenceUtils persistenceUtils;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private HAConfigStatus haConfigStatus;

    @InjectMocks
    private CentralServerConfigurationRestorationService configurationRestorationService;

    @Test
    void shouldSuccessfullyRestoreFromBackupWithHaConfigured() throws Exception {
        String configurationRestoreScriptPath = "/path/to/restore/script.sh";
        String configurationBackupPath = "src/test/resources/backup/";
        String backupFileName = "backup.tar";
        String currentHaNodeName = "node";
        String instanceIdentifier = "TEST";
        ExternalProcessRunner.ProcessResult processResult = new ExternalProcessRunner.ProcessResult(configurationRestoreScriptPath
                + " -b -i VEVTVA== -f " + FormatUtils.encodeStringToBase64(configurationBackupPath + backupFileName)
                + " -n " + FormatUtils.encodeStringToBase64(currentHaNodeName), 0, List.of()
        );

        configurationRestorationService.setConfigurationRestoreScriptPath(configurationRestoreScriptPath);
        when(backupRepository.getAbsoluteBackupFilePath(backupFileName))
                .thenReturn(Paths.get(configurationBackupPath + backupFileName));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(configurationBackupPath);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(instanceIdentifier);
        when(haConfigStatus.isHaConfigured()).thenReturn(true);
        when(haConfigStatus.getCurrentHaNodeName()).thenReturn(currentHaNodeName);
        when(externalProcessRunner.executeAndThrowOnFailure(
                configurationRestoreScriptPath, "-b",
                "-i", FormatUtils.encodeStringToBase64(instanceIdentifier),
                "-f", FormatUtils.encodeStringToBase64(configurationBackupPath + backupFileName),
                "-n", FormatUtils.encodeStringToBase64(currentHaNodeName))
        ).thenReturn(processResult);

        configurationRestorationService.restoreFromBackup(backupFileName);

        verify(auditDataHelper).putBackupFilename(Paths.get(configurationBackupPath + backupFileName));
        verify(eventPublisher).publishEvent(BackupRestoreEvent.START);
        verify(persistenceUtils).evictPoolConnections();
        verify(eventPublisher).publishEvent(BackupRestoreEvent.END);
        verify(apiKeyService).clearApiKeyCaches();
    }

    @Test
    void shouldSuccessfullyRestoreFromBackupWithoutHaConfigured() throws Exception {
        String configurationRestoreScriptPath = "/path/to/restore/script.sh";
        String configurationBackupPath = "src/test/resources/backup/";
        String backupFileName = "backup.tar";
        String instanceIdentifier = "TEST";
        ExternalProcessRunner.ProcessResult processResult = new ExternalProcessRunner.ProcessResult(configurationRestoreScriptPath
                + " -b -i VEVTVA== -f " + FormatUtils.encodeStringToBase64(configurationBackupPath + backupFileName),
                0, List.of()
        );

        configurationRestorationService.setConfigurationRestoreScriptPath(configurationRestoreScriptPath);
        when(backupRepository.getAbsoluteBackupFilePath(backupFileName))
                .thenReturn(Paths.get(configurationBackupPath + backupFileName));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(configurationBackupPath);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(instanceIdentifier);
        when(haConfigStatus.isHaConfigured()).thenReturn(false);
        when(externalProcessRunner.executeAndThrowOnFailure(
                configurationRestoreScriptPath, "-b",
                "-i", FormatUtils.encodeStringToBase64(instanceIdentifier),
                "-f", FormatUtils.encodeStringToBase64(configurationBackupPath + backupFileName))
        ).thenReturn(processResult);

        configurationRestorationService.restoreFromBackup(backupFileName);

        verify(auditDataHelper).putBackupFilename(Paths.get(configurationBackupPath + backupFileName));
        verify(eventPublisher).publishEvent(BackupRestoreEvent.START);
        verify(persistenceUtils).evictPoolConnections();
        verify(eventPublisher).publishEvent(BackupRestoreEvent.END);
        verify(apiKeyService).clearApiKeyCaches();
    }

    @Test
    void shouldFailWhenBackupFileDoesNotExist() {
        String configurationBackupPath = "src/test/resources/backup/";
        String backupFileName = "non-existent.tar";
        when(backupRepository.getAbsoluteBackupFilePath(backupFileName))
                .thenReturn(Paths.get(configurationBackupPath + backupFileName));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(configurationBackupPath);

        assertThrows(NotFoundException.class, () -> configurationRestorationService.restoreFromBackup(backupFileName));
        verify(auditDataHelper).putBackupFilename(Paths.get(configurationBackupPath + backupFileName));
    }

    @Test
    void shouldFailWhenExternalProcessFails() throws Exception {
        String configurationRestoreScriptPath = "/path/to/restore/script.sh";
        String configurationBackupPath = "src/test/resources/backup/";
        String backupFileName = "backup.tar";
        String instanceIdentifier = "TEST";

        configurationRestorationService.setConfigurationRestoreScriptPath(configurationRestoreScriptPath);
        when(backupRepository.getAbsoluteBackupFilePath(backupFileName))
                .thenReturn(Paths.get(configurationBackupPath + backupFileName));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(configurationBackupPath);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(instanceIdentifier);
        when(haConfigStatus.isHaConfigured()).thenReturn(false);
        when(externalProcessRunner.executeAndThrowOnFailure(
                configurationRestoreScriptPath, "-b",
                "-i", FormatUtils.encodeStringToBase64(instanceIdentifier),
                "-f", FormatUtils.encodeStringToBase64(configurationBackupPath + backupFileName))
        ).thenThrow(new ProcessFailedException("message"));

        assertThrows(ServiceException.class, () -> configurationRestorationService.restoreFromBackup(backupFileName));

        verify(auditDataHelper).putBackupFilename(Paths.get(configurationBackupPath + backupFileName));
        verify(eventPublisher).publishEvent(BackupRestoreEvent.START);
        verify(eventPublisher).publishEvent(BackupRestoreEvent.END);
        verify(apiKeyService).clearApiKeyCaches();
    }

}
