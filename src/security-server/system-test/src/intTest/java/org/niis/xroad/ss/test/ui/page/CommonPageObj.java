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
import static java.lang.String.format;

/**
 * Common page objects which can be found in any page.
 */
@SuppressWarnings("InnerClassMayBeStatic")
public class CommonPageObj {
    public final Dialog dialog = new Dialog();
    public final Menu menu = new Menu();
    public final SubMenu subMenu = new SubMenu();
    public final SnackBar snackBar = new SnackBar();
    public final Alerts alerts = new Alerts();
    public final Form form = new Form();

    public class Menu {

        public SelenideElement usernameButton() {
            return $x("//button[@data-test='username-button']");
        }

        public SelenideElement logout() {
            return $x("//div[@data-test='logout-list-tile']");
        }

        public SelenideElement clientsTab() {
            return $x(getTabXpath("Clients"));
        }

        public SelenideElement keysAndCertificatesTab() {
            return $x(getTabXpath("Keys and certificates"));
        }

        public SelenideElement diagnosticsTab() {
            return $x(getTabXpath("Diagnostics"));
        }

        public SelenideElement settingsTab() {
            return $x(getTabXpath("Settings"));
        }

        private String getTabXpath(String tabName) {
            var xpath = "//div[contains(@class, 'main-tabs')]//a[contains(@class,'v-tab')]//span[text()='%s']";
            return format(xpath, tabName);
        }
    }

    public class SubMenu {

        public SelenideElement backupAndRestoresTab() {
            return $x("//*[@data-test='backupandrestore-tab-button']");
        }

        public SelenideElement systemParametersTab() {
            return $x("//*[@data-test='system-parameters-tab-button']");
        }

        public SelenideElement apiKeysTab() {
            return $x("//*[@data-test='api-key-tab-button']");
        }

        public SelenideElement securityServerTLSKeyTab() {
            return $x("//*[@data-test='ss-tls-certificate-tab-button']");
        }

    }

    public class Form {

        public SelenideElement inputErrorMessage() {
            return $x("//div[contains(@class, 'v-messages__message')]");
        }

        public SelenideElement inputErrorMessage(String message) {
            return $x(format("//div[contains(@class, 'v-messages__message') and text()='%s']", message));
        }
    }

    public class Dialog {
        public SelenideElement title() {
            return $x("//span[@data-test='dialog-title']");
        }

        public SelenideElement btnCancel() {
            return $x("//button[@data-test='dialog-cancel-button']");
        }

        public SelenideElement btnSave() {
            return $x("//button[@data-test='dialog-save-button']");
        }

        public SelenideElement btnClose() {
            return $x("//button[@data-test='close']");
        }
    }

    public class SnackBar {
        public SelenideElement success() {
            return $x("//div[@data-test='success-snackbar']");
        }

        public SelenideElement btnClose() {
            return $x("//button[@data-test='close-snackbar']");
        }
    }

    public class Alerts {
        public SelenideElement alert(final String text) {
            return $x(format("//div[@data-test='contextual-alert']//div[contains(text(), '%s')]", text));
        }

        public SelenideElement btnClose() {
            return $x("//div[@data-test='contextual-alert']//button[@data-test='close-alert']");
        }
    }

    public SelenideElement btnSessionExpired() {
        return $x("//button[@data-test='session-expired-ok-button']");
    }
}


