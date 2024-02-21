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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import lombok.RequiredArgsConstructor;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;
import static java.lang.String.format;

public class KeyAndCertPageObj {
    private static final String X_FOLLOWING = "//following::";
    private static final String X_TOKEN_EXPANDABLE = "//div[@class='exp-wrapper expandable']"
            + "//div[span[text()='Token: %s']]";
    private static final String X_TOKEN_EXPANDABLE_W_FOLLOWING = X_TOKEN_EXPANDABLE + X_FOLLOWING;

    public final TokenLoginDialog tokenLoginDialog = new TokenLoginDialog();
    public final TokenLogoutDialog tokenLogoutDialog = new TokenLogoutDialog();
    public final TokenEdit tokenEdit = new TokenEdit();
    public final TlsKey tlsKey = new TlsKey();
    public final AddKeyWizardDetails addKeyWizardDetails = new AddKeyWizardDetails();
    public final AddKeyWizardCsrDetails addKeyWizardCsrDetails = new AddKeyWizardCsrDetails();
    public final AddKeyWizardGenerate addKeyWizardGenerate = new AddKeyWizardGenerate();

    public static class TokenLoginDialog {
        public SelenideElement inputPin() {
            return $x("//input[@name='tokenPin']");
        }

        public SelenideElement btnLogin() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public static class TokenEdit {
        public SelenideElement alertTokenPolicyEnabled() {
            return $x("//*[@data-test='alert-token-policy-enabled']");
        }

        public SelenideElement btnChangeToken() {
            return $x("//*[@data-test='token-open-pin-change-link']");
        }
    }

    public static class TokenLogoutDialog {
        public SelenideElement btnLogout() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public static class TlsKey {

        public SelenideElement buttonGenerateKey() {
            return $x("//*[contains(@data-test, 'security-server-tls-certificate-generate-key-button')]");
        }

        public SelenideElement buttonExportCert() {
            return $x("//*[contains(@data-test, 'security-server-tls-certificate-export-certificate-button')]");
        }
    }

    public Section section(String token) {
        return new Section(token);
    }

    @RequiredArgsConstructor
    public static class Section {
        private final String token;

        public SelenideElement tokenLabel() {
            return $x(format(X_TOKEN_EXPANDABLE, token));
        }

        public SelenideElement tokenEditButton() {
            return $x("//button[@data-test='token-icon-button'][1]");
        }

        public SelenideElement tokenLabeledKey(String label) {
            return $x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "table[.//th[contains(@class, 'title-col')]]//span[contains(text(), '%s')]",
                    token, label));
        }

        public ElementsCollection tokenAuthKeyRows() {
            return $$x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "div[@data-test='auth-keys-table']//i[contains(@class,'icon-Certificate')]",
                    token));
        }

        public SelenideElement tokenLabeledKeyGenerateCsrButton(String keyLabel) {
            return $x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING
                            + "tr[@data-test='key-row' and td//span[text() = '%s']]//button[@data-test='generate-csr-button']",
                    token, keyLabel));
        }

        public ElementsCollection labeledKeyCsrRows(String keyLabel) {
            return $$x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING
                            + "tr[@data-test='key-row' and td//span[text() = '%s']]//following-sibling::"
                            + "tr[td[@class='td-name'] and //div[contains(text(), 'Request')]]",
                    token, keyLabel));
        }

        public ElementsCollection tokenSignKeyRows() {
            return $$x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "div[@data-test='sign-keys-table']//i[contains(@class,'icon-Certificate')]",
                    token));
        }

        public SelenideElement btnLogin() {
            return $x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "button[@data-test='token-login-button']", token));
        }

        public SelenideElement btnLogout() {
            return $x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "button[@data-test='token-logout-button']", token));
        }

        public SelenideElement addSigningKey() {
            return $x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "button[@data-test='token-add-key-button']", token));
        }

        public SelenideElement btnDeleteAuthCsrByPos(int pos) {
            return $x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "div[@data-test='auth-keys-table']"
                    + "//tr//button[@data-test='delete-csr-button'][%d]", token, pos));
        }

        public SelenideElement btnDeleteSignCsrByPos(int pos) {
            return $x(format(X_TOKEN_EXPANDABLE_W_FOLLOWING + "div[@data-test='sign-keys-table']"
                    + "//tr//button[@data-test='delete-csr-button'][%d]", token, pos));
        }

        public SelenideElement btnImportCert() {
            return $x("//button[@data-test='token-import-cert-button']");
        }

        public SelenideElement inputCert() {
            return $x("//input[@type='file']");
        }

        public SelenideElement keyLabelByName(String label) {
            return $x(format("//div[@class ='name-wrap-top']//div[contains(@class,'identifier-wrap')]//span[text() = '%s']", label));
        }

        public SelenideElement keyStatusByLabel(String label) {
            return $x(format("//tbody[ tr/td/div[@class ='name-wrap-top']//div[contains(@class,'identifier-wrap')]//span[text() = '%s']]"
                    + "//div[@class='status-text']", label));
        }

    }

    public static class AddKeyWizardDetails {

        public SelenideElement nextButton() {
            return $x("//button[@data-test='next-button']");
        }

        public SelenideElement cancelButton() {
            return $x("(//button[@data-test='cancel-button'])[1]");
        }

        public SelenideElement keyLabel() {
            return $x("//div[@data-test='key-label-input']");
        }

    }

    public static class AddKeyWizardCsrDetails {
        public SelenideElement continueButton() {
            return $x("//button[@data-test='save-button']");
        }

        public SelenideElement previousButton() {
            return $x("//button[@data-test='previous-button']");
        }

        public SelenideElement csrUsage() {
            return $x("//div[@data-test='csr-usage-select']");
        }


        public SelenideElement csrService() {
            return $x("//div[@data-test='csr-certification-service-select']");
        }


        public SelenideElement csrFormat() {
            return $x("//div[@data-test='csr-format-select']");
        }

        public SelenideElement csrClient() {
            return $x("//div[@data-test='csr-client-select']");
        }
    }

    public static class AddKeyWizardGenerate {
        public SelenideElement doneButton() {
            return $x("(//button[@data-test='save-button'])[2]");
        }

        public SelenideElement cancelButton() {
            return $x("(//button[@data-test='cancel-button'])[3]");
        }

        public SelenideElement generateButton() {
            return $x("//button[@data-test='generate-csr-button']");
        }

        public SelenideElement organizationName() {
            return $x("//div[@data-test='dynamic-csr-input_O']");
        }

        public SelenideElement serverDNS() {
            return $x("//div[@data-test='dynamic-csr-input_CN']");
        }
    }

}
