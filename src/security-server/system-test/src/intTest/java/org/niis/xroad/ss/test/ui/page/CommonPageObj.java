/*
 * The MIT License
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
package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.SelenideElement;
import org.niis.xroad.test.framework.core.ui.page.AbstractCommonPageObj;

import static com.codeborne.selenide.Selenide.$x;

public class CommonPageObj extends AbstractCommonPageObj<CommonPageObj.Menu, CommonPageObj.SubMenu> {

    public static class Menu extends org.niis.xroad.test.framework.core.ui.page.component.Menu {
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
    }

    public static class SubMenu extends org.niis.xroad.test.framework.core.ui.page.component.SubMenu {
        public SelenideElement backupAndRestoresTab() {
            return $x("//*[@data-test='backup-and-restore-tab-button']");
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

        public SelenideElement adminUsersTab() {
            return $x("//*[@data-test='admin-users-tab-button']");
        }

        public SelenideElement trafficTab() {
            return $x("//*[@data-test='diagnostics-traffic-tab-button']");
        }
    }

    public CommonPageObj() {
        super(new Menu(), new SubMenu());
    }

}
