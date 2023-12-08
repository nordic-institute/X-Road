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

import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.SettingsTlsCertificatesPageObj;

import java.io.File;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class SettingsTlsCertificatesStepDefs extends BaseUiStepDefs {

    private static final String DOWNLOADED_CERTIFICATE = "DOWNLOADED_CERTIFICATE";
    private static final String DOWNLOADED_CSR = "DOWNLOADED_CSR";
    private final SettingsTlsCertificatesPageObj settingsTlsCertificatesPageObj = new SettingsTlsCertificatesPageObj();

    @Step("TLS Certificates sub-tab is selected")
    public void navigateTlsCertificatesSubTab() {
        commonPageObj.subMenu.tlsCertificatesTab().click();
        settingsTlsCertificatesPageObj.tlsCertificatesView().shouldBe(visible);
    }

    @Step("Management Service TLS key hash is visible")
    public void tlsCertificateHash() {
        settingsTlsCertificatesPageObj.certificateHash().shouldBe(visible);
    }

    @Step("Management Service TLS key hash field is clicked")
    public void tlsCertificateHashIsClicked() {
        settingsTlsCertificatesPageObj.certificateHash().click();
    }

    @Step("user is able to view the certificate details")
    public void userIsAbleToViewTheCertificate() {
        settingsTlsCertificatesPageObj.certificateView.certificateDetails().shouldBe(visible);
    }

    @Step("Downloading certificate button is enabled")
    public void downloadCertificateButton() {
        settingsTlsCertificatesPageObj.btnDownloadCertificate().shouldBe(visible);
    }

    @Step("Downloading certificate button is clicked")
    public void downloadCertificateButtonIsClicked() {
        final var file = settingsTlsCertificatesPageObj.btnDownloadCertificate().download();
        scenarioContext.putStepData(DOWNLOADED_CERTIFICATE, file);
    }

    @Step("Management Service certificate is successfully downloaded")
    public void certificateIsDownloaded() {
        final File file = scenarioContext.getStepData(DOWNLOADED_CERTIFICATE);
        assertThat(file)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("gz");
    }

    @Step("Re-create key button is enabled")
    public void createCertificateKeyButton() {
        settingsTlsCertificatesPageObj.btnCreateKeyCertificate().shouldBe(visible);
    }

    @Step("Re-create key button is clicked")
    public void createCertificateKeyButtonIsClicked() {
        settingsTlsCertificatesPageObj.btnCreateKeyCertificate().click();
    }

    @Step("new key and certificate are successfully created")
    public void keyAndCertificateAreCreated() {
        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnDelete().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Generate CSR is enabled")
    public void generateCsrButton() {
        settingsTlsCertificatesPageObj.btnCreateCsr().shouldBe(visible);
    }

    @Step("Generate CSR button is clicked")
    public void createCsrButtonIsClicked() {
        settingsTlsCertificatesPageObj.btnCreateCsr().click();
    }

    @Step("new dialog is opened and Enter Distinguished name is asked and value {} is entered")
    public void enterDistinguishedName(final String distinguishedName) {
        settingsTlsCertificatesPageObj.createCsrDialog.btnGenerateCsr().shouldBe(disabled);
        settingsTlsCertificatesPageObj.createCsrDialog.btnCancel().shouldBe(enabled);
        vTextField(settingsTlsCertificatesPageObj.createCsrDialog.distinguishedName())
                .setValue(distinguishedName);
    }

    @Step("dialog Generate CSR button is clicked")
    public void generateCsrIsClicked() {
        final var file =  settingsTlsCertificatesPageObj.createCsrDialog.btnGenerateCsr().download();
        scenarioContext.putStepData(DOWNLOADED_CSR, file);
    }

    @Step("generated sign request is downloaded")
    public void enterServerCode() {
        final File file = scenarioContext.getStepData(DOWNLOADED_CSR);
        assertThat(file)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("p10");
    }

    @Step("Upload certificate is enabled")
    public void uploadCertificateButton() {
        settingsTlsCertificatesPageObj.btnUploadCertificate().shouldBe(visible);
    }

    @Step("different management service TLS certificate {} is uploaded")
    public void uploadCertificate(String fileName) {
        settingsTlsCertificatesPageObj.btnUploadCertificate().click();

        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(disabled);
        commonPageObj.inputFile().uploadFromClasspath("files/certificates/" + fileName);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();
    }
}
