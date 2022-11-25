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
package org.niis.xroad.test.ui.cs.glue;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.test.ui.cs.glue.ParameterMappers.SelenideValidation;
import org.niis.xroad.test.ui.cs.page.SettingsGlobalResourcesPage;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;

import java.util.List;
import java.util.Map;

public class SettingsGlobalResourcesStepDefs extends BaseUiStepDefs {
    private final SettingsGlobalResourcesPage globalResourcesPage = new SettingsGlobalResourcesPage();

    @Step("Global group list elements are validated")
    public void validateGlobalGroupList(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        for (Map<String, String> columns : rows) {
            var code = columns.get("$code");
            var condition = SelenideValidation.fromString(columns.get("$condition"));

            globalResourcesPage.globalGroupList.globalGroupRow(code).shouldBe(condition.getSelenideCondition());
        }
    }

    @Step("Global group {} is {selenideValidation} in list")
    public void validateGlobalGroup(String code, SelenideValidation selenideValidation) {
        globalResourcesPage.globalGroupList.globalGroupRow(code).shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Add Global Group button is clicked")
    public void clickCreateGlobalGroup() {
        globalResourcesPage.globalGroupList.btnAddGlobalGroup().click();
    }

    @Step("Add Global Group dialog is submitted with code {string} and description {string}")
    public void clickCreateGlobalGroup(String code, String desc) {
        globalResourcesPage.globalGroupForm.inputGroupCode().setValue(code);
        globalResourcesPage.globalGroupForm.inputGroupDescription().setValue(desc);
        globalResourcesPage.globalGroupForm.btnConfirm().click();
    }

}
