/**
 * The MIT License
 * <p>
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
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;
import static org.openqa.selenium.By.xpath;

@SuppressWarnings("InnerClassMayBeStatic")
public class TimestampingServicesPageObj {
    public final AddEditDialog addEditDialog = new AddEditDialog();
    public final CertificateViewPageObj certificateView = new CertificateViewPageObj();

    public SelenideElement btnAddTimestampingService() {
        return $x("//button[@data-test='add-timestamping-service']");
    }

    public SelenideElement table() {
        return $x("//div[@data-test='timestamping-services-table']//table");
    }

    public SelenideElement tableWithHeaders(String url, String interval, String cost) {
        var xpath = "./thead//tr[th/span[contains(text(), '%s')] and th/span[contains(text(), '%s')] and th/span[contains(text(), '%s')]]";
        return table().find(xpath(String.format(xpath, url, interval, cost)));
    }

    public SelenideElement tableServicesRowOf(String url) {
        var xpath = "./tbody/tr/td[contains(text(), '%s')]";
        return table().find(xpath(String.format(xpath, url)));
    }

    public SelenideElement tableLoading() {
        return $x("//tr[@class='v-data-table__progress']");
    }

    public SelenideElement buttonLoading() {
        return $x("//span[@class='v-btn__loader']");
    }

    public SelenideElement tableServicesCol(int colIndex) {
        var xpath = "./thead/tr/th[%d]";
        return table().find(xpath(String.format(xpath, colIndex)));
    }

    public SelenideElement btnViewTimestampingService(String url) {
        var xpath = "./..//td/div/button[@data-test='view-timestamping-service-certificate']";
        return tableServicesRowOf(url).find(xpath(xpath));
    }

    public SelenideElement btnEditTimestampingService(String url) {
        var xpath = "./..//td/div/button[@data-test='edit-timestamping-service']";
        return tableServicesRowOf(url).find(xpath(xpath));
    }

    public SelenideElement btnDeleteTimestampingService(String url) {
        var xpath = "./..//td/div/button[@data-test='delete-timestamping-service']";
        return tableServicesRowOf(url).find(xpath(xpath));
    }

    public class AddEditDialog {
        public SelenideElement inputUrl() {
            return $x("//input[@data-test='timestamping-service-url-input']");
        }

        public SelenideElement inputCertificateFile() {
            return $x("//input[@type='file']");
        }

        public SelenideElement btnViewCertificate() {
            return $x("//div/div/button[@data-test='view-timestamping-service-certificate']");
        }

        public SelenideElement btnUploadCertificate() {
            return $x("//button[@data-test='upload-timestamping-service-certificate']");
        }
    }
}
