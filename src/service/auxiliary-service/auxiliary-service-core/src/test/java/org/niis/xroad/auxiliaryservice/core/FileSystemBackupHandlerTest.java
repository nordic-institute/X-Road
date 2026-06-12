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

package org.niis.xroad.auxiliaryservice.core;

import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.auxiliaryservice.core.backup.BackupItem;
import org.niis.xroad.auxiliaryservice.core.backup.FileSystemBackupHandler;
import org.niis.xroad.auxiliaryservice.core.backup.job.repository.BackupRepository;
import org.niis.xroad.auxiliaryservice.core.config.BackupProperties;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.restapi.util.FormatUtils;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileSystemBackupHandlerTest {

    private static final String SECURITY_SERVER_ID = "DEV/COM/1234/SS0";
    private static final String BACKUP_FILE_NAME = "conf_backup_v1_20250515-010203.gpg";
    private static final String BACKUP_FILE_PATH = "/var/tmp/backup/" + BACKUP_FILE_NAME;
    private static final Instant BACKUP_TIMESTAMP = Instant.parse("2025-05-15T01:02:03Z");

    @Mock
    private ExternalProcessRunner externalProcessRunner;

    @Mock
    private BackupRepository backupRepository;

    private BackupProperties backupProperties;
    private FileSystemBackupHandler fileSystemBackupHandler;

    @BeforeEach
    void setUp() {
        backupProperties = ConfigUtils.initConfiguration(BackupProperties.class,
                Map.of("xroad.auxiliary-service.backup.script-path", "/var/backup-script.sh"));

        fileSystemBackupHandler = new FileSystemBackupHandler(externalProcessRunner, backupProperties, backupRepository);
    }

    @Captor
    private ArgumentCaptor<String[]> argsCaptor;

    @Captor
    private ArgumentCaptor<String> cmdCaptor;

    @Test
    void performBackup() throws Exception {
        TimeUtils.setClock(Clock.fixed(BACKUP_TIMESTAMP, ZoneOffset.UTC));

        when(backupRepository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME))
                .thenReturn(Path.of(BACKUP_FILE_PATH));

        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of("output")));
        when(backupRepository.listBackups()).thenReturn(List.of(
                new BackupItem(BACKUP_FILE_NAME, BACKUP_TIMESTAMP, true)));

        BackupItem backupItem = fileSystemBackupHandler.performBackup(SECURITY_SERVER_ID);
        assertEquals(BACKUP_FILE_NAME, backupItem.name());
        assertTrue(backupItem.backupCompatible());

        verify(externalProcessRunner).
                executeAndThrowOnFailure(cmdCaptor.capture(), argsCaptor.capture());

        assertThat(cmdCaptor.getValue()).isEqualTo(backupProperties.scriptPath());
        assertThat(argsCaptor.getValue()).containsExactly("-s", SECURITY_SERVER_ID,
                "-f", BACKUP_FILE_PATH);
    }

    @Test
    void performRestore() throws Exception {
        when(backupRepository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME))
                .thenReturn(Path.of(BACKUP_FILE_PATH));

        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of("output")));

        fileSystemBackupHandler.performRestore(BACKUP_FILE_NAME, SECURITY_SERVER_ID);

        verify(externalProcessRunner).
                executeAndThrowOnFailure(cmdCaptor.capture(), argsCaptor.capture());

        assertThat(cmdCaptor.getValue()).isEqualTo(backupProperties.restoreScriptPath());
        assertThat(argsCaptor.getValue()).containsExactly("-b", "-s", FormatUtils.encodeStringToBase64(SECURITY_SERVER_ID),
                "-f", FormatUtils.encodeStringToBase64(BACKUP_FILE_PATH));
    }

    @Test
    void deleteBackup() {
        fileSystemBackupHandler.deleteBackup(BACKUP_FILE_NAME);
        verify(backupRepository).deleteBackup(BACKUP_FILE_NAME);
    }

    @Test
    void listBackups() {
        fileSystemBackupHandler.listBackups();
        verify(backupRepository).listBackups();
    }

    @Test
    void readBackup() {
        fileSystemBackupHandler.readBackup(BACKUP_FILE_NAME);
        verify(backupRepository).readBackupFile(BACKUP_FILE_NAME);
    }

    @Test
    void saveBackup() {
        byte[] content = new byte[]{1, 2, 3};
        fileSystemBackupHandler.saveBackup(BACKUP_FILE_NAME, content, false);
        verify(backupRepository).storeBackup(BACKUP_FILE_NAME, content);
    }

    @Test
    void saveBackupOverwriteExisting() {
        byte[] content = new byte[]{1, 2, 3};

        when(backupRepository.listBackups()).thenReturn(List.of(new BackupItem(BACKUP_FILE_NAME, Instant.now(), true)));
        assertThatThrownBy(() -> fileSystemBackupHandler.saveBackup(BACKUP_FILE_NAME, content, false))
                .isExactlyInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("file_already_exists: Backup with this name already exists");
        verifyNoMoreInteractions(backupRepository);

        fileSystemBackupHandler.saveBackup(BACKUP_FILE_NAME, content, true);
        verify(backupRepository).storeBackup(BACKUP_FILE_NAME, content);
    }

}
