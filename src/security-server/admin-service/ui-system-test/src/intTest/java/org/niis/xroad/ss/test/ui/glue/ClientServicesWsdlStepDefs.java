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

import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.ClientInfoPageObj;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;

public class ClientServicesWsdlStepDefs extends BaseUiStepDefs {
    private final ClientInfoPageObj clientInfoPageObj = new ClientInfoPageObj();


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
