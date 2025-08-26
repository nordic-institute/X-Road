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

package org.niis.xroad.backupmanager.core.repository;

import ee.ria.xroad.common.CodedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.backupmanager.core.BackupManagerProperties;
import org.niis.xroad.backupmanager.core.BackupValidator;
import org.niis.xroad.common.properties.ConfigUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemBackupRepositoryTest {

    @TempDir
    private Path backupDir;

    private BackupManagerProperties backupManagerProperties;
    private BackupValidator backupValidator;
    private BackupRepository backupRepository;

    @BeforeEach
    void beforeEach() {
        backupManagerProperties = ConfigUtils.initConfiguration(BackupManagerProperties.class,
                Map.of("xroad.backup-manager.backup-location", backupDir.toString()));
        backupValidator = new BackupValidator(backupManagerProperties);
        backupValidator.init();
        backupRepository = new FileSystemBackupRepository(backupManagerProperties, backupValidator);
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
    void readBackupFile() {
        assertThatThrownBy(() -> backupRepository.readBackupFile("not-existing-file.gpg"))
                .isInstanceOf(CodedException.class)
                .hasMessage("backup_file_not_found: not-existing-file.gpg");

        assertThatThrownBy(() -> backupRepository.readBackupFile("file-name-not-valid"))
                .isInstanceOf(CodedException.class)
                .hasMessage("backup_file_not_found: file-name-not-valid");

        createBackupFile("backup.gpg");
        assertThat(backupRepository.readBackupFile("backup.gpg")).isNotNull();
    }

    @Test
    void getAbsoluteBackupFilePath() {
        var name = "../secret/folder/file.txt";
        assertThatThrownBy(() -> backupRepository.getAbsoluteBackupFilePath(name))
                .isInstanceOf(CodedException.class)
                .hasMessage("invalid_filename: %s".formatted(name));

        createBackupFile("backup.gpg");
        assertThat(backupRepository.getAbsoluteBackupFilePath("backup.gpg").toString()).isEqualTo(
                backupManagerProperties.backupLocation() + File.separator + "backup.gpg");
    }

    @Test
    void storeBackupFile() {
        String name = "hello-file.gpg";
        byte[] data = new byte[]{'h', 'e', 'l', 'l', 'o'};

        backupRepository.storeBackup(name, data);

        assertThat(backupRepository.readBackupFile(name)).isEqualTo(data);
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
