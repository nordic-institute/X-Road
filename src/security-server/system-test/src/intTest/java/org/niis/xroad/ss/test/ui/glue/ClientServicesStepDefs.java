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
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.ClientInfoPageObj;

import static com.codeborne.selenide.ClickOptions.usingJavaScript;
import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.checked;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.selected;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static java.time.Duration.ofSeconds;

public class ClientServicesStepDefs extends BaseUiStepDefs {
    private final ClientInfoPageObj clientInfoPageObj = new ClientInfoPageObj();


    @Step("Rest service dialog is opened and base path is set to {string} and service code {string}")
    public void addRestService(String url, String serviceCode) {
        addRestService(true, url, serviceCode);
    }

    @Step("Rest service dialog is opened and OpenApi spec is set to {string} and service code {string}")
    public void addRestServiceOpenApi(String url, String serviceCode) {
        addRestService(false, url, serviceCode);
    }

    private void addRestService(boolean isBasePath, String url, String serviceCode) {
        clientInfoPageObj.services.btnAddREST()
                .shouldBe(visible)
                .click();

        commonPageObj.dialog.btnSave().shouldBe(disabled);

        clientInfoPageObj.services.inputRadioRESTPath().shouldNotBe(selected);
        clientInfoPageObj.services.inputRadioOpenAPI().shouldNotBe(selected);

        if (isBasePath) {
            clientInfoPageObj.services.radioRESTPath().click();
        } else {
            clientInfoPageObj.services.radioOpenAPI().click();
        }
        clientInfoPageObj.services.servicesParameters.inputServiceUrl()
                .shouldBe(empty)
                .setValue(url);

        clientInfoPageObj.services.servicesParameters.inputServiceCode()
                .shouldBe(empty)
                .setValue(serviceCode);
    }

    @Step("Service {string} is {selenideValidation} in the list")
    public void addRestService(String desc, ParameterMappers.SelenideValidation selenideValidation) {
        clientInfoPageObj.services.headerServiceDescription(desc).should(selenideValidation.getSelenideCondition());
    }

    @Step("Service {string} is expanded")
    public void openService(String desc) {
        clientInfoPageObj.services.headerServiceDescriptionExpand(desc).click();
    }

    @Step("Service with code {string} is opened")
    public void openServiceCode(String serviceCode) {
        clientInfoPageObj.services.linkServiceCode(serviceCode).click();
    }

    @Step("Service URL is set to {string}, timeout to {} and tls certificate verification is {checkbox}")
    public void editService(String url, String timeout, boolean verifyTlsCert) {
        editService(url, timeout, verifyTlsCert, false, false, false);
    }

    @Step("Service URL is set to {string}, timeout to {} and tls certificate verification is {checkbox}. "
            + "Apply All? Url: {checkbox}, timeout: {checkbox}, verify Tls: {checkbox}")
    public void editWsdlService(String url, String timeout, boolean verifyTlsCert, boolean urlApplyAll, boolean timeoutApplyAll,
                                boolean verifyTlsApplyAll) {
        editService(url, timeout, verifyTlsCert, urlApplyAll, timeoutApplyAll, verifyTlsApplyAll);
    }

    private void editService(String url, String timeout, boolean verifyTlsCert, boolean urlApplyAll, boolean timeoutApplyAll,
                             boolean verifyTlsApplyAll) {
        clearInput(clientInfoPageObj.services.servicesParameters.inputServiceUrl());
        clearInput(clientInfoPageObj.services.servicesParameters.inputServiceTimeout());

        commonPageObj.form.inputErrorMessage("The URL field is required").shouldBe(visible);
        commonPageObj.form.inputErrorMessage("The Timeout field is required").shouldBe(visible);

        clientInfoPageObj.services.servicesParameters.inputServiceUrl().setValue(url);
        clientInfoPageObj.services.servicesParameters.inputServiceTimeout().setValue(timeout);

        var isChecked = clientInfoPageObj.services.servicesParameters.inputVerifyTlsCert().is(checked);
        if (isChecked != verifyTlsCert) {
            clientInfoPageObj.services.servicesParameters.checkboxVerifyTlsCert().click();
        }

        if (urlApplyAll) {
            clientInfoPageObj.services.servicesEdit.checkboxUrlApplyAll().click();
        }
        if (timeoutApplyAll) {
            clientInfoPageObj.services.servicesEdit.checkboxTimeoutApplyAll().click();
        }
        if (verifyTlsApplyAll) {
            clientInfoPageObj.services.servicesEdit.checkboxVerifySslApplyAll().click();
        }
    }

    @Step("Service URL is {string}, timeout is {} and tls certificate verification is {checkbox}")
    public void editServiceVerify(String url, String timeout, boolean verifyTlsCert) {
        clientInfoPageObj.services.servicesParameters.inputServiceUrl().shouldBe(value(url));
        clientInfoPageObj.services.servicesParameters.inputServiceTimeout().shouldBe(value(timeout));

        clientInfoPageObj.services.servicesParameters.inputVerifyTlsCert().shouldBe(verifyTlsCert ? checked : Condition.not(checked));
    }

    @Step("Service is saved and success message {string} is shown")
    public void dialogSave(String message) {
        clientInfoPageObj.services.servicesParameters.btnSaveEdit().click();
        commonPageObj.snackBar.success().should(text(message));
        commonPageObj.snackBar.btnClose().click();
        commonPageObj.dialog.btnClose().click();
    }

    @Step("Service with a warning is saved and success message {string} is shown")
    public void dialogSaveWithWarning(String message) {
        clientInfoPageObj.services.servicesParameters.btnSaveEdit().click();

        commonPageObj.dialog.title().shouldHave(text("warning"));
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().should(text(message));
        commonPageObj.snackBar.btnClose().click();
        commonPageObj.dialog.btnClose().click();
    }

    @Step("Service add subjects dialog is opened")
    public void openAddSubjects() {
        clientInfoPageObj.services.servicesParameters.btnAddSubjects()
                .scrollIntoView(false)
                .click();
    }

    @Step("Service subject lookup is executed with member name {string} and subsystem code {string}")
    public void searchAccessRights(String name, String subsystem) {
        clientInfoPageObj.services.addSubject.inputName().shouldBe(empty).setValue(name);
        clientInfoPageObj.services.addSubject.inputSubsystemCode().shouldBe(empty).setValue(subsystem);
        clientInfoPageObj.services.addSubject.btnSearch().click();
    }

    @Step("Adding value for member name, member code, subsystem and then click the remove value button on the input field")
    public void clearSubjectsFilter() {
        clientInfoPageObj.services.addSubject.inputName().shouldBe(empty).setValue("name");
        clientInfoPageObj.services.addSubject.buttonClearInputName().click();

        clientInfoPageObj.services.addSubject.inputMemberCode().shouldBe(empty).setValue("memberCode");
        clientInfoPageObj.services.addSubject.buttonClearInputMemberCode().click();

        clientInfoPageObj.services.addSubject.inputSubsystemCode().shouldBe(empty).setValue("subsystemCode");
        clientInfoPageObj.services.addSubject.buttonClearInputSubsystemCode().click();
    }

    @Step("Click Search button on subject dialog")
    public void clickSearch() {
        clientInfoPageObj.services.addSubject.btnSearch().click();
    }

    @Step("Subject with id {string} and {string} is selected from the table. There are total {} entries")
    public void addAccessRights(String id, String id2, int size) {
        clientInfoPageObj.services.addSubject.memberTableRows().shouldHave(size(size));

        clientInfoPageObj.services.addSubject.memberTableRowCheckboxOfId(id).click();
        clientInfoPageObj.services.addSubject.memberTableRowCheckboxOfId(id2).click();

        clientInfoPageObj.services.addSubject.btnSave().click();
        commonPageObj.snackBar.success().shouldHave(text("Access rights added successfully"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("The query return {} entries in the subjects table")
    public void validateSubjectsTableSize(int size) {
        clientInfoPageObj.services.addSubject.memberTableRows().shouldHave(size(size));
    }

    @Step("Service Access Rights table member with id {string} is {selenideValidation}")
    public void validateAccessRights(String id, ParameterMappers.SelenideValidation validation) {
        clientInfoPageObj.services.accessRightsTableRowOfId(id).shouldBe(validation.getSelenideCondition());
    }

    @Step("Service Access Rights subject with id {string} is removed")
    public void removeAccessRights(String id) {
        clientInfoPageObj.services.accessRightsTableRowRemoveOfId(id)
                .shouldBe(visible)
                .scrollIntoView(false)
                .click();

        commonPageObj.dialog.btnSave().click();
        commonPageObj.snackBar.success().shouldHave(text("Access rights removed successfully"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service has all Access Rights removed")
    public void removeAllAccessRights() {
        clientInfoPageObj.services.servicesParameters.btnRemoveAllSubjects().click();
        commonPageObj.dialog.btnSave().click();
        commonPageObj.snackBar.success().shouldHave(text("Access rights removed successfully"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service endpoints view is opened")
    public void selectEndpoints() {
        clientInfoPageObj.services.btnEndpoints().shouldBe(visible).click();
    }

    @Step("Service endpoint with HTTP request method {string} and path {string} is added")
    public void addEndpoint(String httpMethod, String path) {
        addEndpoint(httpMethod, path, false);
    }

    @Step("Service endpoint with duplicated HTTP request method {string} and path {string} is not added")
    public void addEndpointDuplicate(String httpMethod, String path) {
        addEndpoint(httpMethod, path, true);
    }

    private void addEndpoint(String httpMethod, String path, boolean duplicate) {
        clientInfoPageObj.services.endpoints.btnAddEndpoint().click();
        commonPageObj.dialog.btnCancel().click();
        clientInfoPageObj.services.endpoints.btnAddEndpoint().click();

        clientInfoPageObj.services.endpoints.dropdownHttpMethod().click();
        clientInfoPageObj.services.endpoints.selectDropdownOption(httpMethod).click();

        clearInput(clientInfoPageObj.services.endpoints.inputPath());
        commonPageObj.dialog.btnSave().shouldBe(disabled);

        clientInfoPageObj.services.endpoints.inputPath().setValue(path);

        commonPageObj.dialog.btnSave().click();
        if (duplicate) {
            commonPageObj.alerts.alert("Endpoint already exists").shouldBe(visible);
        } else {
            commonPageObj.snackBar.success().shouldHave(text("New endpoint created successfully"));
        }
    }

    @Step("Service endpoint with HTTP request method {string} and path {string} is {selenideValidation} in the list")
    public void validateEndpoint(String httpMethod, String path, ParameterMappers.SelenideValidation selenideValidation) {
        clientInfoPageObj.services.endpoints.endpointRow(httpMethod, path)
                .shouldBe(selenideValidation.getSelenideCondition());
    }

    @Step("Service endpoint with HTTP request method {string} and path {string} is not editable")
    public void validateEndpointEdit(String httpMethod, String path) {
        clientInfoPageObj.services.endpoints.endpointRow(httpMethod, path)
                .shouldBe(visible);

        clientInfoPageObj.services.endpoints.buttonEndpointRowEdit(httpMethod, path).shouldNotBe(visible);
    }

    @Step("Service endpoint with HTTP request method {string} and path {string} has its path changed to {string}")
    public void validateEndpointEdit(String httpMethod, String path, String newPath) {
        clientInfoPageObj.services.endpoints.buttonEndpointRowEdit(httpMethod, path)
                .scrollIntoView(false)
                .click();

        clearInput(clientInfoPageObj.services.endpoints.inputPath());
        clientInfoPageObj.services.endpoints.inputPath().shouldBe(empty);
        clientInfoPageObj.services.endpoints.btnSave().shouldBe(disabled);

        clientInfoPageObj.services.endpoints.inputPath().setValue(newPath);
        clientInfoPageObj.services.endpoints.btnSave().click();
        commonPageObj.snackBar.success().shouldHave(text("Changes to endpoint saved successfully"));
    }

    @Step("Service endpoint with HTTP request method {string} and path {string} is deleted")
    public void deleteEndpoint(String httpMethod, String path) {
        clientInfoPageObj.services.endpoints.buttonEndpointRowEdit(httpMethod, path)
                .scrollIntoView(false)
                .click();

        clientInfoPageObj.services.endpoints.btnDeleteEndpoint().click();
        commonPageObj.dialog.btnCancel().click();
        clientInfoPageObj.services.endpoints.btnDeleteEndpoint().click();
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldHave(text("Endpoint removed successfully"));
    }

    @Step("Service {string} is enabled")
    public void enableService(String desc) {
        clientInfoPageObj.services.headerServiceToggle(desc).click();
        commonPageObj.snackBar.success().shouldHave(text("Service description enabled"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service {string} is disabled with notice {string}")
    public void disableService(String desc, String notice) {
        clientInfoPageObj.services.headerServiceToggle(desc).click(usingJavaScript());
        clientInfoPageObj.services.inputDisableNotice().setValue(notice);

        commonPageObj.dialog.btnSave().click();
        commonPageObj.snackBar.success().shouldHave(text("Service description disabled"));
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Service {string} is updated with url {string} and service code {string}")
    public void editRestService(String name, String url, String serviceCode) {
        clientInfoPageObj.services.headerServiceDescription(name).click();

        clearInput(clientInfoPageObj.services.servicesEdit.inputEditUrl());
        clearInput(clientInfoPageObj.services.servicesEdit.inputEditServiceCode());

        commonPageObj.form.inputErrorMessage("The URL field is required").shouldBe(visible);
        commonPageObj.form.inputErrorMessage("The fields.code_field field is required").shouldBe(visible);

        clientInfoPageObj.services.servicesEdit.inputEditUrl().setValue(url);
        clientInfoPageObj.services.servicesEdit.inputEditServiceCode().setValue(serviceCode);
    }

    @Step("Rest service details are saved and error message {string} is shown")
    @SuppressWarnings("checkstyle:MagicNumber")
    public void saveRestServiceError(String message) {
        clientInfoPageObj.services.servicesEdit.btnSaveEdit().click();
        commonPageObj.alerts.alert(message).shouldBe(Condition.visible, ofSeconds(15));
    }

    @Step("Rest service parameters are saved and error message {string} is shown")
    @SuppressWarnings("checkstyle:MagicNumber")
    public void saveRestServiceParamsSaveError(String message) {
        clientInfoPageObj.services.servicesParameters.btnSaveEdit().click();
        commonPageObj.alerts.alert(message).shouldBe(Condition.visible, ofSeconds(15));
    }

    @Step("Rest service details are saved and success message {string} is shown")
    public void saveRestServiceSuccess(String message) {
        clientInfoPageObj.services.servicesEdit.btnSaveEdit().click();
        commonPageObj.snackBar.success().shouldHave(text(message));
    }

    @Step("Service {string} is deleted")
    public void editRestService(String name) {
        clientInfoPageObj.services.headerServiceDescription(name).click();

        clientInfoPageObj.services.servicesEdit.btnServiceDelete().click();
        commonPageObj.dialog.title().shouldBe(visible);
        commonPageObj.dialog.btnSave().click();
        commonPageObj.snackBar.success().shouldHave(text("Service description deleted"));
    }

    @Step("Services under {string} are as follows:")
    public void validateSubServiceTable(String service, DataTable dataTable) {
        var rows = dataTable.asMaps();

        rows.forEach(row -> {
            var serviceCode = row.get("$serviceCode");
            clientInfoPageObj.services.tableServiceUrlOfServiceCode(serviceCode).shouldHave(text(row.get("$url")));
            clientInfoPageObj.services.tableServiceTimeoutOfServiceCode(serviceCode).shouldHave(text(row.get("$timeout")));
        });
    }

    @Step("WSDL service dialog is opened and url is set to {string}")
    public void addWsdl(String url) {
        clientInfoPageObj.services.btnAddWSDL()
                .shouldBe(visible)
                .click();

        commonPageObj.dialog.btnSave().shouldBe(disabled);

        clientInfoPageObj.services.servicesParameters.inputServiceUrl().shouldBe(empty).setValue(" ");
        commonPageObj.form.inputErrorMessage("The URL field is required").shouldBe(visible);
        clearInput(clientInfoPageObj.services.servicesParameters.inputServiceUrl());

        clientInfoPageObj.services.servicesParameters.inputServiceUrl().shouldBe(empty).setValue("invalid");
        commonPageObj.form.inputErrorMessage("URL is not valid").shouldBe(visible);
        clearInput(clientInfoPageObj.services.servicesParameters.inputServiceUrl());

        clientInfoPageObj.services.servicesParameters.inputServiceUrl().setValue(url);

        commonPageObj.dialog.btnSave().click();
    }

    @Step("Service {string} is updated with url {string}")
    public void editWsdlService(String name, String url) {
        clientInfoPageObj.services.headerServiceDescription(name).click();

        clearInput(clientInfoPageObj.services.servicesEdit.inputEditUrl());

        commonPageObj.form.inputErrorMessage("The URL field is required").shouldBe(visible);

        clientInfoPageObj.services.servicesEdit.inputEditUrl().setValue(url);

        clientInfoPageObj.services.servicesEdit.btnSaveEdit().click();
        clientInfoPageObj.services.servicesEdit.btnContinueWarn().click();
        commonPageObj.snackBar.success().shouldHave(text("Description saved"));
    }

    @Step("WSDL Service is refreshed")
    public void refreshWsdl() {
        clientInfoPageObj.services.btnRefresh().shouldBe(visible).click();

        commonPageObj.snackBar.success().shouldHave(text("Refreshed"));
    }
}
