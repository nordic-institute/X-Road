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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.restapi.common.backup.service.ConfigurationRestorationService;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.time.OffsetDateTime;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

public class SecurityServerConfigurationRestorationServiceTest extends AbstractServiceTestContext {
    private static final String MOCK_SUCCESS_SCRIPT = "src/test/resources/script/success.sh";
    private static final String MOCK_FAIL_SCRIPT = "src/test/resources/script/fail.sh";

    @Autowired
    ConfigurationRestorationService configurationRestorationService;

    @Autowired
    TokenService tokenService;

    private NotificationService notificationService;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String tempBackupFilename = "backup.tar";

    @Before
    public void setup() throws Exception {
        configurationRestorationService.setConfigurationRestoreScriptPath(MOCK_SUCCESS_SCRIPT);
        File tempBackupFile = tempFolder.newFile(tempBackupFilename);
        when(backupRepository.getConfigurationBackupPath()).thenReturn(tempBackupFile.getParent() + File.separator);
        notificationService = new NotificationService(globalConfFacade, tokenService) {
            @Override
            public synchronized OffsetDateTime getBackupRestoreRunningSince() {
                return null;
            }
        };
    }

    @Test
    public void restoreFromBackup() throws Exception {
        configurationRestorationService.restoreFromBackup(tempBackupFilename);
        assertTrue(true);
    }

    @Test
    public void restoreFromNonExistingBackup() throws Exception {
        try {
            configurationRestorationService.restoreFromBackup("no-backups-here.tar");
            fail("should have thrown an exception");
        } catch (NotFoundException e) {
            Assert.assertEquals(DeviationCodes.ERROR_BACKUP_FILE_NOT_FOUND, e.getErrorDeviation().getCode());
        }
    }

    @Test
    public void restoreFromBackupFail() throws Exception {
        configurationRestorationService.setConfigurationRestoreScriptPath(MOCK_FAIL_SCRIPT);
        try {
            configurationRestorationService.restoreFromBackup(tempBackupFilename);
            fail("should have thrown an exception");
        } catch (ServiceException e) {
            Assert.assertEquals(DeviationCodes.ERROR_BACKUP_RESTORE_PROCESS_FAILED, e.getErrorDeviation().getCode());
        }
    }

    @Test
    public void restoreFromBackupNotExecutable() throws Exception {
        configurationRestorationService.setConfigurationRestoreScriptPath("path/to/nowhere.sh");
        try {
            configurationRestorationService.restoreFromBackup(tempBackupFilename);
            fail("should have thrown an exception");
        } catch (ServiceException e) {
            Assert.assertEquals(DeviationCodes.ERROR_BACKUP_RESTORE_PROCESS_FAILED, e.getErrorDeviation().getCode());
        }
    }
}
