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
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.cs.test.ui.page.OcspRespondersPageObj;
import org.niis.xroad.cs.test.ui.page.TrustServicesPageObj;
import org.niis.xroad.cs.test.ui.utils.CertificateUtils;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.cs.test.ui.constants.Constants.CN_SUBJECT_PREFIX;

public class TrustServicesOcspRespondersStepDefs extends BaseUiStepDefs {
    private final TrustServicesPageObj trustServicesPageObj = new TrustServicesPageObj();
    private final OcspRespondersPageObj ocspRespondersPageObj = new OcspRespondersPageObj();

    @Step("OCSP responders tab is selected")
    public void ocspRespondersTabIsSelected() {
        trustServicesPageObj.certServiceDetails.tabOcspResponders().scrollIntoView(false).click();
    }

    @Step("OCSP responder with URL {} is added")
    public void newOcspResponderIsAdded(String url) throws Exception {
        ocspRespondersPageObj.btnAddOcspResponder().click();
        commonPageObj.dialog.btnCancel().should(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldNotBe(Condition.enabled);

        final byte[] certificate = CertificateUtils.generateAuthCert(CN_SUBJECT_PREFIX + url);

        ocspRespondersPageObj.addEditDialog.inputCertificateFile().uploadFile(CertificateUtils.getAsFile(certificate));
        ocspRespondersPageObj.addEditDialog.inputOcspResponderUrl().setValue(url);
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("OCSP responder table is visible")
    public void ocspResponderTableIsVisible() {
        ocspRespondersPageObj.table().shouldBe(Condition.enabled);
    }

    @Step("OCSP responder with URL {} is visible in the OCSP responders list")
    public void ocspResponderIsVisibleInTheOcspRespondersList(String url) {
        ocspRespondersPageObj.tableRowOf(url).should(appear);
    }

    @Step("User is able to sort OCSP responders by URL")
    public void userIsAbleToSortOcspRespondersByUrl() {
        var tableHeader = ocspRespondersPageObj.tableHeader();
        Assertions.assertEquals("none", tableHeader.getAttribute("aria-sort"));
        tableHeader.click();
        Assertions.assertEquals("ascending", tableHeader.getAttribute("aria-sort"));
        tableHeader.click();
        Assertions.assertEquals("descending", tableHeader.getAttribute("aria-sort"));
    }

    @Step("User is able to view the certificate of OCSP responder with URL {}")
    public void userIsAbleToViewTheCertificate(String url) {
        ocspRespondersPageObj.btnViewOcspResponder(url).click();
        ocspRespondersPageObj.certificateView.certificateDetails().shouldBe(visible);
    }

    @Step("User is able click Edit button in OCSP responder with URL {}")
    public void userIsAbleToEditOcspResponder(String url) {
        ocspRespondersPageObj.btnEditOcspResponder(url).click();
    }

    @Step("User is able change the URL to new URL {}")
    public void userIsAbleEditTheUrl(String newUrl) {
        commonPageObj.dialog.btnCancel().should(Condition.enabled);
        commonPageObj.dialog.btnSave().should(Condition.enabled);

        clearInput(ocspRespondersPageObj.addEditDialog.inputOcspResponderUrl());

        commonPageObj.dialog.btnSave().shouldNotBe(Condition.enabled);

        ocspRespondersPageObj.addEditDialog.inputOcspResponderUrl().setValue(newUrl);
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User is able view the certificate of OCSP responder")
    public void userIsAbleViewTheCertificate() {
        ocspRespondersPageObj.addEditDialog.btnViewCertificate().click();
        ocspRespondersPageObj.certificateView.certificateDetails().shouldBe(visible);
        ocspRespondersPageObj.certificateView.btnClose().click();
    }

    @Step("User is able change the certificate of OCSP responder with URL {}")
    public void userIsAbleChangeTheCertificate(String url) throws Exception {
        ocspRespondersPageObj.addEditDialog.btnUploadCertificate().click();

        final byte[] certificate = CertificateUtils.generateAuthCert(CN_SUBJECT_PREFIX + url);
        ocspRespondersPageObj.addEditDialog.inputCertificateFile().uploadFile(CertificateUtils.getAsFile(certificate));
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User is able to click delete button in OCSP responder with URL {}")
    public void userIsAbleToDeleteOcspResponder(String url) {
        ocspRespondersPageObj.btnDeleteOcspResponder(url).click();

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("OCSP responder with URL {} should removed in list")
    public void ocspResponderShouldRemovedInList(String url) {
        ocspRespondersPageObj.tableRowOf(url).shouldNotBe(visible);
    }
}
