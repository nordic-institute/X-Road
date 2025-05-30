/*
 * The MIT License
 *
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

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.files.FileFilters;
import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.KeyAndCertPageObj;

import java.io.File;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.awaitility.Awaitility.given;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.selectorComboboxOf;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.selectorOptionOf;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vSelect;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

@Slf4j
public class KeyAndCertStepDefs extends BaseUiStepDefs {
    private final KeyAndCertPageObj keyAndCertPageObj = new KeyAndCertPageObj();

    @Step("User cannot log-in or log-out out of token {}")
    public void validateLoginLogoutToken(String token) {
        keyAndCertPageObj.section(token).btnLogin().shouldNotBe(visible);
        keyAndCertPageObj.section(token).btnLogout().shouldNotBe(visible);
    }

    @Step("User can log-out out of token {}")
    public void validateCanLogoutToken(String token) {
        keyAndCertPageObj.section(token).btnLogout().shouldBe(visible);
    }

    @Step("User logs in token: {} with PIN: {}")
    public void loginToken(String tokenKey, String tokenPin) {
        keyAndCertPageObj.section(tokenKey).btnLogin()
                .shouldBe(enabled)
                .click();

        keyAndCertPageObj.tokenLoginDialog.inputPin().setValue(tokenPin);
        keyAndCertPageObj.tokenLoginDialog.btnLogin()
                .shouldBe(enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User logs out token: {}")
    public void logoutToken(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).btnLogout()
                .shouldBe(enabled)
                .click();

        keyAndCertPageObj.tokenLogoutDialog.btnLogout()
                .shouldBe(enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Token: {} is logged-out")
    public void tokenIsLoggedOut(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).btnLogin().shouldBe(enabled);
    }

    @Step("Token: {} is logged-in")
    public void tokenIsLoggedIn(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).btnLogout().shouldBe(enabled);
    }

    @Step("Token: {} is present")
    public void tokenIsVisible(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).tokenLabel().shouldBe(visible);
    }

    @Step("Token: {} is present and expanded")
    public void tokenIsVisibleAndExpand(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).tokenLabel().shouldBe(visible).click();
    }

    @Step("Token: {} edit page is opened")
    public void tokenIsVisibleAndEdited(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).tokenEditButton(tokenKey).shouldBe(visible).click();
    }

    @Step("Inactive token {} is successfully deleted")
    public void tokenDeleted(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).tokenDeleteButton().shouldBe(visible).click();
        commonPageObj.dialog.btnCancel().click();

        keyAndCertPageObj.section(tokenKey).tokenDeleteButton().shouldBe(visible).click();
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Token Alert about token policy being enforced is present")
    public void tokenHasEnforceTokenPolicyAlert() {
        keyAndCertPageObj.tokenEdit.btnChangeToken().shouldBe(visible).click();
        keyAndCertPageObj.tokenEdit.alertTokenPolicyEnabled().shouldBe(visible);
    }

    @Step("Token: {} - Add key wizard is opened")
    public void addKey(String tokenKey) {
        keyAndCertPageObj.section(tokenKey).addSigningKey().shouldBe(visible).click();
    }

    @Step("Token: {} - Add key is {selenideValidation}")
    public void validateAddKey(String tokenKey, ParameterMappers.SelenideValidation selenideValidation) {
        keyAndCertPageObj.section(tokenKey).addSigningKey().shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Add key wizard is closed")
    public void closeAddKeyWizard() {
        keyAndCertPageObj.addKeyWizardDetails.cancelButton().click();
    }

    @Step("Add key wizard Generate CSR step is closed")
    public void closeAddKeyGenWizard() {
        keyAndCertPageObj.addKeyWizardGenerate.cancelButton().click();
    }

    @Step("Key Label is set to {string}")
    public void setKeyLabel(String label) {
        if (StringUtils.isNotBlank(label)) {
            vTextField(keyAndCertPageObj.addKeyWizardDetails.keyLabel()).setValue(label);
        }
        keyAndCertPageObj.addKeyWizardDetails.nextButton().click();
    }

    @Step("CSR details Usage is set to {string}, Client set to {string}, Certification Service to {string} and CSR format {string}")
    public void setAuthCsrDetails(String usage, String client, String certificationService, String csrFormat) {
        keyAndCertPageObj.addKeyWizardCsrDetails.continueButton().shouldBe(disabled);

        keyAndCertPageObj.addKeyWizardCsrDetails.csrUsage().click();
        selectorOptionOf(usage).click();

        if ("SIGNING".equalsIgnoreCase(usage)) {
            keyAndCertPageObj.addKeyWizardCsrDetails.csrClient().click();
            selectorComboboxOf(client).click();
        }

        keyAndCertPageObj.addKeyWizardCsrDetails.csrService().click();
        selectorOptionOf(certificationService).click();

        keyAndCertPageObj.addKeyWizardCsrDetails.csrFormat().click();
        selectorOptionOf(csrFormat).click();

        keyAndCertPageObj.addKeyWizardCsrDetails.previousButton().shouldBe(visible).click();
        keyAndCertPageObj.addKeyWizardCsrDetails.csrService().shouldNotBe(visible);
        keyAndCertPageObj.addKeyWizardDetails.nextButton().shouldBe(visible).click();
        keyAndCertPageObj.addKeyWizardCsrDetails.csrService().shouldBe(visible);
        keyAndCertPageObj.addKeyWizardCsrDetails.continueButton().shouldBe(visible).click();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("CSR is generated for token {string}, key {string}, certification service {string}, format {string}, security server {string}")
    public void csrIsGeneratedForKeyCertificationServiceFormat(String token,
                                                               String key,
                                                               String certService,
                                                               String csrFormat,
                                                               String securityServer) {
        keyAndCertPageObj.section(token).tokenLabeledKeyGenerateCsrButton(key).shouldBe(enabled).click();

        keyAndCertPageObj.addKeyWizardCsrDetails.csrService().click();
        selectorOptionOf(certService).click();

        keyAndCertPageObj.addKeyWizardCsrDetails.csrFormat().click();
        selectorOptionOf(csrFormat).click();

        keyAndCertPageObj.addKeyWizardCsrDetails.continueButton().shouldBe(visible).click();

        vTextField(keyAndCertPageObj.addKeyWizardGenerate.commonName()).setValue(securityServer);
        vTextField(keyAndCertPageObj.addKeyWizardGenerate.subjectAltName()).setValue(securityServer);
        vTextField(keyAndCertPageObj.addKeyWizardGenerate.organizationName()).setValue(RandomStringUtils.secure().nextAlphabetic(10));

        keyAndCertPageObj.addKeyWizardGenerate.generateButton().click();
        keyAndCertPageObj.addKeyWizardGenerate.doneButton().click();
    }

    @Step("Token {string}, key {string} has {int} certificate signing requests")
    public void tokenKeyHasCertificateSigningRequests(String token, String keyLabel, int count) {
        keyAndCertPageObj.section(token).labeledKeyCsrRows(keyLabel).shouldBe(CollectionCondition.size(count));
    }

    @SneakyThrows
    @Step("CSR with extension {string} successfully generated")
    public void setGenerateCsr(String format) {
        File certReq = keyAndCertPageObj.addKeyWizardGenerate.generateButton().download(FileFilters.withExtension(format));
        log.info("Putting {} into downloaded file", certReq);
        putStepData(StepDataKey.DOWNLOADED_FILE, certReq);

        keyAndCertPageObj.addKeyWizardGenerate.doneButton().click();
    }

    @Step("Generate {string} CSR is set to DNS {string} and Organization {string}")
    public void setCsr(String usage, String dns, String organization) {
        keyAndCertPageObj.addKeyWizardGenerate.generateButton().shouldBe(disabled);
        keyAndCertPageObj.addKeyWizardGenerate.doneButton().shouldBe(disabled);

        if (StringUtils.isNotBlank(dns)) {
            if ("AUTHENTICATION".equalsIgnoreCase(usage)) {
                vTextField(keyAndCertPageObj.addKeyWizardGenerate.commonName()).setValue(dns);
            }
            vTextField(keyAndCertPageObj.addKeyWizardGenerate.subjectAltName()).setValue(dns);
        }
        vTextField(keyAndCertPageObj.addKeyWizardGenerate.organizationName()).setValue(organization);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @SneakyThrows
    @Step("ACME order is made")
    public void setGenerateAcmeCsr() {
        keyAndCertPageObj.addKeyWizardGenerate.acmeOrderButton().click();
        given()
                .pollDelay(5, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .pollInSameThread()
                .await()
                .untilAsserted(() -> {
                    commonPageObj.snackBar.success().shouldBe(Condition.visible);
                });
        commonPageObj.snackBar.btnClose().click();
        keyAndCertPageObj.addKeyWizardGenerate.doneButton().click();
    }

    @Step("Token: {} - has key with label {string}")
    public void tokenHasKey(String tokenKey, String label) {
        keyAndCertPageObj.section(tokenKey).tokenLabeledKey(label).shouldBe(visible);
    }

    @Step("Token: {} - has {} auth keys, {} sign keys")
    public void tokenHasKeys(String tokenKey, int amountAuth, int amountSign) {
        keyAndCertPageObj.section(tokenKey).tokenAuthKeyRows().shouldBe(CollectionCondition.size(amountAuth));
        keyAndCertPageObj.section(tokenKey).tokenSignKeyRows().shouldBe(CollectionCondition.size(amountSign));
    }

    @Step("Token: {} - Generated certificate is imported")
    public void importCertificate(String tokenKey) {
        Optional<File> cert = getStepData(StepDataKey.CERT_FILE);

        keyAndCertPageObj.section(tokenKey).btnImportCert().shouldBe(enabled);
        keyAndCertPageObj.section(tokenKey).inputCert().uploadFile(cert.orElseThrow());

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Token: {} - has key {string} with status {string} and ocsp status {string}")
    public void validateCert(String tokenKey, String keyLabel, String certStatus, String ocspStatus) {
        keyAndCertPageObj.section(tokenKey).keyLabelByName(keyLabel).shouldBe(visible);
        keyAndCertPageObj.section(tokenKey).keyStatusByLabel(keyLabel).shouldBe(text(certStatus));
        keyAndCertPageObj.section(tokenKey).keyOcspStatusByLabel(keyLabel).shouldBe(text(ocspStatus));
    }

    @Step("Token: {} - has {string} key {string} with correct ARI automatic renewal status")
    public void validateAriAutomaticRenewalStatus(String tokenKey, String usage, String keyLabel) {
        validateAutomaticRenewalStatus(tokenKey, usage, keyLabel, true);
    }

    @Step("Token: {} - has {string} key {string} with correct fixed automatic renewal status")
    public void validateFixedAutomaticRenewalStatus(String tokenKey, String usage, String keyLabel) {
        validateAutomaticRenewalStatus(tokenKey, usage, keyLabel, false);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void validateAutomaticRenewalStatus(String tokenKey, String usage, String keyLabel, boolean ariSupported) {
        String expectedAutomaticRenewalStatus;
        if ("SIGNING".equalsIgnoreCase(usage)) {
            if (ariSupported) {
                // acme2certifier ARI renewal suggestion date is calculated as follows:
                // now() + 90% of days between issued date (here now()) and expiration date
                // (renewaltreshold_pctg and cert_validity_days are configurable in acme_serv.cfg)
                expectedAutomaticRenewalStatus = "NEXT PLANNED RENEWAL ON " + LocalDate.now().plusDays(27);
            } else {
                // Fixed renewal suggestion date is calculated as follows:
                // value given for the '-days' certificate option when generating the certificate in sign_req.sh script
                // -
                // SystemProperties.PROXY_UI_API_ACME_RENEWAL_TIME_BEFORE_EXPIRATION_DATE
                expectedAutomaticRenewalStatus = "NEXT PLANNED RENEWAL ON " + LocalDate.now().plusDays(7300 - 14);
            }
        } else {
            // Generated AUTH cert is in saved not registered status and ignored by the renewal job
            expectedAutomaticRenewalStatus = "N/A";
        }
        keyAndCertPageObj.section(tokenKey).keyAutomaticRenewalStatusByLabel(keyLabel).shouldBe(text(expectedAutomaticRenewalStatus));
    }

    @Step("Token: {} - {string} CSR in position {} is deleted")
    public void deleteCsr(String tokenKey, String type, int pos) {

        SelenideElement btnDelete;
        if ("SIGNING".equalsIgnoreCase(type)) {
            btnDelete = keyAndCertPageObj.section(tokenKey).btnDeleteSignCsrByPos(pos);
        } else {
            btnDelete = keyAndCertPageObj.section(tokenKey).btnDeleteAuthCsrByPos(pos);
        }

        btnDelete.click();
        commonPageObj.dialog.btnCancel().click();
        btnDelete.click();
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Token: {} - CSR of key {string} is used to order certificate from {string}")
    public void orderCertificateWithCsr(String tokenKey, String keyLabel, String ca) {

        SelenideElement btnOrderCertificate = keyAndCertPageObj.section(tokenKey).btnAcmeOrderCertByLabel(keyLabel);

        btnOrderCertificate.click();
        commonPageObj.dialog.btnCancel().click();
        btnOrderCertificate.click();
        vSelect(keyAndCertPageObj.acmeOrderCertificateDialog.certificateAuthority()).clickAndSelect(ca);
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Token: {} - ACME order dialog is opened for key {string}")
    public void acmeOrderDialogIsOpened(String tokenKey, String keyLabel) {
        keyAndCertPageObj.section(tokenKey).btnAcmeOrderCertByLabel(keyLabel).click();
    }

    @Step("ACME order dialog Order button is disabled")
    public void acmeOrderDialogOrderButtonIsDisabled() {
        commonPageObj.dialog.btnSave().shouldBe(disabled);
    }

    @Step("Token: {}, key {string} generate CSR button is disabled")
    public void tokenKeyGenerateCSRButtonIsDisabled(String token, String keyLabel) {
        keyAndCertPageObj.section(token).tokenLabeledKeyGenerateCsrButton(keyLabel).shouldBe(disabled);
    }

    @Step("Wizard CSR page ACME order button is disabled")
    public void acmeOrderButtonIsDisabled() {
        keyAndCertPageObj.addKeyWizardGenerate.acmeOrderButton().shouldBe(disabled);
    }

    @Step("Change the pin section is expanded")
    public void changeThePinSectionIsExpanded() {
        keyAndCertPageObj.tokenEdit.btnChangeToken().shouldBe(visible).click();
    }

    @Step("PIN is changed from {string} to {string}")
    public void pinIsChangedFromTo(String oldPin, String newPin) {
        keyAndCertPageObj.tokenEdit.inputOldPin().setValue(oldPin);
        keyAndCertPageObj.tokenEdit.inputNewPin().setValue(newPin);
        keyAndCertPageObj.tokenEdit.inputNewPinConfirm().setValue(newPin);
        keyAndCertPageObj.tokenEdit.btnSave().shouldBe(enabled).click();
        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }
}
