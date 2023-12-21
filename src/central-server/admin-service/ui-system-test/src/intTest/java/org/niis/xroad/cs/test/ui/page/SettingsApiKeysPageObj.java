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

@SuppressWarnings("InnerClassMayBeStatic")
public class SettingsApiKeysPageObj {
    public final ApiKeysPageWizard wizard = new ApiKeysPageWizard();

    public SelenideElement apiKeysView() {
        return $x("//div[@data-test='api-keys-view']");
    }

    public SelenideElement btnCreateApiKey() {
        return $x("//button[@data-test='api-key-create-key-button']");
    }

    public SelenideElement apiKeyRow(String apiKeyId) {
        var xpath = "//div[@data-test='api-keys-view']//div[contains(text(), '%s')]";
        return $x(String.format(xpath, apiKeyId));
    }

    public SelenideElement apiKeyRoles(String apiKeyId) {
        var xpath = "//span[@data-test='api-key-row-%s-roles']";
        return $x(String.format(xpath, apiKeyId));
    }

    public SelenideElement btnRevokeApiKey(String apiKeyId) {
        var xpath = "//button[@data-test='api-key-row-%s-revoke-button']";
        return $x(String.format(xpath, apiKeyId));
    }

    public SelenideElement btnEditApiKey(String apiKeyId) {
        var xpath = "//button[@data-test='api-key-row-%s-edit-button']";
        return $x(String.format(xpath, apiKeyId));
    }

    public class ApiKeysPageWizard {

        public SelenideElement asElement() {
            return $x("//div[@data-test='create-api-key-stepper-view']");
        }

        public SelenideElement checkboxRole(String role) {
            var xpath = "//div[@data-test='role-%s-checkbox']";
            return $x(String.format(xpath, role));
        }

        public SelenideElement createdApiKeyId() {
            return $x("//div[@data-test='created-apikey-id']");
        }

        public SelenideElement createdApiKey() {
            return $x("//div[@data-test='created-apikey']");
        }

        public SelenideElement btnCreateKey() {
            return $x("//button[@data-test='create-key-button']");
        }

        public SelenideElement btnNext() {
            return $x("//button[@data-test='next-button']");
        }

        public SelenideElement btnPrevious() {
            return $x("//button[@data-test='previous-button']");
        }

        public SelenideElement btnFinish() {
            return $x("//button[@data-test='finish-button']");
        }
    }
}
