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
package org.niis.xroad.test.ui.glue;

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Then;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.test.ui.glue.constants.Constants;
import org.openqa.selenium.By;

import java.io.File;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static org.openqa.selenium.By.xpath;

public class SecurityServerBackupStepDefs extends BaseUiStepDefs {
    private static final By TAB_BACKUP_AND_RESTORE = xpath("//*[@data-test=\"backupandrestore-tab-button\"]");
    private static final By BTN_CREATE_BACKUP = xpath("//*[@data-test=\"backup-create-configuration\"]");
    private static final By BTN_SEARCH = xpath("//button[contains(@class, \"mdi-magnify\")]");
    private static final By INPUT_SEARCH = xpath("//input[@data-test=\"search-input\"]");
    private static final By BTN_DIALOG_CONFIRM_DELETE = xpath("//div[@data-test=\"dialog-simple\" "
            + "and .//div[@data-test=\"dialog-content-text\" "
            + "and contains(text(), \"Are you sure you want to delete\")]]//button[@data-test=\"dialog-save-button\"]");
    private static final By BTN_UPLOAD_CANCEL = xpath("//button[@data-test=\"dialog-cancel-button\"]");
    private static final By INPUT_FILE_UPLOAD = xpath("//input[@type=\"file\"]");

    @Then("Backup and restore tab is selected")
    public void selectBackupAndRestoreTab() {
        $(TAB_BACKUP_AND_RESTORE).click();
    }

    @Then("Backup and restore tab is not visible")
    public void backupAndRestoreTabIsNotVisible() {
        $(TAB_BACKUP_AND_RESTORE).shouldNotBe(Condition.visible);
    }

    @Then("A new backup is created")
    public void anchorDownloadButtonIsVisible() {
        $(BTN_CREATE_BACKUP).click();

        String snackbarMessage = $(Constants.SNACKBAR_SUCCESS)
                .shouldBe(Condition.visible)
                .text();

        String backupName = snackbarMessage.split(" ")[1];
        scenarioProvider.getCucumberScenario().log(String.format("Backup %s was created", backupName));
        scenarioContext.putStepData("backupName", backupName);

        $(Constants.BTN_CLOSE_SNACKBAR).click();
    }

    @Then("A newly created backup is filtered and visible")
    public void backupIsFiltered() {
        String backupName = scenarioContext.getStepData("backupName");

        $(BTN_SEARCH).click();
        $(INPUT_SEARCH).setValue(backupName);


        $$(xpath("//div[@data-test='backup-restore-view']//table/tbody/tr"))
                .shouldHave(CollectionCondition.size(1));

        $(INPUT_SEARCH).clear();
    }

    @Then("A newly created backup is deleted")
    public void backupIsDeleted() {
        String backupName = scenarioContext.getStepData("backupName");
        Assertions.assertNotNull(backupName);

        $(getDeleteBackupSelector(backupName)).click();

        $(BTN_DIALOG_CONFIRM_DELETE).click();

        $(Constants.SNACKBAR_SUCCESS)
                .shouldBe(Condition.visible)
                .shouldHave(Condition.partialText(backupName));

        $(Constants.BTN_CLOSE_SNACKBAR).click();
    }

    @SneakyThrows
    @Then("A newly created backup is downloaded")
    public void backupIsDownloaded() {
        String backupName = scenarioContext.getStepData("backupName");
        Assertions.assertNotNull(backupName);

        File result = $(getDownloadBackupSelector(backupName)).download();
        scenarioContext.putStepData("downloadedBackupFile", result);
    }

    @Then("Downloaded backup is uploaded")
    public void backupIsUploaded() {
        String backupName = scenarioContext.getRequiredStepData("backupName");
        File file = scenarioContext.getRequiredStepData("downloadedBackupFile");

        $(INPUT_FILE_UPLOAD).uploadFile(file);

        //Verify that you can cancel
        $(BTN_UPLOAD_CANCEL).click();

        $(INPUT_FILE_UPLOAD).uploadFile(file);
        $(Constants.BTN_DIALOG_SAVE).click();

        $(Constants.SNACKBAR_SUCCESS)
                .shouldBe(Condition.visible)
                .shouldHave(Condition.partialText(backupName));
    }

    private By getDeleteBackupSelector(String backupName) {
        String selector = String.format("//div[@data-test='backup-restore-view']//table"
                + "/tbody/tr/td[text() = \"%s\"]/..//button[@data-test=\"backup-delete\"]", backupName);
        return xpath(selector);
    }

    private By getDownloadBackupSelector(String backupName) {
        String selector = String.format("//div[@data-test='backup-restore-view']//table"
                + "/tbody/tr/td[text() = \"%s\"]/..//button[@data-test=\"backup-download\"]", backupName);
        return xpath(selector);
    }

}
