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

package org.niis.xroad.auxiliaryservice.core.repository;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.auxiliaryservice.core.backup.BackupItem;
import org.niis.xroad.auxiliaryservice.core.backup.BackupMetadataService;
import org.niis.xroad.auxiliaryservice.core.backup.BackupValidator;
import org.niis.xroad.auxiliaryservice.core.backup.job.repository.BackupRepository;
import org.niis.xroad.auxiliaryservice.core.backup.job.repository.FileSystemBackupRepository;
import org.niis.xroad.auxiliaryservice.core.config.BackupProperties;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileSystemBackupRepositoryTest {

    @TempDir
    private Path backupDir;

    @Mock
    private BackupMetadataService backupMetadataService;

    private BackupProperties backupProperties;
    private BackupRepository backupRepository;

    @BeforeEach
    void beforeEach() {
        backupProperties = new BackupProperties(XRoadConfigBuilder.create()
                .register(AuxiliaryServiceConfigKeys.instance())
                .overrides(Map.of("xroad.auxiliary-service.backup.location", backupDir.toString()))
                .build());
        BackupValidator backupValidator = new BackupValidator(backupProperties);
        backupValidator.init();
        backupRepository = new FileSystemBackupRepository(backupProperties, backupValidator, backupMetadataService);
    }

    @Test
    void listBackups() {
        assertThat(backupRepository.listBackups()).isEmpty();

        createBackupFile("backup-1.gpg");
        createBackupFile("not-a-backup-file");
        createBackupFile("backup-2.gpg");

        assertThat(backupRepository.listBackups()).size().isEqualTo(2);
    }

    @Test
    void listBackupsReportsCompatibilityPerFile() {
        createBackupFile("compatible-backup.gpg");
        createBackupFile("incompatible-backup.gpg");

        when(backupMetadataService.isBackupCompatible(backupDir.resolve("compatible-backup.gpg"))).thenReturn(true);
        when(backupMetadataService.isBackupCompatible(backupDir.resolve("incompatible-backup.gpg"))).thenReturn(false);

        assertThat(backupRepository.listBackups())
                .extracting(BackupItem::name, BackupItem::compatible)
                .containsExactlyInAnyOrder(
                        tuple("compatible-backup.gpg", true),
                        tuple("incompatible-backup.gpg", false));
    }

    @Test
    void readBackupFile() {
        assertThatThrownBy(() -> backupRepository.readBackupFile("not-existing-file.gpg"))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("backup_file_not_found: not-existing-file.gpg");

        assertThatThrownBy(() -> backupRepository.readBackupFile("file-name-not-valid"))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("backup_file_not_found: file-name-not-valid");

        createBackupFile("backup.gpg");
        assertThat(backupRepository.readBackupFile("backup.gpg")).isNotNull();
    }

    @Test
    void deleteBackupThrowsWhenFilenameIsInvalid() {
        var name = "file-name-not-valid";

        assertThatThrownBy(() -> backupRepository.deleteBackup(name))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("backup_file_not_found: %s".formatted(name));
    }

    @Test
    void deleteBackupRemovesExistingFile() {
        createBackupFile("backup.gpg");
        var file = backupDir.resolve("backup.gpg");
        assertThat(file).exists();

        backupRepository.deleteBackup("backup.gpg");

        assertThat(file).doesNotExist();
    }

    @Test
    void deleteBackupSucceedsWhenFileDoesNotExist() {
        assertThatCode(() -> backupRepository.deleteBackup("missing.gpg")).doesNotThrowAnyException();
    }

    @Test
    void deleteBackupThrowsBackupDeletionFailedOnIOException() {
        createBackupFile("backup.gpg");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(new IOException("disk error"));

            assertThatThrownBy(() -> backupRepository.deleteBackup("backup.gpg"))
                    .isInstanceOf(XrdRuntimeException.class)
                    .hasMessageContaining("backup_deletion_failed: backup.gpg");
        }
    }

    @Test
    void getAbsoluteBackupFilePath() {
        var name = "../secret/folder/file.txt";
        assertThatThrownBy(() -> backupRepository.getAbsoluteBackupFilePath(name))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("invalid_filename: %s".formatted(name));

        createBackupFile("backup.gpg");
        assertThat(backupRepository.getAbsoluteBackupFilePath("backup.gpg").toString()).isEqualTo(
                backupProperties.location() + File.separator + "backup.gpg");
    }

    @Test
    void storeBackupFile() {
        String name = "hello-file.gpg";
        byte[] data = new byte[]{'h', 'e', 'l', 'l', 'o'};

        backupRepository.storeBackup(name, data);

        assertThat(backupRepository.readBackupFile(name)).isEqualTo(data);
    }

    @Test
    void storeBackupReportsCompatibleWhenDeterminedCompatible() {
        String name = "compatible-file.gpg";
        byte[] data = new byte[]{'h', 'e', 'l', 'l', 'o'};

        when(backupMetadataService.isBackupCompatible(backupDir.resolve(name))).thenReturn(true);

        BackupItem backupItem = backupRepository.storeBackup(name, data);

        assertThat(backupItem.compatible()).isTrue();
    }

    @Test
    void storeBackupReportsIncompatibleWhenDeterminedIncompatible() {
        String name = "incompatible-file.gpg";
        byte[] data = new byte[]{'h', 'e', 'l', 'l', 'o'};

        when(backupMetadataService.isBackupCompatible(backupDir.resolve(name))).thenReturn(false);

        BackupItem backupItem = backupRepository.storeBackup(name, data);

        assertThat(backupItem.compatible()).isFalse();
    }

    private void createBackupFile(String name) {
        Path backupFile = backupDir.resolve(name);
        try {
            Files.createFile(backupFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create backup file", e);
        }
    }

}
