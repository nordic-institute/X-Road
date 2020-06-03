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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.repository.BackupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class RestoreServiceTest {

    @Autowired
    private RestoreService configurationRestorer;
    @MockBean
    private BackupRepository backupRepository;
    @MockBean
    private NotificationService notificationService;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String tempBackupFilename = "backup.tar";

    @Before
    public void setup() throws Exception {
        configurationRestorer.setConfigurationRestoreScriptPath(ExternalProcessRunnerTest.MOCK_SUCCESS_SCRIPT);
        configurationRestorer.setConfigurationRestoreScriptArgs(ExternalProcessRunnerTest.SCRIPT_ARGS);
        File tempBackupFile = tempFolder.newFile(tempBackupFilename);
        when(backupRepository.getConfigurationBackupPath()).thenReturn(tempBackupFile.getParent() + File.separator);
        when(notificationService.getBackupRestoreRunningSince()).thenReturn(null);
    }

    @Test
    public void restoreFromBackup() throws Exception {
        configurationRestorer.restoreFromBackup(tempBackupFilename);
        assertTrue(true);
    }

    @Test
    public void restoreFromNonExistingBackup() throws Exception {
        try {
            configurationRestorer.restoreFromBackup("no-backups-here.tar");
            fail("should have thrown an exception");
        } catch (BackupFileNotFoundException e) {
            assertEquals(BackupFileNotFoundException.ERROR_BACKUP_FILE_NOT_FOUND, e.getErrorDeviation().getCode());
        }
    }

    @Test
    public void restoreFromBackupFail() throws Exception {
        configurationRestorer.setConfigurationRestoreScriptPath(ExternalProcessRunnerTest.MOCK_FAIL_SCRIPT);
        try {
            configurationRestorer.restoreFromBackup(tempBackupFilename);
            fail("should have thrown an exception");
        } catch (RestoreProcessFailedException e) {
            assertEquals(RestoreProcessFailedException.RESTORE_PROCESS_FAILED, e.getErrorDeviation().getCode());
        }
    }

    @Test
    public void restoreFromBackupNotExecutable() throws Exception {
        configurationRestorer.setConfigurationRestoreScriptPath("path/to/nowhere.sh");
        try {
            configurationRestorer.restoreFromBackup(tempBackupFilename);
            fail("should have thrown an exception");
        } catch (RestoreProcessFailedException e) {
            assertEquals(RestoreProcessFailedException.RESTORE_PROCESS_FAILED, e.getErrorDeviation().getCode());
        }
    }
}
