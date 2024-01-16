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
        return $x("//div[@data-test='subject-distinguished-name-card']/div[contains(@class, 'v-card-text')]/div");
    }

    public SelenideElement cardIssuerDn() {
        return $x("//div[@data-test='issuer-distinguished-name-card']/div[contains(@class, 'v-card-text')]/div");
    }

    public SelenideElement cardValidFrom() {
        return $x("//div[@data-test='valid-from-card']/div[contains(@class, 'v-card-text')]//span");
    }

    public SelenideElement cardValidTo() {
        return $x("//div[@data-test='valid-to-card']/div[contains(@class, 'v-card-text')]//span");
    }


    public class CertServiceDetails {
        public final CaSettings caSettings = new CaSettings();
        public final IntermediateCasPageObj intermediaCas = new IntermediateCasPageObj();

        public SelenideElement tabSettings() {
            return $x("//article[@id='certification-service-view']//div[@role='tablist']"
                    + "//a[@data-test='certification-service-settings-tab-button']");
        }

        public SelenideElement tabIntermediateCas() {
            return $x("//article[@id='certification-service-view']//div[@role='tablist']"
                    + "//a[@data-test='certification-service-intermediate-cas-tab-button']");
        }

        public SelenideElement tabOcspResponders() {
            return $x("//article[@id='certification-service-view']//div[@role='tablist']"
                    + "//a[@data-test='certification-service-ocsp-responders-tab-button']");
        }

        public SelenideElement btnViewCertificate() {
            return $x("//button[@data-test='view-certificate-button']");
        }

        public SelenideElement btnDeleteTrustService() {
            return $x("//div[@data-test='delete-trust-service']");
        }

        public class CaSettings {
            public SelenideElement cardTlsAuth() {
                return $x("//div[@data-test='tls-auth-card']/div[contains(@class, 'v-card-text')]/div");
            }

            public SelenideElement cardCertProfile() {
                return $x("//div[@data-test='cert-profile-card']/div[contains(@class, 'v-card-text')]/div");
            }

            public SelenideElement acmeServerDirectoryUrl() {
                return $x("//div[@data-test='cert-acme-card']//div[@data-test='acme-server-directory-url']");
            }

            public SelenideElement acmeServerIpAddress() {
                return $x("//div[@data-test='cert-acme-card']//div[@data-test='acme-server-ip-address']");
            }

            public SelenideElement btnEditTlsAuth() {
                return $x("//div[@data-test='tls-auth-card']//button[@data-test='info-card-edit-button']");
            }

            public SelenideElement btnEditCertProfile() {
                return $x("//div[@data-test='cert-profile-card']//button[@data-test='info-card-edit-button']");
            }

            public SelenideElement btnEditAcme() {
                return $x("//div[@data-test='cert-acme-card']//button[@data-test='info-card-edit-button']");
            }

            public SelenideElement checkboxTlsAuth() {
                return $x("//div[@data-test='dialog-simple']//div[@data-test='tls-auth-checkbox']");
            }

            public SelenideElement inputCertProfile() {
                return $x("//div[@data-test='dialog-simple']//div[@data-test='cert-profile-input']");
            }

            public SelenideElement inputAcmeServerDirectoryUrl() {
                return $x("//div[@data-test='dialog-simple']//div[@data-test='acme-server-directory-url-input']");
            }

            public SelenideElement inputAcmeServerIpAddress() {
                return $x("//div[@data-test='dialog-simple']//div[@data-test='acme-server-ip-address-input']");
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
            return $x("//div[@data-test='cert-profile-input']");
        }

        public SelenideElement checkboxAcme() {
            return $x("//div[@data-test='acme-checkbox']");
        }

        public SelenideElement inputAcmeServerDirectoryUrl() {
            return $x("//div[@data-test='acme-server-directory-url-input']");
        }

        public SelenideElement inputAcmeServerIpAddress() {
            return $x("//div[@data-test='acme-server-ip-address-input']");
        }
    }
}
