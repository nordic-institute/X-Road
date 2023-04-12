/*
 * The MIT License
 *
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

package org.niis.xroad.cs.test.ui.glue;

import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.GlobalConfigurationTrustedAnchorsPageObj;

import java.io.File;
import java.io.FileNotFoundException;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.test.ui.glue.BaseUiStepDefs.StepDataKey.DOWNLOADED_FILE;

public class GlobalConfigurationTrustedAnchorsStepDefs extends BaseUiStepDefs {
    private static final String HASH = "D2:7B:C4:38:C2:9D:1E:4B:B6:E5:47:AB:15:69:14:78:98:0C:38:CD:4A:0C:D0:DA:E3:96:B8:BD";
    private final GlobalConfigurationTrustedAnchorsPageObj trustedAnchorsPageObj = new GlobalConfigurationTrustedAnchorsPageObj();

    @Step("trusted anchors list not contains instance {}")
    public void trustedAnchorsListNotContainsInstance(String instance) {
        trustedAnchorsPageObj.instanceWithName(instance).shouldNotBe(appear);
    }

    @Step("user clicks Upload button")
    public void userClicksUploadButton() {
        trustedAnchorsPageObj.uploadAnchorButton().shouldBe(enabled).click();
    }

    @Step("user uploads trusted anchor from file {}")
    public void userUploadsTrustedAnchor(String fileName) {
        commonPageObj.inputFile().uploadFromClasspath("files/trusted-anchor/" + fileName);
    }

    @Step("confirmation is asked and user confirm anchor upload")
    public void confirmAnchorUpload() {
        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnConfirm().shouldBe(enabled).click();
    }

    @Step("confirmation is asked and user confirm anchor delete")
    public void confirmAnchorDelete() {
        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();
    }

    @Step("trusted anchor is successfully uploaded")
    public void uploadSuccess() {
        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("trusted anchor {} with created {} is displayed in list")
    public void anchorDetailIsDisplayedInList(String instance, String created) {
        trustedAnchorsPageObj.instanceWithNameAndHashAndCreated(instance, HASH, created).shouldBe(appear);
    }

    @Step("user clicks trusted anchor {} Download button")
    public void userClicksDownloadButton(String instance) throws FileNotFoundException {
        final var file = trustedAnchorsPageObj.downloadAnchorButton(instance).download();
        putStepData(DOWNLOADED_FILE, file);
    }
    @Step("trusted anchor is successfully downloaded")
    public void anchorIsSuccessfullyDownloaded() {
        final File file = (File) getStepData(DOWNLOADED_FILE).orElseThrow();
        assertThat(file)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("xml");
    }

    @Step("user clicks trusted anchor {} Delete button")
    public void userClicksDeleteButton(String instance) {
        trustedAnchorsPageObj.deleteAnchorButton(instance).click();
    }

    @Step("trusted anchor is successfully deleted")
    public void anchorIsSuccessfullyDeleted() {
        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }
}
