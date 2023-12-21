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
import org.niis.xroad.ss.test.ui.page.ClientPageObj;

import java.util.List;

import static com.codeborne.selenide.Condition.focused;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class ClientListStepDefs extends BaseUiStepDefs {
    private final ClientPageObj clientPageObj = new ClientPageObj();

    @Step("Client {string} is opened")
    public void openClient(String client) {
        clientPageObj.linkClientDetailsOfName(client)
                .shouldBe(visible)
                .click();
    }

    @Step("Client {string} is {selenideValidation} in the list")
    public void validateClient(String client, ParameterMappers.SelenideValidation selenideValidation) {
        clientPageObj.linkClientDetailsOfName(client)
                .shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Client {string} with status {string} is {selenideValidation} in the list")
    public void validateClient(String client, String status, ParameterMappers.SelenideValidation selenideValidation) {
        clientPageObj.tableRowWithNameAndStatus(client, status)
                .shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Client {string} details are not available")
    public void validateOpenClient(String client) {
        clientPageObj.linkClientDetailsOfName(client)
                .shouldBe(visible)
                .click();

        clientPageObj.linkClientDetailsOfName(client)
                .shouldBe(visible);
    }

    @Step("Subsystem add page is opened for Client {string}")
    public void addSubsystem(String client) {
        clientPageObj.btnAddSubsystem(client).click();
    }

    @Step("Add client wizard is opened")
    public void addClient() {
        clientPageObj.btnAddClient()
                .shouldBe(visible)
                .click();
    }

    @Step("Client filter is set to {string}")
    public void setFilter(String query) {
        clientPageObj.btnSearch().shouldBe(visible).click();
        vTextField(clientPageObj.inputSearch()).shouldBe(focused).setValue(query);
    }

    @Step("Client table is ordered as follows:")
    public void validateTable(DataTable dataTable) {
        final List<String> values = dataTable.asList();
        for (int i = 0; i < values.size(); i++) {
            clientPageObj.groupByPos(i + 1).shouldBe(text(values.get(i)));
        }

        clientPageObj.groups().shouldBe(CollectionCondition.size(values.size()));
    }

    @Step("Client table sorting change to {string} column")
    public void triggerTableSorting(String column) {
        clientPageObj.tableHeader(column).click();
    }
    @Step("Client table sorting change to {string} column desc")
    public void triggerTableSortingDesc(String column) {
        clientPageObj.tableHeader(column).click();
        clientPageObj.tableHeader(column).click();
    }
}
