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
package org.niis.xroad.cs.admin.core.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.core.config.BackupConfig;
import org.niis.xroad.cs.admin.core.service.BackupMetadataService;
import org.niis.xroad.cs.admin.core.service.BackupValidator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupRepositoryTest {

    private static final String BACKUP_FILE_NAME = "file.zip";

    @Mock
    private BackupValidator backupValidator;
    @Mock
    private BackupConfig backupConfig;
    @Mock
    private BackupMetadataService backupMetadataService;

    @TempDir
    Path backupDir;

    private BackupRepository repository;

    @BeforeEach
    void beforeEach() {
        lenient().when(backupConfig.getConfBackupPath()).thenReturn(backupDir.toAbsolutePath().toString());
        repository = new BackupRepository(backupValidator, backupConfig, backupMetadataService);
    }

    @Test
    void getBackupFilesReturnsEmptyListWhenBackupDirectoryDoesNotExist() {
        when(backupConfig.getConfBackupPath()).thenReturn(backupDir.resolve("missing").toString());

        assertThat(repository.getBackupFiles()).isEmpty();
    }

    @Test
    void getBackupFilesFiltersOutInvalidBackupFilenames() throws IOException {
        var validFile = backupDir.resolve("valid.tar");
        var invalidFile = backupDir.resolve("invalid.txt");
        Files.createFile(validFile);
        Files.createFile(invalidFile);

        when(backupValidator.isValidBackupFilename(any())).thenReturn(false);
        when(backupValidator.isValidBackupFilename("valid.tar")).thenReturn(true);

        var backupFiles = repository.getBackupFiles();

        assertThat(backupFiles).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getBackupFilesReturnsCompatibilityFlagFromMetadataService(boolean compatible) throws IOException {
        var validFile = backupDir.resolve("valid.tar");
        Files.createFile(validFile);

        when(backupValidator.isValidBackupFilename(any())).thenReturn(false);
        when(backupValidator.isValidBackupFilename("valid.tar")).thenReturn(true);
        when(backupMetadataService.isBackupCompatible(validFile)).thenReturn(compatible);

        var backupFiles = repository.getBackupFiles();

        assertThat(backupFiles).hasSize(1);
        assertThat(backupFiles.getFirst().getFilename()).isEqualTo("valid.tar");
        assertThat(backupFiles.getFirst().isCompatible()).isEqualTo(compatible);
        assertThat(backupFiles.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void getBackupFilesSetsCreatedAtToNullWhenAttributesCannotBeRead() throws IOException {
        var brokenLink = backupDir.resolve("broken.tar");
        Files.createSymbolicLink(brokenLink, backupDir.resolve("missing-target"));

        when(backupValidator.isValidBackupFilename(any())).thenReturn(false);
        when(backupValidator.isValidBackupFilename("broken.tar")).thenReturn(true);
        when(backupMetadataService.isBackupCompatible(brokenLink)).thenReturn(false);

        var backupFiles = repository.getBackupFiles();

        assertThat(backupFiles).hasSize(1);
        assertThat(backupFiles.getFirst().getFilename()).isEqualTo("broken.tar");
        assertThat(backupFiles.getFirst().getCreatedAt()).isNull();
    }

    @Test
    void getBackupFilesReturnsEmptyListWhenWalkThrowsIOException() {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.walk(any(Path.class), anyInt())).thenThrow(new IOException("disk error"));

            assertThat(repository.getBackupFiles()).isEmpty();
        }
    }

    @Test
    void deleteBackupFileThrowsNotFoundWhenFilenameIsInvalid() {
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(false);

        assertThatThrownBy(() -> repository.deleteBackupFile(BACKUP_FILE_NAME))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=backup_file_not_found, metadata=[%s]]".formatted(BACKUP_FILE_NAME));

        verify(backupMetadataService, never()).deleteMetadata(any());
    }

    @Test
    void deleteBackupFileDeletesFileAndMetadataWhenFileExists() throws IOException {
        var file = backupDir.resolve(BACKUP_FILE_NAME);
        Files.createFile(file);
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(true);

        repository.deleteBackupFile(BACKUP_FILE_NAME);

        assertThat(file).doesNotExist();
        verify(backupMetadataService).deleteMetadata(file);
    }

    @Test
    void deleteBackupFileDeletesMetadataEvenWhenBackupFileDoesNotExist() {
        var file = backupDir.resolve(BACKUP_FILE_NAME);
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(true);

        repository.deleteBackupFile(BACKUP_FILE_NAME);

        verify(backupMetadataService).deleteMetadata(file);
    }

    @Test
    void deleteBackupFileThrowsNotFoundOnIOException() throws IOException {
        var file = backupDir.resolve(BACKUP_FILE_NAME);
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(true);
        Files.createDirectory(file);
        Files.createFile(file.resolve("child.txt"));

        assertThatThrownBy(() -> repository.deleteBackupFile(BACKUP_FILE_NAME))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=backup_deletion_failed, metadata=[%s]]".formatted(BACKUP_FILE_NAME));

        verify(backupMetadataService, never()).deleteMetadata(any());
    }

    @Test
    void readBackupFileThrowsNotFoundWhenFilenameIsInvalid() {
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(false);

        assertThatThrownBy(() -> repository.readBackupFile(BACKUP_FILE_NAME))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=backup_file_not_found, metadata=[%s]]".formatted(BACKUP_FILE_NAME));
    }

    @Test
    void readBackupFileReturnsFileContents() throws IOException {
        var file = backupDir.resolve(BACKUP_FILE_NAME);
        byte[] content = "backup data".getBytes();
        Files.write(file, content);
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(true);

        assertThat(repository.readBackupFile(BACKUP_FILE_NAME)).isEqualTo(content);
    }

    @Test
    void readBackupFileThrowsNotFoundWhenFileDoesNotExist() {
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(true);

        assertThatThrownBy(() -> repository.readBackupFile(BACKUP_FILE_NAME))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=backup_file_not_found, metadata=[%s]]".formatted(BACKUP_FILE_NAME));
    }

    @Test
    void writeBackupFileThrowsBadRequestExceptionOnInvalidFilename() {
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(false);

        assertThatThrownBy(() -> repository.writeBackupFile(BACKUP_FILE_NAME, new byte[0]))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=invalid_filename, metadata=[%s]]".formatted(BACKUP_FILE_NAME));

        assertThat(backupDir.resolve(BACKUP_FILE_NAME)).doesNotExist();
    }

    @Test
    void writeBackupFileWritesContentAndReturnsBackupFile() throws IOException {
        var content = "content".getBytes();
        var file = backupDir.resolve(BACKUP_FILE_NAME);
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(true);
        when(backupMetadataService.determineBackupCompatibility(file)).thenReturn(true);

        var backupFile = repository.writeBackupFile(BACKUP_FILE_NAME, content);

        assertThat(Files.readAllBytes(file)).isEqualTo(content);
        assertThat(backupFile.getFilename()).isEqualTo(BACKUP_FILE_NAME);
        assertThat(backupFile.isCompatible()).isTrue();
        assertThat(backupFile.getCreatedAt()).isNotNull();
    }

    @Test
    void writeBackupFileThrowsInternalServerErrorExceptionWhenWriteFails() throws IOException {
        when(backupValidator.isValidBackupFilename(BACKUP_FILE_NAME)).thenReturn(true);
        Files.createDirectory(backupDir.resolve(BACKUP_FILE_NAME));

        assertThatThrownBy(() -> repository.writeBackupFile(BACKUP_FILE_NAME, "content".getBytes()))
                .isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void getConfigurationBackupPathAppendsTrailingSeparatorWhenMissing() {
        when(backupConfig.getConfBackupPath()).thenReturn("/tmp/backups");

        assertThat(repository.getConfigurationBackupPath()).isEqualTo("/tmp/backups" + File.separator);
    }

    @Test
    void getConfigurationBackupPathDoesNotDuplicateTrailingSeparator() {
        when(backupConfig.getConfBackupPath()).thenReturn("/tmp/backups" + File.separator);

        assertThat(repository.getConfigurationBackupPath()).isEqualTo("/tmp/backups" + File.separator);
    }

    @Test
    void getAbsoluteBackupFilePath() {
        assertThat(repository.getAbsoluteBackupFilePath(BACKUP_FILE_NAME)).isEqualTo(backupDir.resolve(BACKUP_FILE_NAME));
    }

    @Test
    void fileExists() throws IOException {
        assertThat(repository.fileExists(BACKUP_FILE_NAME)).isFalse();
        backupDir.resolve(BACKUP_FILE_NAME).toFile().createNewFile();
        assertThat(repository.fileExists(BACKUP_FILE_NAME)).isTrue();
    }

    @Test
    void fileExistsShouldFailOnRelativePathName() {
        var filename = "../secret/folder/file.txt";
        assertThatThrownBy(() -> repository.fileExists(filename))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=invalid_filename, metadata=[%s]]".formatted(filename));
    }



}
