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
package org.niis.xroad.restapi.common.backup.service;


import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.restapi.common.backup.dto.BackupFile;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.time.Instant.ofEpochMilli;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_FILE_ALREADY_EXISTS;

/**
 * Test BackupService
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    private static final String BACKUP_FILE_1_NAME = "ss-automatic-backup-2020_02_19_031502.gpg";

    private static final String BACKUP_FILE_1_CREATED_AT = "2020-02-19T03:15:02.451Z";

    private static final Long BACKUP_FILE_1_CREATED_AT_MILLIS = 1582082102451L;

    private static final String BACKUP_FILE_2_NAME = "ss-automatic-backup-2020_02_12_031502.gpg";

    private static final String BACKUP_FILE_2_CREATED_AT = "2020-02-12T03:15:02.684Z";

    private static final Long BACKUP_FILE_2_CREATED_AT_MILLIS = 1581477302684L;

    @Mock
    AuditDataHelper auditDataHelper;
    @Mock
    BackupValidator backupValidator;

    BackupRepository backupRepository;
    BackupService backupService;

    private final MockMultipartFile mockMultipartFile = new MockMultipartFile("test", "content".getBytes());


    @BeforeEach
    void setUp() {
        backupRepository = spy(new BackupRepository(backupValidator));
        backupService = new BackupService(backupRepository, auditDataHelper);
    }

    @Test
    void getBackups() {
        createBackupList();
        List<BackupFile> backups = backupService.getBackupFiles();

        assertThat(backups).hasSize(2);
        assertThat(backups.get(0).getFilename()).isEqualTo(BACKUP_FILE_1_NAME);
        assertThat(backups.get(0).getCreatedAt()).isEqualTo(BACKUP_FILE_1_CREATED_AT);
        assertThat(backups.get(1).getFilename()).isEqualTo(BACKUP_FILE_2_NAME);
        assertThat(backups.get(1).getCreatedAt()).isEqualTo(BACKUP_FILE_2_CREATED_AT);
    }

    @Test
    void getBackupsEmptyList() {
        when(backupRepository.getBackupFiles()).thenReturn(new ArrayList<>());

        List<BackupFile> backups = backupService.getBackupFiles();

        assertThat(backups).isEmpty();
    }

    @Test
    void getBackupsException() {
        when(backupRepository.getBackupFiles())
                .thenThrow(new RuntimeException());

        assertThatThrownBy(() -> backupService.getBackupFiles()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void deleteBackup() {
        List<BackupFile> files = createBackupList();
        doAnswer(invocation -> {
            files.remove(0);
            return null;
        }).when(backupRepository).deleteBackupFile(BACKUP_FILE_1_NAME);

        backupService.deleteBackup(BACKUP_FILE_1_NAME);
        assertThat(backupService.getBackupFiles()).hasSize(1);
    }

    @Test
    void deleteNonExistingBackup() {
        assertThatThrownBy(() -> backupService.deleteBackup("test_file.tar"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void downloadBackup() throws Exception {
        byte[] bytes = "teststring".getBytes(StandardCharsets.UTF_8);
        doReturn(bytes).when(backupRepository).readBackupFile(BACKUP_FILE_1_NAME);

        createBackupList();

        byte[] response = backupService.readBackupFile(BACKUP_FILE_1_NAME);
        assertThat(response).hasSize(bytes.length);
    }

    @Test
    void downloadNonExistingBackup() {
        assertThatThrownBy(() -> backupService.readBackupFile("test_file.tar"))
                .isInstanceOf(NotFoundException.class);
    }


    @Test
    void uploadBackup() throws Exception {
        MultipartFile multipartFile = createMultipartFile(BACKUP_FILE_1_NAME);

        doReturn(new Date(BACKUP_FILE_1_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC))
                .when(backupRepository).writeBackupFile(BACKUP_FILE_1_NAME, multipartFile.getBytes());

        BackupFile backupFile = backupService.uploadBackup(true, multipartFile.getOriginalFilename(),
                multipartFile.getBytes());

        assertThat(backupFile.getFilename()).isEqualTo(BACKUP_FILE_1_NAME);
        assertThat(backupFile.getCreatedAt()).isEqualTo(BACKUP_FILE_1_CREATED_AT);

        verify(auditDataHelper).putBackupFilename(any());
    }

    @Test
    void uploadBackupWithInvalidFilename() {
        assertThatThrownBy(() -> backupService.uploadBackup(true, mockMultipartFile.getOriginalFilename(),
                mockMultipartFile.getBytes()))
                .isInstanceOf(ValidationFailureException.class);
    }

    @Test
    void uploadBackupFileAlreadyExistsNoOverwrite() {
        MultipartFile multipartFile = createMultipartFile(BACKUP_FILE_1_NAME);

        doReturn(true).when(backupRepository).fileExists(anyString());

        assertThatThrownBy(() -> backupService.uploadBackup(false, multipartFile.getOriginalFilename(),
                multipartFile.getBytes()))
                .isInstanceOf(UnhandledWarningsException.class)
                .matches(exception -> {
                    UnhandledWarningsException expected = (UnhandledWarningsException) exception;
                    return WARNING_FILE_ALREADY_EXISTS.equals(expected.getWarningDeviations().iterator().next().getCode());
                });
    }

    private MultipartFile createMultipartFile(String filename) {
        return new MockMultipartFile(filename, filename, "multipart/form-data", "void".getBytes());
    }

    private List<BackupFile> createBackupList() {
        var file1 = new BackupFile(BACKUP_FILE_1_NAME, ofEpochMilli(BACKUP_FILE_1_CREATED_AT_MILLIS).atOffset(ZoneOffset.UTC));
        var file2 = new BackupFile(BACKUP_FILE_2_NAME, ofEpochMilli(BACKUP_FILE_2_CREATED_AT_MILLIS).atOffset(ZoneOffset.UTC));
        List<BackupFile> files = Lists.newArrayList(file1, file2);

        when(backupRepository.getBackupFiles()).thenReturn(files);

        return files;
    }
}
