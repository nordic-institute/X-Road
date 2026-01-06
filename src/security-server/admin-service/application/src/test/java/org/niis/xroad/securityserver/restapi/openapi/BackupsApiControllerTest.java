/*
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.util.TimeUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.core.exception.WarningDeviation;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.common.backup.dto.BackupFile;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokensLoggedOutDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import static java.time.Instant.ofEpochMilli;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_RESTORATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_BACKUP_FILE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_FILENAME;

/**
 * Test BackupsApiController
 * TODO tests should be reviewed as they're not really testing anything meaningful.
 */
public class BackupsApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    BackupsApiController backupsApiController;

    private static final String BACKUP_FILE_1_NAME = "ss-automatic-backup-2020_02_19_031502.gpg";

    private static final String BACKUP_FILE_1_CREATED_AT = "2020-02-19T03:15:02.451Z";

    private static final Long BACKUP_FILE_1_CREATED_AT_MILLIS = 1582082102451L;

    private static final String BACKUP_FILE_2_NAME = "ss-automatic-backup-2020_02_12_031502.gpg";

    private static final String BACKUP_FILE_2_CREATED_AT = "2020-02-12T03:15:02.684Z";

    private static final Long BACKUP_FILE_2_CREATED_AT_MILLIS = 1581477302684L;

    private final MockMultipartFile mockMultipartFile = new MockMultipartFile("test", "test.gpg",
            "multipart/form-data", "content".getBytes());

    @Before
    public void setup() {
        BackupFile bf1 = new BackupFile(BACKUP_FILE_1_NAME, ofEpochMilli(BACKUP_FILE_1_CREATED_AT_MILLIS).atOffset(ZoneOffset.UTC));
        BackupFile bf2 = new BackupFile(BACKUP_FILE_2_NAME, ofEpochMilli(BACKUP_FILE_2_CREATED_AT_MILLIS).atOffset(ZoneOffset.UTC));

        doReturn(new ArrayList<>(Arrays.asList(bf1, bf2))).when(backupService).getBackupFiles();
        doReturn(false).when(tokenService).hasHardwareTokens();
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void getBackups() throws Exception {
        ResponseEntity<Set<BackupDto>> response = backupsApiController.getBackups();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<BackupDto> backups = response.getBody();
        assertEquals(2, backups.size());
        BackupDto firstBackup = response.getBody()
                .stream()
                .filter(backup -> backup.getCreatedAt().toString().equals(BACKUP_FILE_1_CREATED_AT))
                .findFirst()
                .orElse(null);
        BackupDto secondBackup = response.getBody()
                .stream()
                .filter(backup -> backup.getCreatedAt().toString().equals(BACKUP_FILE_2_CREATED_AT))
                .findFirst()
                .orElse(null);
        assertEquals(BACKUP_FILE_1_NAME, firstBackup.getFilename());
        assertEquals(BACKUP_FILE_2_NAME, secondBackup.getFilename());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void getBackupsEmptyList() {
        when(backupService.getBackupFiles()).thenReturn(new ArrayList<>());

        ResponseEntity<Set<BackupDto>> response = backupsApiController.getBackups();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<BackupDto> backups = response.getBody();
        assertEquals(0, backups.size());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void getBackupsException() {
        when(backupService.getBackupFiles()).thenThrow(new RuntimeException());

        try {
            backupsApiController.getBackups();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void deleteBackup() {
        ResponseEntity<Void> response = backupsApiController
                .deleteBackup(BACKUP_FILE_1_NAME);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void deleteNonExistingBackup() throws NotFoundException {
        String filename = "test_file.gpg";

        doThrow(new NotFoundException(BACKUP_FILE_NOT_FOUND.build())).when(backupService).deleteBackup(filename);

        try {
            backupsApiController.deleteBackup(filename);
            fail("should throw ResourceNotFoundException");
        } catch (NotFoundException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void downloadBackup() throws Exception {
        byte[] bytes = "teststring".getBytes(StandardCharsets.UTF_8);
        when(backupService.readBackupFile(BACKUP_FILE_1_NAME)).thenReturn(bytes);

        ResponseEntity<Resource> response = backupsApiController
                .downloadBackup(BACKUP_FILE_1_NAME);
        Resource backup = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(bytes.length, backup.contentLength());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void downloadNonExistingBackup() throws NotFoundException {
        String filename = "test_file.tar";

        doThrow(new NotFoundException(BACKUP_FILE_NOT_FOUND.build())).when(backupService).readBackupFile(filename);

        try {
            backupsApiController.downloadBackup(filename);
            fail("should throw ResourceNotFoundException");
        } catch (NotFoundException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void addBackup() throws Exception {
        BackupFile backupFile = new BackupFile(BACKUP_FILE_1_NAME, TimeUtils.now().atOffset(ZoneOffset.UTC));
        when(backupGenerator.generateBackup()).thenReturn(backupFile);

        ResponseEntity<BackupDto> response = backupsApiController.addBackup();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(BACKUP_FILE_1_NAME, response.getBody().getFilename());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void addBackupFails() throws Exception {
        doThrow(new InterruptedException("")).when(backupGenerator).generateBackup();

        try {
            backupsApiController.addBackup();
            fail("should throw InternalServerErrorException");
        } catch (InternalServerErrorException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackup() throws Exception {
        BackupFile backupFile = new BackupFile(BACKUP_FILE_1_NAME, TimeUtils.now().atOffset(ZoneOffset.UTC));

        when(backupService.uploadBackup(any(Boolean.class), any(String.class), any())).thenReturn(backupFile);

        ResponseEntity<BackupDto> response = backupsApiController.uploadBackup(true, mockMultipartFile);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(BACKUP_FILE_1_NAME, response.getBody().getFilename());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackupWithInvalidFilename() throws Exception {
        doThrow(new BadRequestException(INVALID_FILENAME.build())).when(backupService)
                .uploadBackup(any(Boolean.class), any(String.class), any());

        MockMultipartFile mockMultipartWithInvalidName = new MockMultipartFile("test", "/test.gpg",
                "multipart/form-data", "content".getBytes());
        try {
            backupsApiController.uploadBackup(true, mockMultipartWithInvalidName);
            fail("should throw ValidationFailureException");
        } catch (BadRequestException expected) {
            Assert.assertEquals("invalid_filename", expected.getErrorDeviation().code());
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackupFileAlreadyExists() throws Exception {
        doThrow(new UnhandledWarningsException(new WarningDeviation(""))).when(backupService)
                .uploadBackup(any(Boolean.class), any(String.class), any());

        try {
            backupsApiController.uploadBackup(false, mockMultipartFile);
            fail("should throw ServiceException");
        } catch (BadRequestException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackupFileInvalidBackupFile() throws Exception {
        doThrow(new InternalServerErrorException(INVALID_BACKUP_FILE.build())).when(backupService)
                .uploadBackup(any(Boolean.class), any(String.class), any());

        try {
            ResponseEntity<BackupDto> response = backupsApiController.uploadBackup(false, mockMultipartFile);
            fail("should throw ServiceException");
        } catch (InternalServerErrorException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackup() {
        ResponseEntity<TokensLoggedOutDto> response = backupsApiController
                .restoreBackup(BACKUP_FILE_1_NAME);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        TokensLoggedOutDto tokensLoggedOut = response.getBody();
        assertFalse(tokensLoggedOut.getHsmTokensLoggedOut());
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupWithLoggedOutTokens() {
        when(tokenService.hasHardwareTokens()).thenReturn(true);
        ResponseEntity<TokensLoggedOutDto> response = backupsApiController
                .restoreBackup(BACKUP_FILE_1_NAME);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        TokensLoggedOutDto tokensLoggedOut = response.getBody();
        assertTrue(tokensLoggedOut.getHsmTokensLoggedOut());
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupNotFound() throws Exception {
        doThrow(new NotFoundException(BACKUP_FILE_NOT_FOUND.build())).when(configurationRestorationService).restoreFromBackup(any());
        try {
            backupsApiController.restoreBackup(BACKUP_FILE_1_NAME);
            fail("should throw ServiceException");
        } catch (InternalServerErrorException e) {
            Assert.assertEquals(BACKUP_FILE_NOT_FOUND.code(), e.getErrorDeviation().code());
        }
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupInterrupted() throws Exception {
        doThrow(new InterruptedException()).when(configurationRestorationService).restoreFromBackup(any());
        try {
            backupsApiController.restoreBackup(BACKUP_FILE_1_NAME);
            fail("should throw ServiceException");
        } catch (InternalServerErrorException e) {
            // expected
        }
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupFailed() throws Exception {
        doThrow(new InternalServerErrorException(BACKUP_RESTORATION_FAILED.build()))
                .when(configurationRestorationService).restoreFromBackup(any());
        try {
            backupsApiController.restoreBackup(BACKUP_FILE_1_NAME);
            fail("should throw ServiceException");
        } catch (InternalServerErrorException e) {
            Assert.assertEquals(BACKUP_RESTORATION_FAILED.code(), e.getErrorDeviation().code());
        }
    }
}
