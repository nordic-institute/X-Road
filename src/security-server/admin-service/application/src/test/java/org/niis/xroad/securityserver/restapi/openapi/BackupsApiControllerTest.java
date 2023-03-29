/**
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.util.process.ProcessFailedException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.restapi.common.backup.dto.BackupFile;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.openapi.model.Backup;
import org.niis.xroad.securityserver.restapi.openapi.model.TokensLoggedOut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_RESTORATION_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_BACKUP_FILE;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_FILENAME;

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
        ResponseEntity<Set<Backup>> response = backupsApiController.getBackups();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<Backup> backups = response.getBody();
        assertEquals(2, backups.size());
        Backup firstBackup = response.getBody()
                .stream()
                .filter(backup -> backup.getCreatedAt().toString().equals(BACKUP_FILE_1_CREATED_AT))
                .findFirst()
                .orElse(null);
        Backup secondBackup = response.getBody()
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

        ResponseEntity<Set<Backup>> response = backupsApiController.getBackups();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<Backup> backups = response.getBody();
        assertEquals(0, backups.size());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void getBackupsException() {
        when(backupService.getBackupFiles()).thenThrow(new RuntimeException());

        try {
            ResponseEntity<Set<Backup>> response = backupsApiController.getBackups();
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

        Mockito.doThrow(new NotFoundException(BACKUP_FILE_NOT_FOUND)).when(backupService).deleteBackup(filename);

        try {
            ResponseEntity<Void> response = backupsApiController.deleteBackup(filename);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
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

        Mockito.doThrow(new NotFoundException(BACKUP_FILE_NOT_FOUND)).when(backupService).readBackupFile(filename);

        try {
            ResponseEntity<Resource> response = backupsApiController
                    .downloadBackup(filename);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void addBackup() throws Exception {
        BackupFile backupFile = new BackupFile(BACKUP_FILE_1_NAME, Instant.now().atOffset(ZoneOffset.UTC));
        when(backupGenerator.generateBackup()).thenReturn(backupFile);

        ResponseEntity<Backup> response = backupsApiController.addBackup();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(BACKUP_FILE_1_NAME, response.getBody().getFilename());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void addBackupFails() throws Exception {
        doThrow(new InterruptedException("")).when(backupGenerator).generateBackup();

        try {
            ResponseEntity<Backup> response = backupsApiController.addBackup();
            fail("should throw InternalServerErrorException");
        } catch (InternalServerErrorException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackup() throws Exception {
        BackupFile backupFile = new BackupFile(BACKUP_FILE_1_NAME, Instant.now().atOffset(ZoneOffset.UTC));

        when(backupService.uploadBackup(any(Boolean.class), any(String.class), any())).thenReturn(backupFile);

        ResponseEntity<Backup> response = backupsApiController.uploadBackup(true, mockMultipartFile);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(BACKUP_FILE_1_NAME, response.getBody().getFilename());
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackupWithInvalidFilename() throws Exception {
        Mockito.doThrow(new ValidationFailureException(INVALID_FILENAME)).when(backupService)
                .uploadBackup(any(Boolean.class), any(String.class), any());

        MockMultipartFile mockMultipartWithInvalidName = new MockMultipartFile("test", "/test.gpg",
                "multipart/form-data", "content".getBytes());
        try {
            ResponseEntity<Backup> response = backupsApiController.uploadBackup(true,
                    mockMultipartWithInvalidName);
            fail("should throw BadRequestException");
        } catch (ValidationFailureException expected) {
            Assert.assertEquals("invalid_filename", expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackupFileAlreadyExists() throws Exception {
        Mockito.doThrow(new UnhandledWarningsException(new WarningDeviation(""))).when(backupService)
                .uploadBackup(any(Boolean.class), any(String.class), any());

        try {
            ResponseEntity<Backup> response = backupsApiController.uploadBackup(false, mockMultipartFile);
            fail("should throw BadRequestException");
        } catch (BadRequestException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"BACKUP_CONFIGURATION"})
    public void uploadBackupFileInvalidBackupFile() throws Exception {
        Mockito.doThrow(new ServiceException(INVALID_BACKUP_FILE)).when(backupService)
                .uploadBackup(any(Boolean.class), any(String.class), any());

        try {
            ResponseEntity<Backup> response = backupsApiController.uploadBackup(false, mockMultipartFile);
            fail("should throw BadRequestException");
        } catch (ServiceException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackup() {
        ResponseEntity<TokensLoggedOut> response = backupsApiController
                .restoreBackup(BACKUP_FILE_1_NAME);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        TokensLoggedOut tokensLoggedOut = response.getBody();
        assertFalse(tokensLoggedOut.getHsmTokensLoggedOut());
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupWithLoggedOutTokens() {
        when(tokenService.hasHardwareTokens()).thenReturn(true);
        ResponseEntity<TokensLoggedOut> response = backupsApiController
                .restoreBackup(BACKUP_FILE_1_NAME);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        TokensLoggedOut tokensLoggedOut = response.getBody();
        assertTrue(tokensLoggedOut.getHsmTokensLoggedOut());
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupNotFound() throws Exception {
        Mockito.doThrow(new NotFoundException(BACKUP_FILE_NOT_FOUND)).when(configurationRestorationService).restoreFromBackup(any());
        try {
            backupsApiController.restoreBackup(BACKUP_FILE_1_NAME);
            fail("should throw BadRequestException");
        } catch (NotFoundException e) {
            Assert.assertEquals(DeviationCodes.ERROR_BACKUP_FILE_NOT_FOUND, e.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupInterrupted() throws Exception {
        doThrow(new InterruptedException()).when(configurationRestorationService).restoreFromBackup(any());
        try {
            backupsApiController.restoreBackup(BACKUP_FILE_1_NAME);
            fail("should throw InternalServerErrorException");
        } catch (InternalServerErrorException e) {
            // expected
        }
    }

    @Test
    @WithMockUser(authorities = {"RESTORE_CONFIGURATION"})
    public void restoreFromBackupFailed() throws Exception {
        Mockito.doThrow(new ServiceException(BACKUP_RESTORATION_FAILED,
                        new ProcessFailedException("process failed"), "restore failed"))
                .when(configurationRestorationService).restoreFromBackup(any());
        try {
            backupsApiController.restoreBackup(BACKUP_FILE_1_NAME);
            fail("should throw InternalServerErrorException");
        } catch (ServiceException e) {
            Assert.assertEquals(DeviationCodes.ERROR_BACKUP_RESTORE_PROCESS_FAILED, e.getErrorDeviation().getCode());
        }
    }
}
