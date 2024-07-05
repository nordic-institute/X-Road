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

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.constants.Constants;
import org.niis.xroad.cs.test.ui.page.TrustServicesPageObj;
import org.niis.xroad.cs.test.ui.utils.CertificateUtils;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.UUID;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vCheckbox;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;
import static org.niis.xroad.cs.test.ui.constants.Constants.CN_SUBJECT_PREFIX;

public class TrustServicesStepDefs extends BaseUiStepDefs {
    private static final String CERTIFICATE_PROFILE = "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider";
    private static final String NEW_CERTIFICATE_PROFILE = "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider";

    private final TrustServicesPageObj trustServicesPageObj = new TrustServicesPageObj();

    private String certificationServiceName;
    private X509Certificate testCertificate;

    @Step("new certification service is added")
    public void newCertificationServiceIsAdded() throws Exception {
        trustServicesPageObj.btnAddCertificationService().click();

        certificationServiceName = "e2e-test-cert-service-" + UUID.randomUUID();
        final byte[] certificate = CertificateUtils.generateAuthCert(CN_SUBJECT_PREFIX + certificationServiceName);

        testCertificate = CertificateUtils.readCertificate(certificate);

        trustServicesPageObj.addDialog.inputFile().uploadFile(CertificateUtils.getAsFile(certificate));
        commonPageObj.dialog.btnSave().click();
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputCertificateProfile())
                .setValue(CERTIFICATE_PROFILE);
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("new CA certificate is uploaded")
    public void newCaCertificateIsUploaded() throws Exception {
        trustServicesPageObj.btnAddCertificationService().click();
        certificationServiceName = "e2e-test-acme-cert-service-" + UUID.randomUUID();
        final byte[] certificate = CertificateUtils.generateAuthCert(CN_SUBJECT_PREFIX + certificationServiceName);
        testCertificate = CertificateUtils.readCertificate(certificate);
        trustServicesPageObj.addDialog.inputFile().uploadFile(CertificateUtils.getAsFile(certificate));
        commonPageObj.dialog.btnSave().click();
    }

    @Step("new acme certification service fields dont allow invalid values")
    public void newAcmeCertificationServiceFieldsDontAllowInvalidValues() {
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputCertificateProfile())
                .setValue(CERTIFICATE_PROFILE);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled);
        vCheckbox(trustServicesPageObj.addCaSettingsDialog.checkboxAcme()).shouldBeUnchecked().click();
        commonPageObj.dialog.btnSave().shouldBe(Condition.disabled);
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputAcmeServerDirectoryUrl())
                .setValue("httpss://new-test-ca/acme");
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputAcmeServerIpAddress())
                .setValue("198.7.6.X");

        commonPageObj.dialog.btnSave().shouldBe(Condition.disabled);
        vCheckbox(trustServicesPageObj.addCaSettingsDialog.checkboxAcme()).shouldBeChecked().click();
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled);
    }

    @Step("new ACME certification service is added with valid values")
    public void newAcmeCertificationServiceIsAddedWithValidValues() {
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputCertificateProfile()).clear()
                .setValue(CERTIFICATE_PROFILE);
        vCheckbox(trustServicesPageObj.addCaSettingsDialog.checkboxAcme()).shouldBeUnchecked().click();
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputAcmeServerDirectoryUrl()).clear()
                .setValue("https://test-ca/acme");
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputAcmeServerIpAddress()).clear()
                .setValue("192.3.4.5");
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputAuthCertProfileId()).clear()
                .setValue("1");
        vTextField(trustServicesPageObj.addCaSettingsDialog.inputSignCertProfileId()).clear()
                .setValue("2");
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }


    @Step("new certification service is visible in the Certification Services list")
    public void newCertificationServiceIsVisibleInTheList() {
        trustServicesPageObj.tableServicesRowOf(certificationServiceName).should(appear);
    }

    @Step("certification service is not visible in the Certification Services list")
    public void certificationServiceIsNotVisibleInTheCertificationServicesList() {
        trustServicesPageObj.tableServicesRowOf(certificationServiceName).shouldNot(appear);
    }

    @Step("user is able to sort by column {int}")
    public void userIsAbleToSortByColumn(int columnIndex) {
        var column = trustServicesPageObj.tableServicesCol(columnIndex);
        column
                .shouldHave(cssClass("v-data-table__th--sortable"))
                .shouldNotHave(cssClass("v-data-table__th--sorted"))
                .click();

        column
                .shouldHave(cssClass("v-data-table__th--sorted"))
                .$x(".//i")
                .shouldHave(cssClass("mdi-arrow-up"))
                .click();

        column
                .shouldHave(cssClass("v-data-table__th--sorted"))
                .$x(".//i")
                .shouldHave(cssClass("mdi-arrow-down"))
                .click();
    }


    @Step("user opens certification service details")
    public void userOpensCertificationServiceDetails() {
        trustServicesPageObj.tableServicesRowOf(certificationServiceName).click();
    }

    @Step("certification service details are displayed")
    public void certificationServiceDetailsAreVisible() {
        final SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT);

        trustServicesPageObj.cardSubjectDn().shouldHave(text(testCertificate.getSubjectDN().getName()));
        trustServicesPageObj.cardIssuerDn().shouldHave(text(testCertificate.getIssuerDN().getName()));
        trustServicesPageObj.cardValidFrom().shouldHave(text(sdf.format(testCertificate.getNotBefore())));
        trustServicesPageObj.cardValidTo().shouldHave(text(sdf.format(testCertificate.getNotAfter())));
    }

    @Step("user is able to view the certificate")
    public void userIsAbleToViewTheCertificate() {
        trustServicesPageObj.certServiceDetails.btnViewCertificate().click();
        trustServicesPageObj.certificateView.certificateDetails().shouldBe(visible);
    }

    @Step("user navigates to CA settings")
    public void userNavigatesToCASettings() {
        trustServicesPageObj.certServiceDetails.tabSettings().click();
    }

    @Step("CA settings are shown")
    public void caSettingsAreShown() {
        trustServicesPageObj.certServiceDetails.caSettings.cardCertProfile().shouldHave(text(CERTIFICATE_PROFILE));
        trustServicesPageObj.certServiceDetails.caSettings.cardTlsAuth().shouldHave(text("False"));
    }

    @Step("user can change the certificate profile")
    public void userCanChangeTheCertificateProfile() {
        trustServicesPageObj.certServiceDetails.caSettings.btnEditCertProfile().click();

        vTextField(trustServicesPageObj.certServiceDetails.caSettings.inputCertProfile())
                .clear()
                .setValue(NEW_CERTIFICATE_PROFILE);

        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();

        trustServicesPageObj.certServiceDetails.caSettings.cardCertProfile().shouldHave(text(NEW_CERTIFICATE_PROFILE));
    }

    @Step("user can change the TLS Auth setting")
    public void userCanChangeTheTLSAuthSetting() {
        trustServicesPageObj.certServiceDetails.caSettings.btnEditTlsAuth().click();
        vCheckbox(trustServicesPageObj.certServiceDetails.caSettings.checkboxTlsAuth())
                .click();
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();

        trustServicesPageObj.certServiceDetails.caSettings.cardTlsAuth().shouldHave(text("True"));
    }

    @Step("user can change the acme settings")
    public void userCanChangeTheAcmeSettings() {
        trustServicesPageObj.certServiceDetails.caSettings.btnEditAcme().click();

        String newDirectoryUrl = "https://new-test-ca/acme";
        String newIpAddress = "198.7.6.5";
        String newAuthCertProfileId = "10";
        String newSignCertProfileId = "11";
        vTextField(trustServicesPageObj.certServiceDetails.caSettings.inputAcmeServerDirectoryUrl())
                .clear()
                .setValue(newDirectoryUrl);
        vTextField(trustServicesPageObj.certServiceDetails.caSettings.inputAcmeServerIpAddress())
                .clear()
                .setValue(newIpAddress);
        vTextField(trustServicesPageObj.certServiceDetails.caSettings.inputAuthCertProfileId())
                .clear()
                .setValue(newAuthCertProfileId);
        vTextField(trustServicesPageObj.certServiceDetails.caSettings.inputSignCertProfileId())
                .clear()
                .setValue(newSignCertProfileId);

        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();

        trustServicesPageObj.certServiceDetails.caSettings.acmeServerDirectoryUrl().shouldHave(text(newDirectoryUrl));
        trustServicesPageObj.certServiceDetails.caSettings.acmeServerIpAddress().shouldHave(text(newIpAddress));
        trustServicesPageObj.certServiceDetails.caSettings.authenticationCertificateProfileId().shouldHave(text(newAuthCertProfileId));
        trustServicesPageObj.certServiceDetails.caSettings.signingCertificateProfileId().shouldHave(text(newSignCertProfileId));
    }

    @Step("changed acme settings fields are validated")
    public void changedAcmeSettingsFieldsAreValidated() {
        trustServicesPageObj.certServiceDetails.caSettings.btnEditAcme().click();

        String newDirectoryUrl = "httpss://new-test-ca/acme";
        String newIpAddress = "198.7.6.X";
        vTextField(trustServicesPageObj.certServiceDetails.caSettings.inputAcmeServerDirectoryUrl())
                .clear()
                .setValue(newDirectoryUrl);
        vTextField(trustServicesPageObj.certServiceDetails.caSettings.inputAcmeServerIpAddress())
                .clear()
                .setValue(newIpAddress);

        commonPageObj.dialog.btnSave().shouldBe(Condition.disabled);
    }

    @Step("user can remove CA ACME capability")
    public void userCanRemoveCaAcmeCapability() {
        vCheckbox(trustServicesPageObj.addCaSettingsDialog.checkboxAcme()).shouldBeChecked().click();
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();

        trustServicesPageObj.certServiceDetails.caSettings.acmeServerDirectoryUrl().shouldHave(text("-"));
        trustServicesPageObj.certServiceDetails.caSettings.acmeServerIpAddress().shouldHave(text("-"));
        trustServicesPageObj.certServiceDetails.caSettings.authenticationCertificateProfileId().shouldHave(text("-"));
        trustServicesPageObj.certServiceDetails.caSettings.signingCertificateProfileId().shouldHave(text("-"));
    }

    @Step("user clicks on delete trust service")
    public void userClicksOnDeleteTrustService() {
        trustServicesPageObj.certServiceDetails.btnDeleteTrustService().click();

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled);

        commonPageObj.dialog.btnSave().click();
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
    }

}
