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

import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.ui.page.BackupAndRestorePageObj;
import org.niis.xroad.ss.test.ui.page.LoginPageObj;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.DB_SERVERCONF_INIT;
import static org.niis.xroad.test.framework.core.ui.utils.VuetifyHelper.vTextField;

@SuppressWarnings(value = {"SpringJavaInjectionPointsAutowiringInspection"})
public class BackupAndRestoreStepDefs extends BaseUiStepDefs {
    public static final int WAIT_FOR_RESTART = 30;
    private final BackupAndRestorePageObj backupAndRestorePageObj = new BackupAndRestorePageObj();
    private final LoginPageObj loginPageObj = new LoginPageObj();

    private File downloadedBackup;
    private String createdBackupName;

    @Autowired
    private SsSystemTestContainerSetup systemTestContainerSetup;

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

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service restarting dialog is displayed")
    public void serviceRestartingDialogIsDisplayed() {
        backupAndRestorePageObj.restartingDialog().shouldBe(visible, Duration.ofSeconds(WAIT_FOR_RESTART));
    }

    @Step("Login page is displayed after service restart")
    @SuppressWarnings("checkstyle:MagicNumber")
    public void loginPageIsDisplayedAfterServiceRestart() {
        // rerun serverconf-init db container after restore
        systemTestContainerSetup.start(DB_SERVERCONF_INIT, false);

        loginPageObj.inputUsername().shouldBe(visible, Duration.ofSeconds(120));
    }

    @Step("Configuration backup is downloaded")
    public void downloadConfigurationBackup() {
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

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is overwritten")
    public void overwriteConfigurationBackup() {
        backupAndRestorePageObj.btnUploadConfigurationBackup().shouldBe(enabled).click();

        backupAndRestorePageObj.inputConfigurationBackupBackupFile().uploadFile(downloadedBackup);

        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is deleted")
    public void deleteConfigurationBackup() {
        backupAndRestorePageObj.btnDeleteConfigurationBackup().shouldBe(enabled).click();
        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup count is equal to {}")
    public void configurationBackupCountIsEqualTo(int count) {
        backupAndRestorePageObj.backupList().shouldHave(size(count));
    }

    @Step("Configuration backup filter is set to last created backup")
    public void configurationBackupCountIsEqualTo() {
        vTextField(backupAndRestorePageObj.inputSearch()).shouldBe(enabled).setValue(createdBackupName);
    }

    @Step("Configuration backup is shown as compatible")
    public void configurationBackupIsShownAsCompatible() {
        backupAndRestorePageObj.btnRestoreConfigurationFromBackup().shouldBe(enabled);
    }

    @Step("Configuration backup with incompatible filename is uploaded")
    public void uploadIncompatibleConfigurationBackup() throws IOException {
        File incompatibleBackup = new ClassPathResource("files/backups/ss-backup-incompatible.gpg").getFile();
        backupAndRestorePageObj.btnUploadConfigurationBackup().shouldBe(enabled).click();
        backupAndRestorePageObj.inputConfigurationBackupBackupFile().uploadFile(incompatibleBackup);
        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration backup is shown as incompatible")
    public void configurationBackupIsShownAsIncompatible() {
        backupAndRestorePageObj.btnRestoreConfigurationFromBackup().shouldNotBe(enabled);
    }
}
