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

package org.niis.xroad.test.ui.glue.centralserver;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;
import org.openqa.selenium.By;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.UUID;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_CLOSE_SNACKBAR;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_SAVE;
import static org.niis.xroad.test.ui.glue.constants.Constants.DATETIME_FORMAT;
import static org.niis.xroad.test.ui.glue.constants.Constants.INPUT_FILE_UPLOAD;
import static org.niis.xroad.test.ui.glue.constants.Constants.SNACKBAR_SUCCESS;
import static org.niis.xroad.test.ui.utils.CertificateUtils.generateAuthCert;
import static org.niis.xroad.test.ui.utils.CertificateUtils.getAsFile;
import static org.niis.xroad.test.ui.utils.CertificateUtils.readCertificate;
import static org.openqa.selenium.By.xpath;
import static org.openqa.selenium.Keys.CONTROL;
import static org.openqa.selenium.Keys.DELETE;

public class CentralServerCertificationServicesStepDefs extends BaseUiStepDefs {

    private static final String CERTIFICATE_PROFILE = "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider";
    private static final String NEW_CERTIFICATE_PROFILE = "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider";

    private static final By TAB_TRUST_SERVICES = xpath("//div[contains(@class, \"v-tabs-bar__content\")]//a[contains(@class,"
            + "\"v-tab\") and contains(text(), \"Trust Services\")]");
    private static final By BTN_ADD_CERTIFICATION_SERVICE = xpath("//button[@data-test=\"add-certification-service\"]");
    private static final By BTN_EDIT_TLS_AUTH = xpath("//div[@data-test=\"tls-auth-card\"]//button[@data-test=\"info-card-edit-button\"]");
    private static final By BTN_EDIT_CERT_PROFILE =
            xpath("//div[@data-test=\"cert-profile-card\"]//button[@data-test=\"info-card-edit-button\"]");
    private static final By INPUT_CERTIFICATE_PROFILE = xpath("//input[@data-test=\"cert-profile-input\"]");
    private static final String TABLE_CERT_SERVICES = "//div[@data-test=\"certification-services\"]//table";
    private static final String TABLE_ROW_NEW_CERT_SERVICE = TABLE_CERT_SERVICES + "/tbody/tr/td/div[contains(text(), \"%s\")]";
    private static final By CARD_SUBJECT_DN = xpath("//div[@data-test=\"subject-distinguished-name-card\"]/div[2]/div");
    private static final By CARD_ISSUER_DN = xpath("//div[@data-test=\"issuer-distinguished-name-card\"]/div[2]/div");
    private static final By CARD_VALID_FROM = xpath("//div[@data-test=\"valid-from-card\"]/div[2]/div");
    private static final By CARD_VALID_TO = xpath("//div[@data-test=\"valid-to-card\"]/div[2]/div");
    private static final By CARD_TLS_AUTH = xpath("//div[@data-test=\"tls-auth-card\"]/div[2]/div");
    private static final By CARD_CERT_PROFILE = xpath("//div[@data-test=\"cert-profile-card\"]/div[2]/div");
    private static final By BTN_VIEW_CERTIFICATE = xpath("//button[@data-test=\"view-certificate-button\"]");
    private static final By CERTIFICATE_DETAILS_COMPONENT = xpath("//div[contains(@class, \"certificate-details-wrapper\")]");
    private static final By TAB_CA_SETTINGS =
            xpath("//div[@id=\"certification-service-view\"]//div[@role=\"tablist\"]//a[contains(text(), \"CA Settings\")]");
    private static final By INPUT_EDIT_CERT_PROFILE =
            xpath("//div[@data-test=\"dialog-simple\"]//input[@data-test=\"cert-profile-input\"]");
    private static final By CHECKBOX_CHANGE_TLS_AUTH =
            xpath("//div[@data-test=\"dialog-simple\"]//input[@role=\"checkbox\"]/parent::div/following-sibling::label");

    @And("TrustServices tab is selected")
    public void trustServicesTabIsSelected() {
        $(TAB_TRUST_SERVICES).click();
    }

    @When("new certification service is added")
    public void newCertificationServiceIsAdded() throws Exception {
        $(BTN_ADD_CERTIFICATION_SERVICE).click();

        final String certificationServiceName = "e2e-test-cert-service-" + UUID.randomUUID();
        final byte[] certificate = generateAuthCert(certificationServiceName);

        scenarioContext.putStepData("certificationServiceName", certificationServiceName);
        scenarioContext.putStepData("testCertificate", readCertificate(certificate));

        $(INPUT_FILE_UPLOAD).uploadFile(getAsFile(certificate));
        $(BTN_DIALOG_SAVE).click();
        $(INPUT_CERTIFICATE_PROFILE).setValue(CERTIFICATE_PROFILE);
        $(BTN_DIALOG_SAVE).click();

        $(SNACKBAR_SUCCESS).shouldBe(visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }


    @Then("new certification service is visible in the Certification Services list")
    public void newCertificationServiceIsVisibleInTheList() {
        final String certificationServiceName = scenarioContext.getRequiredStepData("certificationServiceName");

        $(xpath(format(TABLE_ROW_NEW_CERT_SERVICE, certificationServiceName))).should(appear);
    }

    @And("user is able to sort by column {int}")
    public void userIsAbleToSortByColumn(int columnIndex) {
        final String column = TABLE_CERT_SERVICES + "/thead/tr/th[" + columnIndex + "]";
        assertEquals("none", $(xpath(column)).getAttribute("aria-sort"));
        $(xpath(column)).click();
        assertEquals("ascending", $(xpath(column)).getAttribute("aria-sort"));
        $(xpath(column)).click();
        assertEquals("descending", $(xpath(column)).getAttribute("aria-sort"));
    }


    @And("user opens certification service details")
    public void userOpensCertificationServiceDetails() {
        final String certificationServiceName = scenarioContext.getRequiredStepData("certificationServiceName");

        $(xpath(format(TABLE_ROW_NEW_CERT_SERVICE, certificationServiceName))).click();
    }

    @Then("certification service details are displayed")
    public void certificationServiceDetailsAreVisible() {
        final X509Certificate testCertificate = scenarioContext.getRequiredStepData("testCertificate");
        final SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT);

        $(CARD_SUBJECT_DN).shouldHave(text(testCertificate.getSubjectDN().getName()));
        $(CARD_ISSUER_DN).shouldHave(text(testCertificate.getIssuerDN().getName()));
        $(CARD_VALID_FROM).shouldHave(text(sdf.format(testCertificate.getNotBefore())));
        $(CARD_VALID_TO).shouldHave(text(sdf.format(testCertificate.getNotAfter())));
    }

    @And("user is able to view the certificate")
    public void userIsAbleToViewTheCertificate() {
        $(BTN_VIEW_CERTIFICATE).click();
        $(CERTIFICATE_DETAILS_COMPONENT).shouldBe(visible);
    }

    @When("user navigates to CA settings")
    public void userNavigatesToCASettings() {
        $(TAB_CA_SETTINGS).click();
    }

    @Then("CA settings are shown")
    public void caSettingsAreShown() {
        $(CARD_CERT_PROFILE).shouldHave(text(CERTIFICATE_PROFILE));
        $(CARD_TLS_AUTH).shouldHave(text("False"));
    }

    @And("user can change the certificate profile")
    public void userCanChangeTheCertificateProfile() {
        $(BTN_EDIT_CERT_PROFILE).click();

        $(INPUT_EDIT_CERT_PROFILE).sendKeys(CONTROL, "a");
        $(INPUT_EDIT_CERT_PROFILE).sendKeys(DELETE);
        $(INPUT_EDIT_CERT_PROFILE).setValue(NEW_CERTIFICATE_PROFILE);

        $(BTN_DIALOG_SAVE).shouldBe(Condition.enabled).click();

        $(SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(BTN_CLOSE_SNACKBAR).click();

        $(CARD_CERT_PROFILE).shouldHave(text(NEW_CERTIFICATE_PROFILE));
    }

    @And("user can change the TLS Auth setting")
    public void userCanChangeTheTLSAuthSetting() {
        $(BTN_EDIT_TLS_AUTH).click();

        $(CHECKBOX_CHANGE_TLS_AUTH).click();
        $(BTN_DIALOG_SAVE).shouldBe(Condition.enabled).click();

        $(SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(BTN_CLOSE_SNACKBAR).click();

        $(CARD_TLS_AUTH).shouldHave(text("True"));
    }
}
