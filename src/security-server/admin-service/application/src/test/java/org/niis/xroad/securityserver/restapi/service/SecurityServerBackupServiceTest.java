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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.Test;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_RESTORATION_FAILED;

public class SecurityServerBackupServiceTest extends AbstractServiceTestContext {

    @Autowired
    private SecurityServerBackupService backupService;

    private final String tempBackupFilename = "backup.tar";

    @Test
    public void addBackupFails() {
        when(backupManagerRpcClient.createBackup(any(String.class)))
                .thenThrow(new InternalServerErrorException(""));

        assertThatThrownBy(() -> backupService.generateBackup())
                .isInstanceOf(DeviationAwareRuntimeException.class);
    }

    @Test
    public void restoreFromBackup() {
        backupService.restoreFromBackup(tempBackupFilename);
        verify(backupManagerRpcClient).restoreFromBackup(eq(tempBackupFilename), anyString());
    }

    @Test
    public void restoreFromNonExistingBackup() {
        doThrow(new NotFoundException(BACKUP_FILE_NOT_FOUND.build("no-backups-here.tar")))
                .when(backupManagerRpcClient).restoreFromBackup(anyString(), anyString());
        try {
            backupService.restoreFromBackup("no-backups-here.tar");
            fail("should have thrown an exception");
        } catch (NotFoundException e) {
            assertEquals(BACKUP_FILE_NOT_FOUND.code(), e.getErrorDeviation().code());
        }
    }

    @Test
    public void restoreFromBackupFail() throws Exception {
        doThrow(new InternalServerErrorException(BACKUP_RESTORATION_FAILED.build()))
                .when(backupManagerRpcClient).restoreFromBackup(anyString(), anyString());
        try {
            backupService.restoreFromBackup(tempBackupFilename);
            fail("should have thrown an exception");
        } catch (InternalServerErrorException e) {
            assertEquals(BACKUP_RESTORATION_FAILED.code(), e.getErrorDeviation().code());
        }
    }

    @Test
    public void restoreFromBackupNotExecutable() throws Exception {
        doThrow(new InternalServerErrorException(BACKUP_RESTORATION_FAILED.build()))
                .when(backupManagerRpcClient).restoreFromBackup(anyString(), anyString());
        try {
            backupService.restoreFromBackup(tempBackupFilename);
            fail("should have thrown an exception");
        } catch (InternalServerErrorException e) {
            assertEquals(BACKUP_RESTORATION_FAILED.code(), e.getErrorDeviation().code());
        }
    }

}
