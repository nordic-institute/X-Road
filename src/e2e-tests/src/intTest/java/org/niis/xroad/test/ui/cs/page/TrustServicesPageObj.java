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
package org.niis.xroad.test.ui.cs.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;
import static org.openqa.selenium.By.xpath;

@SuppressWarnings("InnerClassMayBeStatic")
public class TrustServicesPageObj {
    public final CertServiceDetails certServiceDetails = new CertServiceDetails();
    public final AddDialog addDialog = new AddDialog();
    public final AddCaSettingsDialog addCaSettingsDialog = new AddCaSettingsDialog();
    public final CertificateViewPageObj certificateView = new CertificateViewPageObj();

    public SelenideElement btnAddCertificationService() {
        return $x("//button[@data-test='add-certification-service']");
    }

    public SelenideElement tableServices() {
        return $x("//div[@data-test='certification-services']//table");
    }

    public SelenideElement tableServicesRowOf(String name) {
        var xpath = "./tbody/tr/td/div[contains(text(), '%s')]";
        return tableServices().find(xpath(String.format(xpath, name)));
    }

    public SelenideElement tableServicesCol(int colIndex) {
        var xpath = "./thead/tr/th[%d]";
        return tableServices().find(xpath(String.format(xpath, colIndex)));
    }


    public SelenideElement cardSubjectDn() {
        return $x("//div[@data-test='subject-distinguished-name-card']/div[2]/div");
    }

    public SelenideElement cardIssuerDn() {
        return $x("//div[@data-test='issuer-distinguished-name-card']/div[2]/div");
    }

    public SelenideElement cardValidFrom() {
        return $x("//div[@data-test='valid-from-card']/div[2]/div");
    }

    public SelenideElement cardValidTo() {
        return $x("//div[@data-test='valid-to-card']/div[2]/div");
    }


    public class CertServiceDetails {
        public final CaSettings caSettings = new CaSettings();
        public final IntermediateCasPageObj intermediaCas = new IntermediateCasPageObj();

        public SelenideElement tabSettings() {
            return $x("//div[@id='certification-service-view']//div[@role='tablist']//a[contains(text(), 'CA Settings')]");
        }

        public SelenideElement tabIntermediateCas() {
            return $x("//div[@id='certification-service-view']//div[@role='tablist']//a[contains(text(), 'Intermediate CAs')]");
        }

        public SelenideElement tabOcspResponders() {
            return $x("//div[@id='certification-service-view']//div[@role='tablist']//a[contains(text(), 'OCSP Responders')]");
        }

        public SelenideElement btnViewCertificate() {
            return $x("//button[@data-test='view-certificate-button']");
        }


        public class CaSettings {
            public SelenideElement cardTlsAuth() {
                return $x("//div[@data-test='tls-auth-card']/div[2]/div");
            }

            public SelenideElement cardCertProfile() {
                return $x("//div[@data-test='cert-profile-card']/div[2]/div");
            }

            public SelenideElement btnEditTlsAuth() {
                return $x("//div[@data-test='tls-auth-card']//button[@data-test='info-card-edit-button']");
            }

            public SelenideElement btnEditCertProfile() {
                return $x("//div[@data-test='cert-profile-card']//button[@data-test='info-card-edit-button']");
            }

            public SelenideElement checkboxTlsAuth() {
                return $x("//div[@data-test='dialog-simple']//input[@role='checkbox']/parent::div/following-sibling::label");
            }

            public SelenideElement inputCertProfile() {
                return $x("//div[@data-test='dialog-simple']//input[@data-test='cert-profile-input']");
            }
        }

    }

    public class AddDialog {
        public SelenideElement inputFile() {
            return $x("//input[@type='file']");
        }
    }

    public class AddCaSettingsDialog {
        public SelenideElement inputCertificateProfile() {
            return $x("//input[@data-test='cert-profile-input']");
        }
    }
}
