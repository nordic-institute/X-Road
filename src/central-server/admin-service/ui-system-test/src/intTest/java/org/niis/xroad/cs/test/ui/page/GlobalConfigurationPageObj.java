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
import static java.lang.String.format;

@SuppressWarnings("InnerClassMayBeStatic")
public class GlobalConfigurationPageObj {
    private static final String X_TOKEN_EXPANDABLE = "//div[@data-test='token-%s-expandable']";
    public final TokenLoginDialog tokenLoginDialog = new TokenLoginDialog();
    public final TokenLogoutDialog tokenLogoutDialog = new TokenLogoutDialog();
    public final AddSigningKeyDialog addSigningKeyDialog = new AddSigningKeyDialog();
    public final ActivateSigningKeyDialog activateSigningKeyDialog = new ActivateSigningKeyDialog();
    public final DeleteSigningKeyDialog deleteSigningKeyDialog = new DeleteSigningKeyDialog();
    public final AnchorSection anchor = new AnchorSection();
    public final ConfigurationPartsSection configurationParts = new ConfigurationPartsSection();


    public SelenideElement tokenLabel(final String tokenName) {
        return $x(format(X_TOKEN_EXPANDABLE + "//div[@data-test='token-name']", tokenName));
    }

    public SelenideElement loginButton(final String tokenName) {
        return $x(format(X_TOKEN_EXPANDABLE + "//button[@data-test='token-login-button']", tokenName));
    }

    public SelenideElement logoutButton(final String tokenName) {
        return $x(format(X_TOKEN_EXPANDABLE + "//button[@data-test='token-logout-button']", tokenName));
    }

    public SelenideElement addSigningKey(final String tokenName) {
        return $x(format(X_TOKEN_EXPANDABLE + "//button[@data-test='token-add-key-button']", tokenName));
    }

    public SelenideElement signingKeyLabel(final String tokenName, final String keyLabel) {
        return $x(format(X_TOKEN_EXPANDABLE + "//span[@data-test='key-label-text'][contains(text(), '%s')]", tokenName, keyLabel));
    }

    public SelenideElement btnActivateSigningKey(final String tokenName, final String keyLabel) {
        return $x(format(X_TOKEN_EXPANDABLE + "//button[@data-test='key-%s-activate-button']", tokenName, keyLabel));
    }

    public SelenideElement btnDeleteSigningKey(final String tokenName, final String keyLabel) {
        return $x(format(X_TOKEN_EXPANDABLE + "//button[@data-test='key-%s-delete-button']", tokenName, keyLabel));
    }

    public SelenideElement internalConfiguration() {
        return $x("//a[@data-test='internal-conf-tab-button']");
    }

    public SelenideElement externalConfiguration() {
        return $x("//a[@data-test='external-conf-tab-button']");
    }

    public SelenideElement trustedAnchors() {
        return $x("//a[@data-test='trusted-anchors-tab-button']");
    }

    public class TokenLoginDialog {
        public SelenideElement inputPin() {
            return $x("//div[@data-test='token-pin-input']");
        }

        public SelenideElement btnLogin() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public class AddSigningKeyDialog {
        public SelenideElement inputLabel() {
            return $x("//div[@data-test='signing-key-label-input']");
        }

        public SelenideElement btnSave() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public class ActivateSigningKeyDialog {
        public SelenideElement btnActivate() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public class DeleteSigningKeyDialog {
        public SelenideElement btnDelete() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public class TokenLogoutDialog {
        public SelenideElement btnLogout() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public class AnchorSection {
        public SelenideElement btnRecreate() {
            return $x("//button[@data-test='re-create-anchor-button']");
        }

        public SelenideElement btnDownload() {
            return $x("//button[@data-test='download-anchor-button']");
        }

        public SelenideElement txtHash() {
            return $x("//span[@data-test='anchor-hash']");
        }

        public SelenideElement txtCreatedAt() {
            return $x("//span[@data-test='anchor-created-at']");
        }
    }

    public class ConfigurationPartsSection {

        public SelenideElement textContentIdentifier(String contentIdentifier) {
            return $x(format("//span[@data-test='configuration-part-%s']", contentIdentifier));
        }
        public SelenideElement textUpdatedAt(String contentIdentifier) {
            return $x(format("//span[@data-test='configuration-part-%s-updated-at']", contentIdentifier));
        }

        public SelenideElement btnDownload(String contentIdentifier) {
            return $x(format("//button[@data-test='configuration-part-%s-download']", contentIdentifier));
        }

        public SelenideElement btnUpload(String contentIdentifier) {
            return $x(format("//button[@data-test='configuration-part-%s-upload']", contentIdentifier));
        }

        public SelenideElement btnConfirmUpload() {
            return $x("//button[@data-test='dialog-save-button']");
        }

        public SelenideElement inputConfigurationFile() {
            return $x("//input[@type='file']");
        }

        public SelenideElement btnCancelUpload() {
            return $x("//button[@data-test='dialog-cancel-button']");
        }
    }
}
