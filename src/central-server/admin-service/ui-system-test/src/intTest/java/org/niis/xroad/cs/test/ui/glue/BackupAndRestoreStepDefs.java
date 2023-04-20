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
package org.niis.xroad.cs.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.BackupAndRestorePageObj;

import java.io.File;
import java.io.FileNotFoundException;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.appear;
import static org.assertj.core.api.Assertions.assertThat;

public class BackupAndRestoreStepDefs extends BaseUiStepDefs {

    private static final String DOWNLOADED_BACKUP_FILE = "DOWNLOADED_BACKUP_FILE";

    private final BackupAndRestorePageObj backupAndRestorePageObj = new BackupAndRestorePageObj();

    @Step("Configuration backup is created")
    public void configurationBackupIsCreated() {
        backupAndRestorePageObj.btnCreateConfigurationBackup().click();
        backupAndRestorePageObj.btnLoading().should(appear);

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration can be successfully restored from backup")
    public void configurationIsSuccessfullyRestoredFromBackup() {
        backupAndRestorePageObj.btnRestoreConfigurationFromBackup().click();
        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is downloaded")
    public void downloadConfigurationBackup() throws FileNotFoundException {
        final var backupFile = backupAndRestorePageObj.btnDownloadConfigurationBackup().download();
        assertThat(backupFile)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("gpg");
        scenarioContext.putStepData(DOWNLOADED_BACKUP_FILE, backupFile);
    }

    @Step("Configuration backup is uploaded")
    public void uploadConfigurationBackup() {
        backupAndRestorePageObj.btnUploadConfigurationBackup().shouldBe(Condition.enabled).click();

        final File backupFile = scenarioContext.getStepData(DOWNLOADED_BACKUP_FILE);
        backupAndRestorePageObj.inputConfigurationBackupBackupFile().uploadFile(backupFile);

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is overwritten")
    public void overwriteConfigurationBackup() {
        backupAndRestorePageObj.btnUploadConfigurationBackup().shouldBe(Condition.enabled).click();

        final File backupFile = scenarioContext.getStepData(DOWNLOADED_BACKUP_FILE);
        backupAndRestorePageObj.inputConfigurationBackupBackupFile().uploadFile(backupFile);

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is deleted")
    public void deleteConfigurationBackup() {
        backupAndRestorePageObj.btnDeleteConfigurationBackup().shouldBe(Condition.enabled).click();
        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup count is equal to {}")
    public void configurationBackupCountIsEqualTo(int count) {
        backupAndRestorePageObj.backupList().shouldHave(size(count));
    }
}
