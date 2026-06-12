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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.util.BackupUtils;
import ee.ria.xroad.common.util.EncoderUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.cs.admin.api.dto.BackupFile;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.BackupService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.repository.BackupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationBackupGeneratorImplTest {

    private static final String INSTANCE_ID = "DEV";
    private static final String HA_NODE_NAME = "node-1";
    private static final String BACKUP_SCRIPT_PATH = "/var/backup-script.sh";
    private static final String BACKUP_DIR = "/var/lib/xroad/backup/";
    private static final Instant BACKUP_TIMESTAMP = Instant.parse("2025-05-15T01:02:03Z");
    private static final String BACKUP_FILE_NAME = "conf_backup_v1_20250515-010203.gpg";
    private static final String BACKUP_FILE_PATH = BACKUP_DIR + BACKUP_FILE_NAME;

    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private BackupService backupService;
    @Mock
    private BackupRepository backupRepository;
    @Mock
    private ExternalProcessRunner externalProcessRunner;
    @Mock
    private AuditDataHelper auditDataHelper;

    @Captor
    private ArgumentCaptor<String[]> argsCaptor;

    @Test
    void generateBackup() throws Exception {
        TimeUtils.setClock(Clock.fixed(BACKUP_TIMESTAMP, ZoneOffset.UTC));

        var haConfigStatus = new HAConfigStatus(null, false);
        var generator = new ConfigurationBackupGeneratorImpl(
                systemParameterService, haConfigStatus, BACKUP_SCRIPT_PATH,
                backupService, backupRepository, externalProcessRunner, auditDataHelper);

        when(backupRepository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME))
                .thenReturn(Path.of(BACKUP_FILE_PATH));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(BACKUP_DIR);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_ID);
        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of("output")));

        var backupFile = new BackupFile(BACKUP_FILE_NAME, OffsetDateTime.now(ZoneOffset.UTC), true);
        when(backupService.getBackup(BACKUP_FILE_NAME)).thenReturn(Optional.of(backupFile));

        BackupFile result = generator.generateBackup();

        assertThat(result.getFilename()).isEqualTo(BACKUP_FILE_NAME);
        assertThat(result.getBackupCompatible()).isTrue();

        verify(auditDataHelper).putBackupFilename(Path.of(BACKUP_FILE_PATH));
        verify(externalProcessRunner).executeAndThrowOnFailure(anyString(), argsCaptor.capture());

        assertThat(argsCaptor.getValue()).containsExactly(
                "-b",
                "-i", EncoderUtils.encodeBase64(INSTANCE_ID),
                "-f", EncoderUtils.encodeBase64(BACKUP_FILE_PATH));
    }

    @Test
    void generateBackupHaConfigured() throws Exception {
        TimeUtils.setClock(Clock.fixed(BACKUP_TIMESTAMP, ZoneOffset.UTC));

        var haConfigStatus = new HAConfigStatus(HA_NODE_NAME, true);
        var generator = new ConfigurationBackupGeneratorImpl(
                systemParameterService, haConfigStatus, BACKUP_SCRIPT_PATH,
                backupService, backupRepository, externalProcessRunner, auditDataHelper);

        when(backupRepository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME))
                .thenReturn(Path.of(BACKUP_FILE_PATH));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(BACKUP_DIR);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_ID);
        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of("output")));

        var backupFile = new BackupFile(BACKUP_FILE_NAME, OffsetDateTime.now(ZoneOffset.UTC), true);
        when(backupService.getBackup(BACKUP_FILE_NAME)).thenReturn(Optional.of(backupFile));

        generator.generateBackup();

        verify(externalProcessRunner).executeAndThrowOnFailure(anyString(), argsCaptor.capture());

        assertThat(argsCaptor.getValue()).containsExactly(
                "-b",
                "-i", EncoderUtils.encodeBase64(INSTANCE_ID),
                "-n", EncoderUtils.encodeBase64(HA_NODE_NAME),
                "-f", EncoderUtils.encodeBase64(BACKUP_FILE_PATH));
    }

    @Test
    void generateBackupUsesVersionedFileName() throws Exception {
        TimeUtils.setClock(Clock.fixed(BACKUP_TIMESTAMP, ZoneOffset.UTC));

        var haConfigStatus = new HAConfigStatus(null, false);
        var generator = new ConfigurationBackupGeneratorImpl(
                systemParameterService, haConfigStatus, BACKUP_SCRIPT_PATH,
                backupService, backupRepository, externalProcessRunner, auditDataHelper);

        when(backupRepository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME))
                .thenReturn(Path.of(BACKUP_FILE_PATH));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(BACKUP_DIR);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_ID);
        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of("output")));

        var backupFile = new BackupFile(BACKUP_FILE_NAME, OffsetDateTime.now(ZoneOffset.UTC), true);
        when(backupService.getBackup(BACKUP_FILE_NAME)).thenReturn(Optional.of(backupFile));

        generator.generateBackup();

        assertThat(BACKUP_FILE_NAME).matches(BackupUtils::isBackupCompatible);
        verify(backupRepository).getAbsoluteBackupFilePath(BACKUP_FILE_NAME);
        verify(backupService).getBackup(BACKUP_FILE_NAME);
    }

    @Test
    void generateBackupThrowsWhenBackupFileMissing() throws Exception {
        TimeUtils.setClock(Clock.fixed(BACKUP_TIMESTAMP, ZoneOffset.UTC));

        var haConfigStatus = new HAConfigStatus(null, false);
        var generator = new ConfigurationBackupGeneratorImpl(
                systemParameterService, haConfigStatus, BACKUP_SCRIPT_PATH,
                backupService, backupRepository, externalProcessRunner, auditDataHelper);

        when(backupRepository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME))
                .thenReturn(Path.of(BACKUP_FILE_PATH));
        when(backupRepository.getConfigurationBackupPath()).thenReturn(BACKUP_DIR);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_ID);
        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of()));
        when(backupService.getBackup(BACKUP_FILE_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(generator::generateBackup)
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessageContaining("backup_generation_failed");
    }
}
