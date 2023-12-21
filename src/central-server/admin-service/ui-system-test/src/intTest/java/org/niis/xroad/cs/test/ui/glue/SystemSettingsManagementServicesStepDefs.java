/*
 * The MIT License
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

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.Selenide;
import io.cucumber.java.en.Step;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.cs.test.ui.page.SettingsManagementServicesPageObj;

import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class SystemSettingsManagementServicesStepDefs extends BaseUiStepDefs {

    private final SettingsManagementServicesPageObj settingsManagementServicesPageObj =
            new SettingsManagementServicesPageObj();

    @Step("service provider identifier field should have value {}")
    public void serviceProviderIdentifierFieldHasText(String serviceProviderIdentifier) {
        settingsManagementServicesPageObj.serviceProviderIdentifier().shouldHave(text(serviceProviderIdentifier));
    }

    @Step("service provider identifier field should be empty")
    public void serviceProviderIdentifierFieldIsEmpty() {
        settingsManagementServicesPageObj.serviceProviderIdentifier().shouldBe(empty);
    }

    @Step("service provider name field should have value {}")
    public void serviceProviderNameFieldHasText(String serviceProviderName) {
        settingsManagementServicesPageObj.serviceProviderName().shouldHave(text(serviceProviderName));
    }

    @Step("service provider name field should be empty")
    public void serviceProviderNameFieldIsEmpty() {
        settingsManagementServicesPageObj.serviceProviderName().shouldHave(empty);
    }

    @Step("security server field should have value {}")
    public void securityServerFieldHasText(String securityServer) {
        settingsManagementServicesPageObj.securityServer().shouldHave(text(securityServer));
    }

    @Step("security server field should be empty")
    public void securityServerFieldIsEmpty() {
        settingsManagementServicesPageObj.securityServer().shouldHave(empty);
    }

    @Step("security server field should have {}")
    public void securityServerFieldIsEmpty(String securityServerId) {
        settingsManagementServicesPageObj.securityServer().shouldHave(text(securityServerId));
    }

    @Step("wsdl address field should have value {}")
    public void wsdlAddressFieldHasText(String wsdlAddress) {
        settingsManagementServicesPageObj.wsdlAddress().shouldHave(text(wsdlAddress));
    }

    @Step("management services address field should have value {}")
    public void managementServicesAddressFieldHasText(String centralServerAddress) {
        settingsManagementServicesPageObj.managementServicesAddress().shouldHave(text(centralServerAddress));
    }

    @Step("security server owner group code field should have value {}")
    public void ownerGroupCodeFieldHasText(String ownerGroupCode) {
        settingsManagementServicesPageObj.ownerGroupCode().shouldHave(text(ownerGroupCode));
    }

    @Step("wsdl address copy button is clicked")
    public void wsdlAddressCopyButtonIsClicked() {
        settingsManagementServicesPageObj.wsdlAddressCopyButton().click();
    }

    @Step("management services address copy button is clicked")
    public void managementServicesAddressCopyButtonIsClicked() {
        settingsManagementServicesPageObj.managementServicesAddressCopyButton().click();
    }

    @Step("{} should be on clipboard")
    public void addressOnClipboard(String address) {
        var clipboardText = Selenide.<String>executeAsyncJavaScript("await navigator.clipboard.readText();");
        Assertions.assertEquals(clipboardText, address);
    }

    @Step("edit management member button is clicked")
    public void editManagementMemberButtonIsClicked() {
        settingsManagementServicesPageObj.editManagementSubsystemButton().click();
    }

    @Step("management service security server Edit button is visible")
    public void editManagementSecurityServerButtonIsVisible() {
        settingsManagementServicesPageObj.editManagementSecurityServerButton().shouldBe(visible);
    }

    @Step("management service security server Edit button is not visible")
    public void editManagementSecurityServerButtonIsNotVisible() {
        settingsManagementServicesPageObj.editManagementSecurityServerButton().shouldNotBe(visible);
    }

    @Step("management service security server Edit button is clicked")
    public void editManagementSecurityServerButtonIsClicked() {
        settingsManagementServicesPageObj.editManagementSecurityServerButton().click();
    }

    @Step("{} is written in search field")
    public void isWrittenInSearchField(String searchTerm) {
        vTextField(settingsManagementServicesPageObj.editManagementSubsystemDialog.search())
                .setValue(searchTerm);
    }

    @Step("{} is written in security server search field")
    public void isWrittenInSecurityServerSearchField(String searchTerm) {
        vTextField(settingsManagementServicesPageObj.editManagementSecurityServerDialog.search())
                .setValue(searchTerm);
    }

    @Step("{} dialog should be open")
    public void selectSecurityServerDialog(String title) {
        settingsManagementServicesPageObj.title(title).shouldBe(visible);
    }

    @Step("checkbox for subsystem {} is selected")
    public void checkboxIsSelected(String subsystem) {
        settingsManagementServicesPageObj.editManagementSubsystemDialog.checkboxOf(subsystem).click(ClickOptions.usingJavaScript());
    }

    @Step("checkbox for security server {} is selected")
    public void checkboxForSecurityServerIsSelected(String code) {
        settingsManagementServicesPageObj.editManagementSecurityServerDialog.checkboxOf(code).click(ClickOptions.usingJavaScript());
    }

    @Step("select button is clicked")
    public void selectButtonIsClicked() {
        settingsManagementServicesPageObj.editManagementSubsystemDialog.selectButton().click();
    }

    @Step("Select button for security server is clicked")
    public void selectButtonForSecurityServerIsClicked() {
        settingsManagementServicesPageObj.editManagementSecurityServerDialog.selectButton().click();
    }

    @Step("success snackbar should be visible")
    public void successSnackbarIsVisible() {
        commonPageObj.snackBar.success().shouldBe(visible);
    }
}
