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

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.ClientInfoPageObj;

import java.util.concurrent.atomic.AtomicInteger;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vCheckbox;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class ClientServiceClientsStepDefs extends BaseUiStepDefs {
    private final ClientInfoPageObj.ServiceClients serviceClients = new ClientInfoPageObj.ServiceClients();

    @Step("Service clients add subject wizard is opened")
    public void openSubjectWizard() {
        serviceClients.btnAddSubject().shouldBe(visible).click();
        serviceClients.addSubject.btnCancelWizardMemberPage().shouldBe(visible).click();
        serviceClients.btnAddSubject().shouldBe(visible).click();
    }

    @Step("Service clients wizard is filtered to {string} with {} results and subject {string} is selected")
    public void selectMemberSubjectWizard(String search, int resultCount, String id) {
        serviceClients.addSubject.btnNext().shouldBe(disabled);

        vTextField(serviceClients.addSubject.inputMemberSearch()).shouldBe(empty).setValue(search);

        serviceClients.addSubject.tableMemberRows().shouldHave(size(resultCount));

        serviceClients.addSubject.tableMemberRowRadioById(id).shouldBe(visible).click();
    }

    @Step("Service clients subject {string} is not selectable")
    public void validateDisabledMembers(String id) {
        serviceClients.addSubject.tableMemberRowRadioInputById(id).shouldBe(disabled);
    }

    @Step("Service clients wizard services step is filtered to {string} with {} results and service {string} is selected")
    public void selectServiceSubjectWizard(String search, int resultCount, String serviceCode) {
        serviceClients.addSubject.btnNext().shouldBe(enabled).click();
        serviceClients.addSubject.btnPrevious().click();
        serviceClients.addSubject.btnNext().click();
        serviceClients.addSubject.btnFinish().shouldBe(disabled);

        vTextField(serviceClients.addSubject.inputServiceSearch()).shouldBe(empty).setValue(search);

        serviceClients.addSubject.tableServiceRows().shouldHave(size(resultCount));

        vCheckbox(serviceClients.addSubject.tableServiceRowRadioById(serviceCode)).click();
        serviceClients.addSubject.btnFinish().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldHave(text("Access rights successfully added"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service clients list is as follows")
    public void validateTable(DataTable dataTable) {
        var rows = dataTable.asMaps();

        AtomicInteger rowNo = new AtomicInteger();
        rows.forEach(row -> serviceClients.tableMemberNameOfId(
                        rowNo.incrementAndGet(),
                        row.get("$id"))
                .shouldHave(text(row.get("$memberName"))));
    }

    @Step("Service clients list is filtered with {string}")
    public void searchTable(String query) {
        vTextField(serviceClients.inputMemberSearch()).clear().setValue(query);
    }

    @Step("Service clients list sorted by col no {} {sortDir}")
    public void changeTableSort(int colNo, ParameterMappers.SortDir sortDir) {

        var sortCol = serviceClients.tableHeaderOfCol(colNo);
        var currentSort = sortCol.getAttribute("aria-sort");

        if ("ascending".equalsIgnoreCase(currentSort) && sortDir != ParameterMappers.SortDir.ASC) {
            sortCol.click();
        } else if ("descending".equalsIgnoreCase(currentSort) && sortDir != ParameterMappers.SortDir.DESC) {
            sortCol.click();
        } else {
            if (sortDir == ParameterMappers.SortDir.ASC) {
                sortCol.click();
            } else {
                sortCol.click();
                sortCol.click();
            }
        }
    }

    @Step("Service clients list entry with id {string} is {selenideValidation}")
    public void validateTableRow(String id, ParameterMappers.SelenideValidation selenideValidation) {
        serviceClients.tableMemberNameOfId(id).shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Service client {string} is opened")
    public void editServiceClient(String id) {
        serviceClients.tableMemberNameOfId(id).click();
    }

    @Step("Service client view shows id {string} and member name {string}")
    public void validateEditClientDesc(String id, String memberName) {
        serviceClients.edit.cellMemberName().shouldBe(text(memberName));
        serviceClients.edit.cellId().shouldBe(text(id));
    }

    @Step("Service client view access right list is as follows")
    public void validateEditClientAccessRights(DataTable dataTable) {
        var rows = dataTable.asMaps();
        rows.forEach(row -> serviceClients.edit.tableAccessRightsOfServiceCode(row.get("$serviceCode")).shouldBe(visible));
    }

    @Step("Service client view access right for service code {string} is removed")
    public void removeAccessRight(String serviceCode) {
        serviceClients.edit.btnRemoveByServiceCode(serviceCode).click();
        commonPageObj.dialog.btnCancel().click();
        serviceClients.edit.btnRemoveByServiceCode(serviceCode).click();
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldHave(text("Access rights removed successfully"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service client view remove all button is missing")
    public void validateRemoveAllMissing() {
        serviceClients.edit.btnRemoveAll().shouldNotBe(visible);
    }

    @Step("Service client view Access rights table is empty")
    public void validateAccessRightsMissing() {
        serviceClients.edit.tableAccessRightsEmptyMsg().shouldBe(visible);
    }

    @Step("Service clients access rights are removed in full")
    public void removeAllAccessRights() {
        serviceClients.edit.btnRemoveAll().click();
        commonPageObj.dialog.title().shouldBe(visible);
        commonPageObj.dialog.btnCancel().click();
        serviceClients.edit.btnRemoveAll().click();
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldHave(text("Access rights removed successfully"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service client view has following services added")
    public void addEditViewServices(DataTable dataTable) {
        serviceClients.edit.btnAddService().click();
        commonPageObj.dialog.btnCancel().click();
        serviceClients.edit.btnAddService().click();

        var rows = dataTable.asMaps();
        rows.forEach(row -> vCheckbox(serviceClients.addSubject.tableServiceRowRadioById(row.get("$serviceCode"))).click());

        commonPageObj.dialog.btnSave().click();
        commonPageObj.snackBar.success().shouldHave(text("Access rights successfully added"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service client view is closed")
    public void addEditViewServices() {
        commonPageObj.dialog.btnClose().click();
    }
}
