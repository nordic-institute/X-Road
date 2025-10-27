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

package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

public class TlsKeyPageObj {

    public final GenerateTlsKeyAndCertificateDialog generateTlsKeyDialog = new GenerateTlsKeyAndCertificateDialog();
    public final GenerateTlsCsrView generateTlsCsrView = new GenerateTlsCsrView();

    public SelenideElement buttonGenerateKey() {
        return $x("//button[@data-test='management-service-certificate-generateKey']");
    }

    public SelenideElement inputTlsCertificateFile() {
        return $x("//input[@type='file']");
    }

    public SelenideElement buttonDownloadCert() {
        return $x("//button[@data-test='download-management-service-certificate']");
    }

    public SelenideElement buttonGenerateCsr() {
        return $x("//button[@data-test='management-service-certificate-generateCsr']");
    }

    public SelenideElement internalTlsCertificate() {
        return $x("//div[@data-test='view-management-service-certificate']");
    }

    public static class GenerateTlsKeyAndCertificateDialog {
        public SelenideElement btnConfirm() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public class GenerateTlsCsrView {
        public SelenideElement distinguishedNameInput() {
            return $x("//div[@data-test='enter-distinguished-name']");
        }

        public SelenideElement btnGenerateCsr() {
            return $x("//button[@data-test='dialog-save-button']");
        }

        public SelenideElement btnDone() {
            return $x("//button[@data-test='generate-internal-csr-done-button']");
        }

    }
}
