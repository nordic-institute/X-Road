/**
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
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;
import static org.openqa.selenium.By.xpath;

@SuppressWarnings("InnerClassMayBeStatic")
public class OcspRespondersPageObj {
    public final AddEditDialog addEditDialog = new AddEditDialog();
    public final CertificateViewPageObj certificateView = new CertificateViewPageObj();

    public SelenideElement btnAddOcspResponder() {
        return $x("//button[@data-test='add-ocsp-responder-button']");
    }

    public SelenideElement table() {
        return $x("//div[@data-test='ocsp-responders-table']//table");
    }

    public SelenideElement tableRowOf(String url) {
        var xpath = "./tbody/tr/td/div[contains(text(), '%s')]";
        return table().find(xpath(String.format(xpath, url)));
    }

    public SelenideElement btnViewOcspResponder(String url) {
        var xpath = "./../..//td/div/button[@data-test='view-ocsp-responder-certificate']";
        return tableRowOf(url).find(xpath(xpath));
    }

    public SelenideElement btnEditOcspResponder(String url) {
        var xpath = "./../..//td/div/button[@data-test='edit-ocsp-responder']";
        return tableRowOf(url).find(xpath(xpath));
    }

    public SelenideElement btnDeleteOcspResponder(String url) {
        var xpath = "./../..//td/div/button[@data-test='delete-ocsp-responder']";
        return tableRowOf(url).find(xpath(xpath));
    }

    public SelenideElement tableHeader() {
        return table().find(xpath("./thead[2]/tr/th[1]"));
    }


    public class AddEditDialog {
        public SelenideElement inputOcspResponderUrl() {
            return $x("//input[@data-test='ocsp-responder-url-input']");
        }

        public SelenideElement inputCertificateFile() {
            return $x("//input[@type='file']");
        }

        public SelenideElement btnViewCertificate() {
            return $x("//div/div/button[@data-test='view-ocsp-responder-certificate']");
        }

        public SelenideElement btnUploadCertificate() {
            return $x("//button[@data-test='upload-ocsp-responder-certificate']");
        }
    }

}
