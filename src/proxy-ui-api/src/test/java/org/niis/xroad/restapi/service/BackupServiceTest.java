/**
 * The MIT License
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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.BackupFile;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.repository.BackupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
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

/**
 * Test BackupService
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
@WithMockUser
public class BackupServiceTest {

    @MockBean
    private BackupRepository backupRepository;

    @MockBean
    private ExternalProcessRunner externalProcessRunner;

    @Autowired
    private BackupService backupService;

    private static final String BASE_DIR = "/tmp/backups/";

    private static final String BACKUP_FILE_1_NAME = "ss-automatic-backup-2020_02_19_031502.tar";

    private static final String BACKUP_FILE_1_CREATED_AT = "2020-02-19T03:15:02.451Z";

    private static final Long BACKUP_FILE_1_CREATED_AT_MILLIS = 1582082102451L;

    private static final String BACKUP_FILE_2_NAME = "ss-automatic-backup-2020_02_12_031502.tar";

    private static final String BACKUP_FILE_2_CREATED_AT = "2020-02-12T03:15:02.684Z";

    private static final Long BACKUP_FILE_2_CREATED_AT_MILLIS = 1581477302684L;

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
    public void getBackups() throws Exception {
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
        when(externalProcessRunner.executeAndThrowOnFailure(any(), any())).thenThrow(new ProcessFailedException(""));
        try {
            backupService.generateBackup();
            fail("should throw ProcessFailedException");
        } catch (DeviationAwareRuntimeException expected) {
            // success
        }
    }
}
