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
package org.niis.xroad.cs.admin.core.service;

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
import org.niis.xroad.cs.admin.core.config.BackupConfig;
import org.niis.xroad.cs.admin.core.config.BackupMetadataProperties;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupMetadataServiceTest {

    private static final String CURRENT_FORMAT_VERSION = "v1";

    private static final String METADATA_JSON_TEMPLATE = "{\"version\":\"%s\",\"server_type\":\"central\"}";

    @TempDir
    private Path backupDir;

    @TempDir
    private Path outsideDir;

    @Mock
    private ExternalProcessRunner externalProcessRunner;
    @Mock
    private BackupMetadataProperties backupMetadataProperties;
    @Mock
    private BackupConfig backupConfig;

    private BackupMetadataService service;

    @BeforeEach
    void beforeEach() throws IOException {
        Path formatVersionFile = backupDir.resolve("_backup_format_version");
        Files.writeString(formatVersionFile, CURRENT_FORMAT_VERSION);
        when(backupMetadataProperties.getBackupFormatVersionFilePath()).thenReturn(formatVersionFile.toString());
        lenient().when(backupMetadataProperties.getCreateBackupMetadataPath()).thenReturn("irrelevant-script-path");
        lenient().when(backupConfig.getConfBackupPath()).thenReturn(backupDir.toString());

        service = new BackupMetadataService(externalProcessRunner, backupMetadataProperties, backupConfig);
        service.afterPropertiesSet();
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenMetadataFileMissing() {
        var backupPath = backupDir.resolve("backup.tar");

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupCompatibleReturnsTrueWhenMetadataVersionMatches() throws IOException {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), metadataJson(CURRENT_FORMAT_VERSION));

        assertThat(service.isBackupCompatible(backupPath)).isTrue();
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenMetadataVersionDiffers() throws IOException {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), metadataJson("v2"));

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenServerTypeDiffers() throws IOException {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"),
                "{\"version\":\"" + CURRENT_FORMAT_VERSION + "\",\"server_type\":\"security\"}");

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenMetadataFileIsNotValidJson() throws IOException {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), "not-valid-json");

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenReadingMetadataThrowsException() throws IOException {
        var backupPath = backupDir.resolve("backup.tar");
        var metadataPath = backupDir.resolve("backup.tar.metadata");
        Files.writeString(metadataPath, metadataJson(CURRENT_FORMAT_VERSION));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(metadataPath)).thenReturn(true);
            filesMock.when(() -> Files.readString(metadataPath)).thenThrow(new IOException("disk error"));

            assertThat(service.isBackupCompatible(backupPath)).isFalse();
        }
    }

    @Test
    void isBackupCompatibleReturnsFalseWhenCurrentFormatVersionCouldNotBeRead() throws IOException {
        when(backupMetadataProperties.getBackupFormatVersionFilePath())
                .thenReturn(backupDir.resolve("missing-format-version").toString());
        service = new BackupMetadataService(externalProcessRunner, backupMetadataProperties, backupConfig);
        service.afterPropertiesSet();

        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), metadataJson(""));

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void deleteMetadataRemovesMetadataFileWhenPresent() throws IOException {
        var backupPath = backupDir.resolve("backup.tar");
        var metadataPath = backupDir.resolve("backup.tar.metadata");
        Files.writeString(metadataPath, metadataJson(CURRENT_FORMAT_VERSION));

        service.deleteMetadata(backupPath);

        assertThat(metadataPath).doesNotExist();
    }

    @Test
    void deleteMetadataIsNoopWhenMetadataFileAbsent() {
        var backupPath = backupDir.resolve("backup.tar");

        assertThat(backupDir.resolve("backup.tar.metadata")).doesNotExist();
        service.deleteMetadata(backupPath);
    }

    @Test
    void deleteMetadataSwallowsIOException() {
        var backupPath = backupDir.resolve("backup.tar");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(new IOException("disk error"));

            assertThatCode(() -> service.deleteMetadata(backupPath)).doesNotThrowAnyException();
        }
    }

    @Test
    void createMetadataLeavesBackupCompatibleWhenScriptSucceedsAndMetadataMatches() throws Exception {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), metadataJson(CURRENT_FORMAT_VERSION));
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 0, List.of("ok")));

        service.createMetadata(backupPath);

        assertThat(service.isBackupCompatible(backupPath)).isTrue();
    }

    @Test
    void createMetadataLeavesBackupIncompatibleWhenScriptSucceedsAndMetadataDiffers() throws Exception {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), metadataJson("v2"));
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 0, List.of("ok")));

        service.createMetadata(backupPath);

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void createMetadataLeavesStaleMetadataUntouchedWhenScriptExitsWithNonZeroCode() throws Exception {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), metadataJson(CURRENT_FORMAT_VERSION));
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 1, List.of("failure")));

        service.createMetadata(backupPath);

        assertThat(service.isBackupCompatible(backupPath)).isTrue();
    }

    @Test
    void createMetadataDeletesStaleMetadataWhenProcessNotExecutable() throws Exception {
        var backupPath = backupDir.resolve("backup.tar");
        Files.writeString(backupDir.resolve("backup.tar.metadata"), metadataJson(CURRENT_FORMAT_VERSION));
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new ProcessNotExecutableException(new IOException("boom")));

        service.createMetadata(backupPath);

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void createMetadataLeavesBackupIncompatibleWhenProcessFailed() throws Exception {
        var backupPath = backupDir.resolve("backup.tar");
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new ProcessFailedException("failed"));

        service.createMetadata(backupPath);

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void createMetadataKeepsInterruptFlagWhenInterrupted() throws Exception {
        var backupPath = backupDir.resolve("backup.tar");
        when(externalProcessRunner.execute(anyString(), anyString()))
                .thenThrow(new InterruptedException("interrupted"));

        service.createMetadata(backupPath);

        assertThat(service.isBackupCompatible(backupPath)).isFalse();
    }

    @Test
    void deleteMetadataDoesNotDeleteFileOutsideBackupDir() throws IOException {
        var outsidePath = outsideDir.resolve("outside.tar");
        var outsideMetadata = outsideDir.resolve("outside.tar.metadata");
        Files.writeString(outsideMetadata, metadataJson(CURRENT_FORMAT_VERSION));

        service.deleteMetadata(outsidePath);

        assertThat(outsideMetadata).exists();
    }

    @Test
    void createMetadataDoesNotRunScriptForPathOutsideBackupDir() {
        var outsidePath = outsideDir.resolve("outside.tar");

        service.createMetadata(outsidePath);

        verifyNoInteractions(externalProcessRunner);
    }

    private static String metadataJson(String version) {
        return String.format(METADATA_JSON_TEMPLATE, version);
    }
}
