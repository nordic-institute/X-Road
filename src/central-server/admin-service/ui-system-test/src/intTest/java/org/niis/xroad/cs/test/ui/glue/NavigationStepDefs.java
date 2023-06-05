/**
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
package org.niis.xroad.cs.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.SettingsApiKeysPageObj;

public class NavigationStepDefs extends BaseUiStepDefs {
    private final SettingsApiKeysPageObj settingsApiKeysPageObj = new SettingsApiKeysPageObj();

    @Step("Members tab is selected")
    public void userNavigatesToMembersTab() {
        commonPageObj.menu.memberTab().click();
    }

    @Step("TrustServices tab is selected")
    public void trustServicesTabIsSelected() {
        commonPageObj.menu.trustServices().click();
    }

    @Step("Global configuration tab is selected")
    public void globalConfigurationTabIsSelected() {
        commonPageObj.menu.globalConfiguration().click();
    }

    @Step("Management requests tab is selected")
    public void managementRequestsTabIsSelected() {
        commonPageObj.menu.managementRequestsTab().click();
    }

    @Step("CentralServer Settings tab is selected")
    public void navigateSettingsTab() {
        commonPageObj.menu.settingsTab().click();
    }

    @Step("Security Servers tab is selected")
    public void navigateSecurityServersTab() {
        commonPageObj.menu.securityServersTab().click();
    }

    @Step("System settings sub-tab is selected")
    public void navigateSystemSettingsSubTab() {
        commonPageObj.subMenu.settingsTab().click();
    }

    @Step("Global Resources sub-tab is selected")
    public void navigateGlobalResourcesSubTab() {
        commonPageObj.subMenu.globalResourcesTab().click();
    }

    @Step("API Keys sub-tab is selected")
    public void navigateApiKeysSubTab() {
        commonPageObj.subMenu.apiKeysTab().click();

        settingsApiKeysPageObj.apiKeysView().shouldBe(Condition.visible);
    }

    @Step("Backup and Restore sub-tab is selected")
    public void navigateBackupAndRestoreSubTab() {
        commonPageObj.subMenu.backupAndRestoresTab().click();
    }

    @Step("user clicks back")
    public void userClicksBack() {
        commonPageObj.backLink().click();
    }
}
