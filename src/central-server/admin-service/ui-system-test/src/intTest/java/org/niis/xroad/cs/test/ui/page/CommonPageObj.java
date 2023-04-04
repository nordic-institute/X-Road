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

/**
 * Common page objects which can be found in any page.
 */
@SuppressWarnings("InnerClassMayBeStatic")
public class CommonPageObj {
    public final Dialog dialog = new Dialog();
    public final Menu menu = new Menu();
    public final SubMenu subMenu = new SubMenu();
    public final SnackBar snackBar = new SnackBar();
    public final Button button = new Button();

    public class Menu {

        public SelenideElement memberTab() {
            return $x(getTabXpath("Members"));
        }

        public SelenideElement managementRequestsTab() {
            return $x(getTabXpath("Management Requests"));
        }

        public SelenideElement trustServices() {
            return $x(getTabXpath("Trust Services"));
        }

        public SelenideElement globalConfiguration() {
            return $x(getTabXpath("Global configuration"));
        }

        public SelenideElement settingsTab() {
            return $x(getTabXpath("Settings"));
        }

        public SelenideElement securityServersTab() {
            return $x(getTabXpath("Security Servers"));
        }

        private String getTabXpath(String tabName) {
            var xpath = "//div[contains(@class, 'v-tabs-bar__content')]//*[contains(@class,'v-tab') and contains(text(), '%s')]";
            return String.format(xpath, tabName);
        }
    }

    public class SubMenu {
        public SelenideElement globalResourcesTab() {
            return $x("//*[@data-test='globalresources-tab-button']");
        }

        public SelenideElement backupAndRestoresTab() {
            return $x("//*[@data-test='backupandrestore-tab-button']");
        }

        public SelenideElement settingsTab() {
            return $x("//*[@data-test='systemsettings-tab-button']");
        }

        public SelenideElement apiKeysTab() {
            return $x("//*[@data-test='apikeys-tab-button']");
        }
    }

    public class Dialog {
        public SelenideElement title() {
            return $x("//button[@data-test='dialog-title']");
        }

        public SelenideElement btnCancel() {
            return $x("//button[@data-test='dialog-cancel-button']");
        }

        public SelenideElement btnSave() {
            return $x("//button[@data-test='dialog-save-button']");
        }

        public SelenideElement btnDelete() {
            return $x("//button[@data-test='dialog-delete-button']");
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

    public class Button {
        public SelenideElement btnApprove() {
            return $x("//button[@data-test='approve-button']");
        }
        public SelenideElement btnDecline() {
            return $x("//button[@data-test='decline-button']");
        }
    }
}
