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
package org.niis.xroad.cs.test.glue;

import feign.FeignException;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.BackupDto;
import org.niis.xroad.cs.test.api.FeignBackupsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static java.lang.ClassLoader.getSystemResourceAsStream;
import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class BackupStepDefs extends BaseStepDefs {
    @Autowired
    private FeignBackupsApi backupsApi;

    private ResponseEntity<BackupDto> newBackup;

    @Step("Backups are retrieved")
    public void getBackups() {
        try {
            var result = backupsApi.getBackups();
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
            putStepData(StepDataKey.RESPONSE_BODY, result.getBody());
            putStepData(StepDataKey.RESPONSE, result);
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Backups contains {} backup: {}")
    public void isContainingBackup(String backupName, String condition) {
        List<BackupDto> backups = getRequiredStepData(StepDataKey.RESPONSE_BODY);

        Boolean containsBackup = backups.stream()
                .filter(backup -> backup.getFilename().equals(backupName)).count() == 1;

        assertEquals(Boolean.valueOf(condition), containsBackup);
    }

    @Step("Backup {} is uploaded")
    public void uploadBackup(String fileName) throws IOException {
        MultipartFile backup = new MockMultipartFile("file", fileName, null,
                getSystemResourceAsStream("files/backups/" + fileName));
        try {
            var result = backupsApi.uploadBackup(false, backup);
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
            putStepData(StepDataKey.RESPONSE_BODY, result.getBody());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Backup is created")
    public void createBackup() {
        try {
            newBackup = backupsApi.addBackup();
            putStepData(StepDataKey.RESPONSE_STATUS, newBackup.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Backup {} is deleted")
    public void deleteBackup(String fileName) {
        try {
            var response = backupsApi.deleteBackup(fileName);
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("it contains data of new backup file")
    public void containsBackupData() {
        validate(newBackup)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();

        validate(getRequiredStepData(StepDataKey.RESPONSE))
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(newBackup.getBody().getFilename(), "body[0].filename"))
                .execute();
    }

    @Step("Backup named {} is downloaded")
    public void backupIsDownloaded(String backupFilename) {
        try {
            var result = backupsApi.downloadBackup(backupFilename);
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Central server is restored from {}")
    public void restoreFromBackup(String fileName) {
        var response = backupsApi.restoreBackup(fileName);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(isTrue("body.hsmTokensLoggedOut"))
                .execute();
    }
}
