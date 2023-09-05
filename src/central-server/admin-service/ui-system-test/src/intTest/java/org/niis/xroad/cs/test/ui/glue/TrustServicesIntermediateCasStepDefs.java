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
import org.niis.xroad.cs.test.ui.constants.Constants;
import org.niis.xroad.cs.test.ui.page.IntermediateCasPageObj;
import org.niis.xroad.cs.test.ui.page.TrustServicesPageObj;
import org.niis.xroad.cs.test.ui.utils.CertificateUtils;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.cs.test.ui.constants.Constants.CN_SUBJECT_PREFIX;

public class TrustServicesIntermediateCasStepDefs extends BaseUiStepDefs {
    private final TrustServicesPageObj trustServicesPageObj = new TrustServicesPageObj();
    private final IntermediateCasPageObj intermediateCasPageObj = new IntermediateCasPageObj();

    private X509Certificate testCertificate;

    @Step("Intermediate CAs tab is selected")
    public void intermediateCasTabIsSelected() {
        trustServicesPageObj.certServiceDetails.tabIntermediateCas()
                .scrollIntoView(false)
                .click();
    }

    @Step("Intermediate CA OCSP responders tab is selected")
    public void ocspRespondersTabIsSelected() {
        intermediateCasPageObj.tabOcspResponders().click();
    }

    @Step("Intermediate CA with name {} is added")
    public void newIntermediateCaIsAdded(String name) throws Exception {
        intermediateCasPageObj.btnAdd().click();
        commonPageObj.dialog.btnCancel().should(enabled);
        commonPageObj.dialog.btnSave().shouldNotBe(enabled);

        final byte[] certificate = CertificateUtils.generateAuthCert(CN_SUBJECT_PREFIX + name);

        testCertificate = CertificateUtils.readCertificate(certificate);

        intermediateCasPageObj.inputAddCertFile().uploadFile(CertificateUtils.getAsFile(certificate));
        commonPageObj.dialog.btnSave().click();


        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Intermediate CAs table is visible")
    public void intermediateCasTableIsVisible() {
        intermediateCasPageObj.table().shouldBe(enabled);
    }


    @Step("Intermediate CA with name {} is visible in the Intermediate CA list")
    public void intermediateCaIsVisibleInTheIntermediateCasList(String name) {
        intermediateCasPageObj.tableRowOf(name).should(appear);
    }

    @Step("User is able to sort Intermediate CAs by header column {int}")
    public void userIsAbleToSortByColumn(int headerColumnIndex) {
        var column = intermediateCasPageObj.tableHeaderCol(headerColumnIndex);
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

    @Step("User is able to view the certificate of Intermediate CA with name {}")
    public void userIsAbleToViewTheCertificate(String url) {
        intermediateCasPageObj.btnViewIntermediateCa(url).click();
        intermediateCasPageObj.certificateView.certificateDetails().shouldBe(visible);
    }

    @Step("User is able to click delete button in Intermediate CA with name {}")
    public void userIsAbleToDeleteIntermediateCa(String name) {
        intermediateCasPageObj.btnDeleteIntermediateCa(name).click();

        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Intermediate CA with name {} should be removed in list")
    public void ocspResponderShouldRemovedInList(String url) {
        intermediateCasPageObj.tableRowOf(url).shouldNotBe(visible);
    }

    @Step("Intermediate CA details are visible")
    public void intermediateCaDetailsAreVisible() {
        final SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT);

        trustServicesPageObj.cardSubjectDn().shouldHave(text(testCertificate.getSubjectDN().getName()));
        trustServicesPageObj.cardIssuerDn().shouldHave(text(testCertificate.getIssuerDN().getName()));
        trustServicesPageObj.cardValidFrom().shouldHave(text(sdf.format(testCertificate.getNotBefore())));
        trustServicesPageObj.cardValidTo().shouldHave(text(sdf.format(testCertificate.getNotAfter())));
    }

    @Step("User opens intermediate CA with name {} details")
    public void userOpensIntermediateCaDetails(String name) {
        intermediateCasPageObj.tableRowOf(name).click();
    }
}
