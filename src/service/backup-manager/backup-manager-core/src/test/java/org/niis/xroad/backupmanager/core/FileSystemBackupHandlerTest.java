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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.backupmanager.core.repository.BackupRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileSystemBackupHandlerTest {

    @Mock
    private ExternalProcessRunner externalProcessRunner;

    @Mock
    private BackupRepository backupRepository;

    private BackupManagerProperties backupManagerProperties;
    private FileSystemBackupHandler fileSystemBackupHandler;

    @BeforeEach
    void setUp() {
        backupManagerProperties = ConfigUtils.initConfiguration(BackupManagerProperties.class,
                Map.of("xroad.backup-manager.backup-script-path", "/var/backup-script.sh"));

        fileSystemBackupHandler = new FileSystemBackupHandler(externalProcessRunner, backupManagerProperties, backupRepository);
    }

    @Captor
    private ArgumentCaptor<String[]> argsCaptor;

    @Captor
    private ArgumentCaptor<String> cmdCaptor;

    @Test
    void performBackup() throws Exception {
        Instant timestamp = Instant.parse("2025-05-15T01:02:03Z");
        TimeUtils.setClock(Clock.fixed(timestamp, ZoneOffset.UTC));

        when(backupRepository.getAbsoluteBackupFilePath("conf_backup_20250515-010203.gpg"))
                .thenReturn(Path.of("/var/tmp/backup/conf_backup_20250515-010203.gpg"));

        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of("output")));
        when(backupRepository.listBackups()).thenReturn(List.of(
                new BackupItem("conf_backup_20250515-010203.gpg", timestamp)));

        fileSystemBackupHandler.performBackup("DEV/COM/1234/SS0");

        verify(externalProcessRunner).
                executeAndThrowOnFailure(cmdCaptor.capture(), argsCaptor.capture());

        assertThat(cmdCaptor.getValue()).isEqualTo(backupManagerProperties.backupScriptPath());
        assertThat(argsCaptor.getValue()).containsExactly("-s", "DEV/COM/1234/SS0",
                "-f", "/var/tmp/backup/conf_backup_20250515-010203.gpg");
    }

    @Test
    void performRestore() throws Exception {
        when(backupRepository.getAbsoluteBackupFilePath("conf_backup_20250515-010203.gpg"))
                .thenReturn(Path.of("/var/tmp/backup/conf_backup_20250515-010203.gpg"));

        when(externalProcessRunner.executeAndThrowOnFailure(anyString(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, List.of("output")));

        fileSystemBackupHandler.performRestore("conf_backup_20250515-010203.gpg", "DEV/COM/1234/SS0");

        verify(externalProcessRunner).
                executeAndThrowOnFailure(cmdCaptor.capture(), argsCaptor.capture());

        assertThat(cmdCaptor.getValue()).isEqualTo(backupManagerProperties.restoreScriptPath());
        assertThat(argsCaptor.getValue()).containsExactly("-b", "-s", FormatUtils.encodeStringToBase64("DEV/COM/1234/SS0"),
                "-f", FormatUtils.encodeStringToBase64("/var/tmp/backup/conf_backup_20250515-010203.gpg"));
    }

    @Test
    void deleteBackup() {
        String name = "conf_backup_20250515-010203.gpg";
        fileSystemBackupHandler.deleteBackup(name);
        verify(backupRepository).deleteBackup(name);
    }

    @Test
    void listBackups() {
        fileSystemBackupHandler.listBackups();
        verify(backupRepository).listBackups();
    }

    @Test
    void readBackup() {
        String name = "conf_backup_20250515-010203.gpg";
        fileSystemBackupHandler.readBackup(name);
        verify(backupRepository).readBackupFile(name);
    }

    @Test
    void saveBackup() {
        String name = "conf_backup_20250515-010203.gpg";
        byte[] content = new byte[]{1, 2, 3};
        fileSystemBackupHandler.saveBackup(name, content, false);
        verify(backupRepository).storeBackup(name, content);
    }

    @Test
    void saveBackupOverwriteExisting() {
        String name = "conf_backup_20250515-010203.gpg";
        byte[] content = new byte[]{1, 2, 3};

        when(backupRepository.listBackups()).thenReturn(List.of(new BackupItem(name, Instant.now())));
        assertThatThrownBy(() -> fileSystemBackupHandler.saveBackup(name, content, false))
                .isExactlyInstanceOf(CodedException.class)
                .hasMessageContaining("warning_file_already_exists: Backup with this name already exists");
        verifyNoMoreInteractions(backupRepository);

        fileSystemBackupHandler.saveBackup(name, content, true);
        verify(backupRepository).storeBackup(name, content);
    }

}
