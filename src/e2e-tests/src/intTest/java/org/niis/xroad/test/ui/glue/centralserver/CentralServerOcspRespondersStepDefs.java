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
import io.cucumber.java.en.Step;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_CLOSE_SNACKBAR;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_CLOSE_X;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_CANCEL;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_SAVE;
import static org.niis.xroad.test.ui.glue.constants.Constants.INPUT_FILE_UPLOAD;
import static org.niis.xroad.test.ui.glue.constants.Constants.SNACKBAR_SUCCESS;
import static org.niis.xroad.test.ui.utils.CertificateUtils.generateAuthCert;
import static org.niis.xroad.test.ui.utils.CertificateUtils.getAsFile;
import static org.openqa.selenium.By.xpath;
import static org.openqa.selenium.Keys.CONTROL;
import static org.openqa.selenium.Keys.DELETE;

public class CentralServerOcspRespondersStepDefs extends BaseUiStepDefs {

    private static final By TAB_OCSP_RESPONDERS =
            xpath("//a[contains(text(), \"OCSP Responders\") and contains(@class, \"v-tab\")]");
    private static final By BTN_ADD_OCSP_RESPONDER = xpath("//button[@data-test=\"add-ocsp-responder-button\"]");
    private static final By INPUT_OCSP_RESPONDER_URL = xpath("//input[@data-test=\"ocsp-responder-url-input\"]");
    private static final String TABLE_OCSP_RESPONDERS = "//div[@data-test=\"ocsp-responders-table\"]//table";
    private static final String TABLE_ROW_OCSP_RESPONDER = TABLE_OCSP_RESPONDERS + "/tbody/tr/td/div[contains(text(), \"%s\")]";
    private static final By TABLE_URL_HEADER = xpath(TABLE_OCSP_RESPONDERS + "/thead[2]/tr/th[1]");
    private static final By CERTIFICATE_DETAILS_COMPONENT = xpath("//div[contains(@class, \"certificate-details-wrapper\")]");
    private static final String BTN_VIEW_OCSP_RESPONDER_CERTIFICATE =
            TABLE_ROW_OCSP_RESPONDER + "/../..//td/div/button[@data-test=\"view-ocsp-responder-certificate\"]";
    private static final String BTN_EDIT_OCSP_RESPONDER =
            TABLE_ROW_OCSP_RESPONDER + "/../..//td/div/button[@data-test=\"edit-ocsp-responder\"]";
    private static final By BTN_VIEW_CERTIFICATE = xpath("//div/div/button[@data-test=\"view-ocsp-responder-certificate\"]");
    private static final By BTN_UPLOAD_CERTIFICATE = xpath("//button[@data-test=\"upload-ocsp-responder-certificate\"]");
    private static final String BTN_DELETE_OCSP_RESPONDER =
            TABLE_ROW_OCSP_RESPONDER + "/../..//td/div/button[@data-test=\"delete-ocsp-responder\"]";

    @Step("OCSP responders tab is selected")
    public void ocspRespondersTabIsSelected() {
        $(TAB_OCSP_RESPONDERS).click();
    }

    @Step("OCSP responder with URL {} is added")
    public void newOcspResponderIsAdded(String url) throws Exception {
        $(BTN_ADD_OCSP_RESPONDER).click();
        $(BTN_DIALOG_CANCEL).should(Condition.enabled);
        $(BTN_DIALOG_SAVE).shouldNotBe(Condition.enabled);

        final byte[] certificate = generateAuthCert(url);

        $(INPUT_FILE_UPLOAD).uploadFile(getAsFile(certificate));
        $(INPUT_OCSP_RESPONDER_URL).setValue(url);
        $(BTN_DIALOG_SAVE).click();

        $(SNACKBAR_SUCCESS).shouldBe(visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Step("OCSP responder table is visible")
    public void ocspResponderTableIsVisible() {
        $(xpath(TABLE_OCSP_RESPONDERS)).shouldBe(Condition.enabled);
    }

    @Step("OCSP responder with URL {} is visible in the OCSP responders list")
    public void ocspResponderIsVisibleInTheOcspRespondersList(String url) {
        $(xpath(format(TABLE_ROW_OCSP_RESPONDER, url))).should(appear);
    }

    @Step("User is able to sort OCSP responders by URL")
    public void userIsAbleToSortOcspRespondersByUrl() {
        assertEquals("none", $(TABLE_URL_HEADER).getAttribute("aria-sort"));
        $(TABLE_URL_HEADER).click();
        assertEquals("ascending", $(TABLE_URL_HEADER).getAttribute("aria-sort"));
        $(TABLE_URL_HEADER).click();
        assertEquals("descending", $(TABLE_URL_HEADER).getAttribute("aria-sort"));
    }

    @Step("User is able to view the certificate of OCSP responder with URL {}")
    public void userIsAbleToViewTheCertificate(String url) {
        $(xpath(format(BTN_VIEW_OCSP_RESPONDER_CERTIFICATE, url))).click();
        $(CERTIFICATE_DETAILS_COMPONENT).shouldBe(visible);
    }

    @Step("User is able click Edit button in OCSP responder with URL {}")
    public void userIsAbleToEditOcspResponder(String url) {
        $(xpath(format(BTN_EDIT_OCSP_RESPONDER, url))).click();
    }

    @Step("User is able change the URL to new URL {}")
    public void userIsAbleEditTheUrl(String newUrl) {
        $(BTN_DIALOG_CANCEL).should(Condition.enabled);
        $(BTN_DIALOG_SAVE).should(Condition.enabled);

        clearInput($(INPUT_OCSP_RESPONDER_URL));

        $(BTN_DIALOG_SAVE).shouldNotBe(Condition.enabled);

        $(INPUT_OCSP_RESPONDER_URL).setValue(newUrl);
        $(BTN_DIALOG_SAVE).click();

        $(SNACKBAR_SUCCESS).shouldBe(visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Step("User is able view the certificate of OCSP responder")
    public void userIsAbleViewTheCertificate() {
        $(BTN_VIEW_CERTIFICATE).click();
        $(CERTIFICATE_DETAILS_COMPONENT).shouldBe(visible);
        $(BTN_CLOSE_X).click();
    }

    @Step("User is able change the certificate of OCSP responder with URL {}")
    public void userIsAbleChangeTheCertificate(String url) throws Exception {
        $(BTN_UPLOAD_CERTIFICATE).click();

        final byte[] certificate = generateAuthCert(url);
        $(INPUT_FILE_UPLOAD).uploadFile(getAsFile(certificate));
        $(BTN_DIALOG_SAVE).click();

        $(SNACKBAR_SUCCESS).shouldBe(visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Step("User is able to click delete button in OCSP responder with URL {}")
    public void userIsAbleToDeleteOcspResponder(String url) {
        $(xpath(format(BTN_DELETE_OCSP_RESPONDER, url))).click();

        $(BTN_DIALOG_CANCEL).shouldBe(Condition.enabled);
        $(BTN_DIALOG_SAVE).shouldBe(Condition.enabled).click();

        $(SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Step("OCSP responder with URL {} should removed in list")
    public void ocspResponderShouldRemovedInList(String url) {
        $(xpath(format(TABLE_ROW_OCSP_RESPONDER, url))).shouldNotBe(appear);
    }
}
