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
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;
import org.niis.xroad.test.framework.core.ui.page.AbstractCommonPageObj;

import static com.codeborne.selenide.Selenide.$x;

/**
 * Common page objects which can be found in any page.
 */
@SuppressWarnings("InnerClassMayBeStatic")
public class CommonPageObj extends AbstractCommonPageObj<CommonPageObj.Menu, CommonPageObj.SubMenu> {

    public CommonPageObj() {
        super(new Menu(), new SubMenu());
    }

    public SelenideElement viewTitle(final String viewTitle) {
        return $x(String.format("//div[@data-test='view-title-text' and text()='%s']", viewTitle));
    }

    public static class Menu extends org.niis.xroad.test.framework.core.ui.page.component.Menu {

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
            return $x(getTabXpath("Global Conf."));
        }

        public SelenideElement settingsTab() {
            return $x(getTabXpath("Settings"));
        }

        public SelenideElement securityServersTab() {
            return $x(getTabXpath("Security Servers"));
        }
    }

    public static class SubMenu extends org.niis.xroad.test.framework.core.ui.page.component.SubMenu {
        public SelenideElement globalResourcesTab() {
            return $x("//*[@data-test='globalresources-tab-button']");
        }

        public SelenideElement backupAndRestoresTab() {
            return $x("//*[@data-test='backup-and-restore-tab-button']");
        }

        public SelenideElement settingsTab() {
            return $x("//*[@data-test='systemsettings-tab-button']");
        }

        public SelenideElement apiKeysTab() {
            return $x("//*[@data-test='apikeys-tab-button']");
        }

        public SelenideElement tlsCertificatesTab() {
            return $x("//*[@data-test='tlscertificates-tab-button']");
        }
    }

    public SelenideElement inputFile() {
        return $x("//input[@type='file']");
    }

    public SelenideElement backLink() {
        return $x("//a[@data-test='navigation-back'][last()]");
    }
}
