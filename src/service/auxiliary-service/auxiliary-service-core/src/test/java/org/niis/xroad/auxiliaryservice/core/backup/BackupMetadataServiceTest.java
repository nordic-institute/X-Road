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

package org.niis.xroad.auxiliaryservice.core.backup;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.auxiliaryservice.core.config.BackupProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupMetadataServiceTest {

    private static final String CURRENT_FORMAT_VERSION = "v1";

    @TempDir
    private Path backupDir;

    @Mock
    private ExternalProcessRunner externalProcessRunner;
    @Mock
    private BackupProperties backupProperties;

    private BackupMetadataService service;

    @BeforeEach
    void beforeEach() throws IOException {
        Path formatVersionFile = backupDir.resolve("_backup_format_version");
        Files.writeString(formatVersionFile, CURRENT_FORMAT_VERSION);
        when(backupProperties.backupFormatVersionFilePath()).thenReturn(formatVersionFile.toString());
        lenient().when(backupProperties.createBackupMetadataPath()).thenReturn("irrelevant-script-path");

        service = new BackupMetadataService(externalProcessRunner, backupProperties);
        service.init();
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenMetadataFileMissing() {
        var backupPath = backupDir.resolve("backup.gpg");

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupCompatibleReturnsTrueWhenMetadataVersionMatches() throws IOException {
        var backupPath = backupDir.resolve("backup.gpg");
        Files.writeString(backupDir.resolve("backup.gpg.metadata"), CURRENT_FORMAT_VERSION);

        assertThat(service.isBackupCompatible(backupPath)).isTrue();
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenMetadataVersionDiffers() throws IOException {
        var backupPath = backupDir.resolve("backup.gpg");
        Files.writeString(backupDir.resolve("backup.gpg.metadata"), "v2");

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void deleteMetadataRemovesMetadataFileWhenPresent() throws IOException {
        var backupPath = backupDir.resolve("backup.gpg");
        var metadataPath = backupDir.resolve("backup.gpg.metadata");
        Files.writeString(metadataPath, CURRENT_FORMAT_VERSION);

        service.deleteMetadata(backupPath);

        assertThat(metadataPath).doesNotExist();
    }

    @Test
    void deleteMetadataIsNoopWhenMetadataFileAbsent() {
        var backupPath = backupDir.resolve("backup.gpg");

        assertThat(backupDir.resolve("backup.gpg.metadata")).doesNotExist();
        service.deleteMetadata(backupPath);
    }

    @Test
    void deleteMetadataSwallowsIOException() {
        var backupPath = backupDir.resolve("backup.gpg");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(new IOException("disk error"));

            assertThatCode(() -> service.deleteMetadata(backupPath)).doesNotThrowAnyException();
        }
    }

    @Test
    void determineBackupCompatibilityReturnsTrueWhenScriptSucceedsAndMetadataMatches() throws Exception {
        var backupPath = backupDir.resolve("backup.gpg");
        Files.writeString(backupDir.resolve("backup.gpg.metadata"), CURRENT_FORMAT_VERSION);
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 0, List.of("ok")));

        assertThat(service.determineBackupCompatibility(backupPath)).isTrue();
    }

    @Test
    void determineBackupCompatibilityReturnsTrueWhenScriptSucceedsAndMetadataDiffers() throws Exception {
        var backupPath = backupDir.resolve("backup.gpg");
        Files.writeString(backupDir.resolve("backup.gpg.metadata"), "v2");
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 0, List.of("ok")));

        assertThat(service.determineBackupCompatibility(backupPath)).isFalse();
    }

    @Test
    void determineBackupCompatibilityReturnsFalseWhenScriptExitsWithNonZeroCode() throws Exception {
        var backupPath = backupDir.resolve("backup.gpg");
        Files.writeString(backupDir.resolve("backup.gpg.metadata"), CURRENT_FORMAT_VERSION);
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 1, List.of("failure")));

        assertThat(service.determineBackupCompatibility(backupPath)).isFalse();
    }

    @Test
    void determineBackupCompatibilityReturnsFalseWhenProcessNotExecutable() throws Exception {
        var backupPath = backupDir.resolve("backup.gpg");
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new ProcessNotExecutableException(new IOException("boom")));

        assertThat(service.determineBackupCompatibility(backupPath)).isFalse();
    }

    @Test
    void determineBackupCompatibilityReturnsFalseWhenProcessFailed() throws Exception {
        var backupPath = backupDir.resolve("backup.gpg");
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new ProcessFailedException("failed"));

        assertThat(service.determineBackupCompatibility(backupPath)).isFalse();
    }

    @Test
    void determineBackupCompatibilityReturnsFalseAndKeepsInterruptFlagWhenInterrupted() throws Exception {
        var backupPath = backupDir.resolve("backup.gpg");
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new InterruptedException("interrupted"));

        assertThat(service.determineBackupCompatibility(backupPath)).isFalse();
    }

}
