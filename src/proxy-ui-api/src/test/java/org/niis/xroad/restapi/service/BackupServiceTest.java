/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.dto.BackupFile;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_FILENAME;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_FILE_ALREADY_EXISTS;

/**
 * Test BackupService
 */
public class BackupServiceTest extends AbstractServiceTestContext {

    @Autowired
    BackupService backupService;

    @Autowired
    ExternalProcessRunner externalProcessRunner;

    @Autowired
    AuditDataHelper auditDataHelper;

    @Autowired
    ServerConfService serverConfService;

    private static final String BASE_DIR = "/tmp/backups/";

    private static final String BACKUP_FILE_1_NAME = "ss-automatic-backup-2020_02_19_031502.gpg";

    private static final String BACKUP_FILE_1_CREATED_AT = "2020-02-19T03:15:02.451Z";

    private static final Long BACKUP_FILE_1_CREATED_AT_MILLIS = 1582082102451L;

    private static final String BACKUP_FILE_2_NAME = "ss-automatic-backup-2020_02_12_031502.gpg";

    private static final String BACKUP_FILE_2_CREATED_AT = "2020-02-12T03:15:02.684Z";

    private static final Long BACKUP_FILE_2_CREATED_AT_MILLIS = 1581477302684L;

    private final MockMultipartFile mockMultipartFile = new MockMultipartFile("test", "content".getBytes());

    @Before
    public void setup() {
        List<File> files = new ArrayList<>(Arrays.asList(new File(BASE_DIR + BACKUP_FILE_1_NAME),
                new File(BASE_DIR + BACKUP_FILE_2_NAME)));

        when(backupRepository.getBackupFiles()).thenReturn(files);
        when(backupRepository.getCreatedAt(BACKUP_FILE_1_NAME)).thenReturn(
                new Date(BACKUP_FILE_1_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));
        when(backupRepository.getCreatedAt(BACKUP_FILE_2_NAME)).thenReturn(
                new Date(BACKUP_FILE_2_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));
        doAnswer(invocation -> {
            files.remove(0);
            return null;
        }).when(backupRepository).deleteBackupFile(BACKUP_FILE_1_NAME);
    }

    @Test
    public void getBackups() {
        List<BackupFile> backups = backupService.getBackupFiles();

        assertEquals(2, backups.size());
        assertEquals(BACKUP_FILE_1_NAME, backups.get(0).getFilename());
        assertEquals(BACKUP_FILE_1_CREATED_AT, backups.get(0).getCreatedAt().toString());
        assertEquals(BACKUP_FILE_2_NAME, backups.get(1).getFilename());
        assertEquals(BACKUP_FILE_2_CREATED_AT, backups.get(1).getCreatedAt().toString());
    }

    @Test
    public void getBackupsEmptyList() {
        when(backupRepository.getBackupFiles()).thenReturn(new ArrayList<>());

        List<BackupFile> backups = backupService.getBackupFiles();

        assertEquals(0, backups.size());
    }

    @Test
    public void getBackupsException() {
        when(backupRepository.getBackupFiles()).thenThrow(new RuntimeException());

        try {
            backupService.getBackupFiles();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }

    @Test
    public void deleteBackup() throws BackupFileNotFoundException {
        backupService.deleteBackup(BACKUP_FILE_1_NAME);
        assertEquals(1, backupService.getBackupFiles().size());
    }

    @Test
    public void deleteNonExistingBackup() {
        try {
            backupService.deleteBackup("test_file.tar");
            fail("should throw BackupFileNotFoundException");
        } catch (BackupFileNotFoundException expected) {
            // success
        }
    }

    @Test
    public void downloadBackup() throws Exception {
        byte[] bytes = "teststring".getBytes(StandardCharsets.UTF_8);
        when(backupRepository.readBackupFile(BACKUP_FILE_1_NAME)).thenReturn(bytes);

        byte[] response = backupService.readBackupFile(BACKUP_FILE_1_NAME);
        assertEquals(bytes.length, response.length);
    }

    @Test
    public void downloadNonExistingBackup() {
        try {
            backupService.readBackupFile("test_file.tar");
            fail("should throw BackupFileNotFoundException");
        } catch (BackupFileNotFoundException expected) {
            // success
        }
    }

    @Test
    public void addBackupFails() throws Exception {
        mockExternalProcessRunnerFail();
        try {
            backupService.generateBackup();
            fail("should throw DeviationAwareRuntimeException");
        } catch (DeviationAwareRuntimeException expected) {
            // success
        }
    }

    @Test
    public void uploadBackup() throws UnhandledWarningsException, InvalidBackupFileException, IOException,
            InvalidFilenameException {
        MultipartFile multipartFile = createMultipartFile(BACKUP_FILE_1_NAME);

        when(backupRepository.fileExists(BACKUP_FILE_1_NAME)).thenReturn(false);
        when(backupRepository.writeBackupFile(BACKUP_FILE_1_NAME, multipartFile.getBytes())).thenReturn(
                new Date(BACKUP_FILE_1_CREATED_AT_MILLIS).toInstant().atOffset(ZoneOffset.UTC));

        BackupFile backupFile = backupService.uploadBackup(true, multipartFile.getOriginalFilename(),
                multipartFile.getBytes());

        assertEquals(BACKUP_FILE_1_NAME, backupFile.getFilename());
        assertEquals(BACKUP_FILE_1_CREATED_AT, backupFile.getCreatedAt().toString());
    }

    @Test
    public void uploadBackupWithInvalidFilename() throws UnhandledWarningsException, InvalidBackupFileException,
            IOException {
        try {
            backupService.uploadBackup(true, mockMultipartFile.getOriginalFilename(),
                    mockMultipartFile.getBytes());
            fail("should throw InvalidFilenameException");
        } catch (InvalidFilenameException expected) {
            assertEquals(ERROR_INVALID_FILENAME, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    public void uploadBackupFileAlreadyExistsNoOverwrite() throws InvalidFilenameException,
            InvalidBackupFileException, IOException {
        MultipartFile multipartFile = createMultipartFile(BACKUP_FILE_1_NAME);
        when(backupRepository.fileExists(any(String.class))).thenReturn(true);
        try {
            backupService.uploadBackup(false, multipartFile.getOriginalFilename(),
                    multipartFile.getBytes());
            fail("should throw UnhandledWarningsException");
        } catch (UnhandledWarningsException expected) {
            assertEquals(WARNING_FILE_ALREADY_EXISTS, expected.getWarningDeviations().iterator().next().getCode());
        }
    }

    private MultipartFile createMultipartFile(String filename) throws IOException {
        return new MockMultipartFile(filename, filename, "multipart/form-data", "void".getBytes());
    }

    private void mockExternalProcessRunnerFail() {
        externalProcessRunner = new ExternalProcessRunner() {
            @Override
            public ProcessResult executeAndThrowOnFailure(String command, String... args) throws
                    ProcessNotExecutableException, ProcessFailedException, InterruptedException {
                throw new ProcessFailedException("");
            }
        };
        backupService = new BackupService(backupRepository, serverConfService, externalProcessRunner, auditDataHelper);
    }
}
