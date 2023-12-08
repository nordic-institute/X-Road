/*
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

public class SettingsTlsCertificatesPageObj {

    public final CertificateViewPageObj certificateView = new CertificateViewPageObj();
    public final CreateCsrDialog createCsrDialog = new CreateCsrDialog();

    public SelenideElement tlsCertificatesView() {
        return $x("//div[@data-test='tls-certificates-view']");
    }

    public SelenideElement certificateHash() {
        return $x("//div[@data-test='view-management-service-certificate']");
    }

    public SelenideElement btnDownloadCertificate() {
        return $x("//button[@data-test='download-management-service-certificate']");
    }

    public SelenideElement btnUploadCertificate() {
        return $x("//button[@data-test='upload-management-service-certificate']");
    }

    public SelenideElement btnCreateKeyCertificate() {
        return $x("//button[@data-test='management-service-certificate-generateKey']");
    }

    public SelenideElement btnCreateCsr() {
        return $x("//button[@data-test='management-service-certificate-generateCsr']");
    }

    public static class CreateCsrDialog {
        public SelenideElement distinguishedName() {
            var xpath = "//div[@data-test='enter-distinguished-name']";
            return $x(xpath);
        }

        public SelenideElement btnGenerateCsr() {
            var xpath = "//button[@data-test='dialog-save-button']";
            return $x(xpath);
        }

        public SelenideElement btnCancel() {
            var xpath = "//button[@data-test='dialog-cancel-button']";
            return $x(xpath);
        }

        public SelenideElement dialog() {
            var xpath = "//div[@data-test='dialog-simple']";
            return $x(xpath);
        }
    }
}
