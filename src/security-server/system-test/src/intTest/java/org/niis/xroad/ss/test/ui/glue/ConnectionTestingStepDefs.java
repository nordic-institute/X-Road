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
package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.ConnectionTestingPageObj;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vRadio;

public class ConnectionTestingStepDefs extends BaseUiStepDefs {
    private final ConnectionTestingPageObj page = new ConnectionTestingPageObj();

    @Step("Global configuration download from {string} status should be failed")
    public void centralServerGlobalConfMessage(String url) {
        page.centralServerGlobalConfMessage(url).shouldNotHave(Condition.partialText("Everything ok"));
    }

    @Step("Global configuration download Test button should be enabled")
    public void centralServerGlobalConfTestButton() {
        page.centralServerGlobalConfTestButton().shouldBe(enabled);
    }

    @Step("Central Server authentication certificate registration service status should be failed")
    public void centralServerAuthCertStatusFailed() {
        page.centralServerAuthCertMessage().shouldHave(Condition.partialText("IO error"));
    }

    @Step("Central Server authentication certificate registration service Test button should be enabled")
    public void centralServerAuthCertTestButton() {
        page.centralServerAuthCertTestButton().shouldBe(enabled);
    }

    @Step("Run test for Management Security Server")
    public void managementServerTest() {
        page.managementServerTestButton().shouldBe(enabled).click();
    }

    @Step("Management Security Server error message should contain {}")
    public void managementServerErrorContains(String message) {
        page.managementServerStatusMessage().shouldHave(Condition.partialText(message));
    }

    @Step("Other Security Server Test button should be {}")
    public void otherSecurityServerTestButton(String state) {
        switch (state) {
            case "enabled" -> page.otherSecurityServerTestButton().shouldBe(enabled);
            case "disabled" -> page.otherSecurityServerTestButton().shouldBe(Condition.disabled);
            default -> throw new IllegalArgumentException("State [" + state + "] is not supported");
        }
    }

    @Step("Current client is set to {}")
    public void selectedClient(String clientId) {
        page.filter.selectedClient().click().selectCombobox(clientId);
    }

    @Step("Service type is set to REST")
    public void selectServiceType() {
        page.radioRestPath().shouldBe(visible, enabled).click();
        vRadio(page.radioSoapPath()).shouldBeUnChecked();
    }

    @Step("Target instance is prefilled with {}")
    public void targetInstance(String instance) {
        page.selectedTargetInstanceInput().shouldHave(Condition.value(instance));
    }

    @Step("Target client is set to {}")
    public void selectedTargetClient(String clientId) {
        page.filter.selectedTargetClient().click().selectCombobox(clientId);
    }

    @Step("Target security server is prefilled with {}")
    public void securityServerId(String server) {
        page.selectedSecurityServerIdInput().shouldHave(Condition.value(server));
    }

    @Step("Run test for Other Security Server")
    public void otherSecurityServerTest() {
        page.otherSecurityServerTestButton().shouldBe(enabled).click();
    }

    @Step("Other Security Server error message should contain {}")
    public void otherServerStatusVisible(String message) {
        page.otherSecurityServerStatusMessage().shouldHave(Condition.partialText(message));
    }
}
