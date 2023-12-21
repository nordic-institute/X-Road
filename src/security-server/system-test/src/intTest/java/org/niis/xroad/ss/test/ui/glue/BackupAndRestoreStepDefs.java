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
package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.BackupAndRestorePageObj;

import java.io.File;
import java.io.FileNotFoundException;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.focused;
import static com.codeborne.selenide.Condition.visible;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class BackupAndRestoreStepDefs extends BaseUiStepDefs {
    private final BackupAndRestorePageObj backupAndRestorePageObj = new BackupAndRestorePageObj();

    private File downloadedBackup;
    private String createdBackupName;

    @Step("Configuration backup is created")
    public void configurationBackupIsCreated() {
        backupAndRestorePageObj.btnCreateConfigurationBackup().click();
        backupAndRestorePageObj.btnLoading().should(appear);

        var message = commonPageObj.snackBar.success().shouldBe(visible).text();
        commonPageObj.snackBar.btnClose().click();

        createdBackupName = message.split(" ")[1];
    }

    @Step("Configuration can be successfully restored from backup")
    public void configurationIsSuccessfullyRestoredFromBackup() {
        backupAndRestorePageObj.btnRestoreConfigurationFromBackup().click();
        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is downloaded")
    public void downloadConfigurationBackup() throws FileNotFoundException {
        downloadedBackup = backupAndRestorePageObj.btnDownloadConfigurationBackup().download();
        assertThat(downloadedBackup)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("gpg");
    }

    @Step("Configuration backup is uploaded")
    public void uploadConfigurationBackup() {
        backupAndRestorePageObj.btnUploadConfigurationBackup().shouldBe(enabled).click();

        backupAndRestorePageObj.inputConfigurationBackupBackupFile().uploadFile(downloadedBackup);

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is overwritten")
    public void overwriteConfigurationBackup() {
        backupAndRestorePageObj.btnUploadConfigurationBackup().shouldBe(enabled).click();

        backupAndRestorePageObj.inputConfigurationBackupBackupFile().uploadFile(downloadedBackup);

        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is deleted")
    public void deleteConfigurationBackup() {
        backupAndRestorePageObj.btnDeleteConfigurationBackup().shouldBe(enabled).click();
        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup count is equal to {}")
    public void configurationBackupCountIsEqualTo(int count) {
        backupAndRestorePageObj.backupList().shouldHave(size(count));
    }

    @Step("Configuration backup filter is set to last created backup")
    public void configurationBackupCountIsEqualTo() {
        backupAndRestorePageObj.inputSearch().click();
        vTextField(backupAndRestorePageObj.inputSearch()).shouldBe(focused).setValue(createdBackupName);
    }
}
