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

package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.WebElementCondition;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers.SelenideValidation;
import org.niis.xroad.ss.test.ui.page.ApiKeysPageObj;

import java.util.List;
import java.util.Map;

import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vCheckbox;

public class ApiKeysStepDefs extends BaseUiStepDefs {

    private final ApiKeysPageObj apiKeysPage = new ApiKeysPageObj();
    private String createdApiKeyId;

    @Step("Create API key button is clicked")
    public void createAPIKeyButtonIsClicked() {
        apiKeysPage.btnCreateApiKey().click();
        apiKeysPage.wizard.asElement().shouldBe(Condition.visible);
    }

    @Step("Create API key wizard next button status is {}")
    public void btnWizardNextStatus(String status) {
        switch (status) {
            case "enabled" -> apiKeysPage.wizard.btnNext().shouldBe(Condition.enabled);
            case "disabled" -> apiKeysPage.wizard.btnNext().shouldBe(Condition.disabled);
            default -> throw new IllegalArgumentException("Status [" + status + "] is not supported");
        }
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

    @Step("Create API key wizard Previous button is clicked")
    public void clickWizardBack() {
        apiKeysPage.wizard.btnPrevious().click();
    }

    @Step("Create API key wizard Create Key button is clicked")
    public void clickWizardCreateKey() {
        apiKeysPage.wizard.btnCreateKey().click();
    }

    @Step("API key is created and visible")
    public void apiKeyIsPresent() {
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        createdApiKeyId = apiKeysPage.wizard.createdApiKeyId()
                .shouldBe(Condition.visible)
                .text();
    }

    @Step("Create API key wizard Finish button is clicked")
    public void clickWizardFinish() {
        apiKeysPage.wizard.btnFinish().click();
    }

    @Step("Newly created API key is {selenideValidation} in the list")
    public void apiKeyIsPresentInList(SelenideValidation condition) {
        apiKeysPage.apiKeyRow(createdApiKeyId).shouldBe(condition.getSelenideCondition());
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

    @Step("Role {string} is not available")
    public void roleIsNotAvailable(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        apiKeysPage.wizard.checkboxRole(roleEnum).shouldNotBe(Condition.visible);
    }

    private String mapRoleTextToEnum(String roleText) {
        return switch (roleText) {
            case "Security Officer" -> "XROAD_SECURITY_OFFICER";
            case "Registration Officer" -> "XROAD_REGISTRATION_OFFICER";
            case "Service Administrator" -> "XROAD_SERVICE_ADMINISTRATOR";
            case "System Administrator" -> "XROAD_SYSTEM_ADMINISTRATOR";
            case "Server Observer" -> "XROAD_SECURITYSERVER_OBSERVER";
            default -> throw new IllegalArgumentException("Role [" + roleText + "] is not supported");
        };
    }
}
