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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.WebElementCondition;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.glue.mappers.ParameterMappers.SelenideValidation;
import org.niis.xroad.cs.test.ui.page.SettingsApiKeysPageObj;

import java.util.List;
import java.util.Map;

import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vCheckbox;

public class SettingsApiKeysStepDefs extends BaseUiStepDefs {
    private final SettingsApiKeysPageObj apiKeysPage = new SettingsApiKeysPageObj();

    private String createdApiKeyId;

    @Step("Create API key button is clicked")
    public void clickCreateApiKey() {
        apiKeysPage.btnCreateApiKey().click();
        apiKeysPage.wizard.asElement().shouldBe(Condition.visible);
    }

    @Step("Role {string} is being clicked")
    public void selectRole(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        vCheckbox(apiKeysPage.wizard.checkboxRole(roleEnum)).click();
    }

    @Step("Create API key wizard next button is clicked")
    public void clickWizardNext() {
        apiKeysPage.wizard.btnNext().click();
    }

    @Step("Create API key wizard next button status is {}")
    public void btnWizardNextStatus(String status) {
        switch (status) {
            case "enabled":
                apiKeysPage.wizard.btnNext().shouldBe(Condition.enabled);
                break;
            case "disabled":
                apiKeysPage.wizard.btnNext().shouldBe(Condition.disabled);
                break;
            default:
                throw new IllegalArgumentException("Status [" + status + "] is not supported");
        }

    }

    @Step("Create API key wizard Previous button is clicked")
    public void clickWizardBack() {
        apiKeysPage.wizard.btnPrevious().click();
    }

    @Step("Create API key wizard Cancel button is clicked")
    public void clickWizardCancel() {
        commonPageObj.dialog.btnCancel().click();
    }

    @Step("Create API key wizard Create Key button is clicked")
    public void clickWizardCreateKey() {
        apiKeysPage.wizard.btnCreateKey().click();
    }

    @Step("Create API key wizard Finish button is clicked")
    public void clickWizardFinish() {
        apiKeysPage.wizard.btnFinish().click();
    }

    @Step("API key is created and visible")
    public void apiKeyIsPresent() {
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        createdApiKeyId = apiKeysPage.wizard.createdApiKeyId()
                .shouldBe(Condition.visible)
                .text();
    }

    @Step("API key is set to token {} and in Authentication header")
    public void getApiKeyAndSetAuthenticationHeaderIsSet(CommonStepDefs.TokenType type) {
        var createdApiKey = apiKeysPage.wizard.createdApiKey().text();
        type.setToken(createdApiKey);
        putStepData(StepDataKey.TOKEN_TYPE, type);
    }

    @Step("Newly created API key is {selenideValidation} in the list")
    public void apiKeyIsPresentInList(SelenideValidation condition) {
        apiKeysPage.apiKeyRow(createdApiKeyId).shouldBe(condition.getSelenideCondition());
    }

    @Step("Newly created API key is present in the list and has roles")
    public void apiKeyIsPresentInListWithRoles(DataTable table) {
        apiKeysPage.apiKeyRow(createdApiKeyId).shouldBe(Condition.visible);

        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        for (Map<String, String> columns : rows) {
            var role = columns.get("$role");

            WebElementCondition condition;
            if (SelenideValidation.PRESENT.name().equalsIgnoreCase(columns.get("$condition"))) {
                condition = Condition.partialText(role);
            } else {
                condition = Condition.not(Condition.partialText(role));
            }
            apiKeysPage.apiKeyRoles(createdApiKeyId).shouldHave(condition);
        }
    }

    @Step("Newly created API key is revoked")
    public void apiKeyRevoked() {
        apiKeysPage.btnRevokeApiKey(createdApiKeyId).shouldBe(Condition.visible).click();
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Newly created API key is edit dialog is opened")
    public void apiKeyEdited() {
        apiKeysPage.btnEditApiKey(createdApiKeyId).shouldBe(Condition.visible).click();
    }

    @Step("Role {string} is not available")
    public void roleIsNotAvailable(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        apiKeysPage.wizard.checkboxRole(roleEnum).shouldNotBe(Condition.visible);
    }

    @Step("Role {string} is available")
    public void roleIsAvailable(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        apiKeysPage.wizard.checkboxRole(roleEnum).shouldBe(Condition.visible);
    }

    private String mapRoleTextToEnum(String roleText) {
        return switch (roleText) {
            case "Registration Officer" -> "XROAD_REGISTRATION_OFFICER";
            case "Security Officer" -> "XROAD_SECURITY_OFFICER";
            case "System Administrator" -> "XROAD_SYSTEM_ADMINISTRATOR";
            case "Management Services" -> "XROAD_MANAGEMENT_SERVICE";
            default -> throw new IllegalArgumentException("Role [" + roleText + "] is not supported");
        };
    }
}
