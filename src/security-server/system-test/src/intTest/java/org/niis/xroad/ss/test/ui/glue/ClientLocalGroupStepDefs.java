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

import com.codeborne.selenide.CollectionCondition;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.ClientInfoPageObj;

import java.util.List;

import static com.codeborne.selenide.CollectionCondition.empty;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.selectorOptionOf;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vCheckbox;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;
import static org.openqa.selenium.Keys.ENTER;

public class ClientLocalGroupStepDefs extends BaseUiStepDefs {
    private final ClientInfoPageObj clientInfoPageObj = new ClientInfoPageObj();

    @Step("Local group {string} with description {string} is added")
    public void addLocalGroup(String name, String desc) {
        clientInfoPageObj.localGroups.btnAddLocalGroup()
                .shouldBe(visible)
                .click();

        commonPageObj.dialog.btnSave().shouldBe(disabled);
        vTextField(clientInfoPageObj.localGroups.inputLocalGroupCode()).setValue(name);
        commonPageObj.dialog.btnSave().shouldBe(disabled);
        vTextField(clientInfoPageObj.localGroups.inputLocalGroupDescription()).setValue(desc);
        commonPageObj.dialog.btnSave()
                .shouldBe(enabled)
                .click();
    }

    @Step("Local group {string} is {selenideValidation} in the list")
    public void groupExists(String group, ParameterMappers.SelenideValidation selenideValidation) {
        clientInfoPageObj.localGroups.groupByCode(group)
                .shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Local group table sorting change to {string} column")
    public void triggerTableSorting(String column) {
        clientInfoPageObj.localGroups.tableHeader(column).click();
    }

    @Step("Local group filter is set to {string}")
    public void setFilter(String query) {
        vTextField(clientInfoPageObj.localGroups.inputFilter()).setValue(query);
    }

    @Step("Local group table is ordered as follows:")
    public void validateTable(DataTable dataTable) {
        final List<String> values = dataTable.asList();
        for (int i = 0; i < values.size(); i++) {
            clientInfoPageObj.localGroups.groupByPos(i + 1).shouldBe(text(values.get(i)));
        }

        clientInfoPageObj.localGroups.groups().shouldBe(CollectionCondition.size(values.size()));
    }

    @Step("Local group {string} is selected")
    public void selectLocalGroup(String code) {
        clientInfoPageObj.localGroups.groupByCode(code).click();
    }

    @Step("Add Local group button is {selenideValidation}")
    public void validateBtnAddLocalGrup(ParameterMappers.SelenideValidation selenideValidation) {
        clientInfoPageObj.localGroups.btnAddLocalGroup().shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Local group details are read-only")
    public void validateLocalGroupView() {
        clientInfoPageObj.localGroups.details.btnAddMembers().shouldNotBe(visible);
        clientInfoPageObj.localGroups.details.btnRemoveAll().shouldNotBe(visible);
        clientInfoPageObj.localGroups.details.btnRemove().shouldBe(empty);
        clientInfoPageObj.localGroups.details.btnDelete().shouldNotBe(visible);

        clientInfoPageObj.localGroups.details.btnClose().click();
    }

    @Step("Local group details are editable")
    public void validateLocalGroupViewEditable() {
        clientInfoPageObj.localGroups.details.btnAddMembers().shouldBe(visible);
        clientInfoPageObj.localGroups.details.btnRemoveAll().shouldBe(visible);
        clientInfoPageObj.localGroups.details.btnDelete().shouldBe(visible);

        clientInfoPageObj.localGroups.details.btnClose().click();
    }

    @Step("Local group is deleted")
    public void delete() {
        clientInfoPageObj.localGroups.details.btnDelete().click();
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Local group description is set to {string}")
    public void setDescription(String desc) {
        vTextField(clientInfoPageObj.localGroups.details.inputLocalGroupDescription())
                .clear()
                .setValue(desc)
                .sendKeys(ENTER);

        if (isNotBlank(desc)) {
            commonPageObj.snackBar.success().shouldHave(text("Description saved"));
            commonPageObj.snackBar.btnClose().click();
        }
    }

    @Step("Local group search dialog is opened and members for instance {string} and member class {string} are filtered")
    public void lookupAddMembers(String instance, String memberClass) {

        clientInfoPageObj.localGroups.details.btnAddMembers().click();

        clientInfoPageObj.localGroups.details.addMember.inputInstance().click();
        selectorOptionOf(instance).click();

        clientInfoPageObj.localGroups.details.addMember.inputMemberCode().click();
        selectorOptionOf(memberClass).click();

        clientInfoPageObj.localGroups.details.addMember.btnSearch().click();
    }

    @Step("Following members are added to local group:")
    public void lookupAddMembers(DataTable dataTable) {
        clientInfoPageObj.localGroups.details.addMember.btnAddSelected().shouldBe(disabled);
        dataTable.asList().forEach(member -> vCheckbox(clientInfoPageObj.localGroups.details.addMember.checkboxSelectMember(member))
                .scrollIntoView(false)
                .click());

        clientInfoPageObj.localGroups.details.addMember.btnAddSelected()
                .shouldBe(enabled)
                .click();

    }

    @Step("Following members are {selenideValidation} in local group:")
    public void validateMembers(ParameterMappers.SelenideValidation selenideValidation, DataTable dataTable) {
        dataTable.asList().forEach(member -> clientInfoPageObj.localGroups.details.memberByCode(member)
                .shouldBe(selenideValidation.getSelenideCondition()));
    }


    @Step("Local group member {string} is removed")
    public void lookupAddMembers(String member) {
        clientInfoPageObj.localGroups.details.btnRemoveMemberByCode(member).click();
        commonPageObj.dialog.btnSave().click();
    }
}
