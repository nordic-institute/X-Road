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

@SuppressWarnings("InnerClassMayBeStatic")
public class GlobalConfigurationPageObj {
    public final TokenLoginDialog tokenLoginDialog = new TokenLoginDialog();
    public final AddSigningKeyDialog addSigningKeyDialog = new AddSigningKeyDialog();

    public SelenideElement tokenLabel(final String tokenKey) {
        return $x(String.format("//div[@data-test='token-name']/span[contains(text(), 'Token: %s')]", tokenKey));
    }

    public SelenideElement loginButton(final String tokenKey) {
        return tokenLabel(tokenKey)
                .ancestor("[class=exp-header]")
                .$("button[data-test=token-login-button]");
    }

    public SelenideElement addSigningKey(final String tokenKey) {
        return tokenLabel(tokenKey)
                .ancestor(".exp-wrapper")
                .$("button[data-test=token-add-key-button]");
    }

    public SelenideElement internalConfiguration() {
        return $x("//a[@data-test='internal-conf-tab-button']");
    }

    public class TokenLoginDialog {
        public SelenideElement inputPin() {
            return $x("//input[@data-test='token-pin-input']");
        }

        public SelenideElement btnLogin() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }

    public class AddSigningKeyDialog {
        public SelenideElement inputLabel() {
            return $x("//input[@data-test='signing-key-label-input']");
        }

        public SelenideElement btnSave() {
            return $x("//button[@data-test='dialog-save-button']");
        }
    }
}
