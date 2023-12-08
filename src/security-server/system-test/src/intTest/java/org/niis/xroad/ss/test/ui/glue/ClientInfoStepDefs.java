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

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;

public class ClientInfoStepDefs extends BaseUiStepDefs {
    private final ClientInfoPageObj clientInfoPageObj = new ClientInfoPageObj();

    @Step("Client Details is as follows: Member Name {string}, Member Class {string}, Member Code {string}, Sign Cert: {string}")
    public void validateClientDetails(String name, String clazz, String code, String cert) {
        clientInfoPageObj.details.rowMemberName().should(text(name));
        clientInfoPageObj.details.rowMemberClass().should(text(clazz));
        clientInfoPageObj.details.rowMemberCode().should(text(code));
        clientInfoPageObj.details.rowCertName().should(text(cert));
    }

    @Step("Client sign certificate {string} is selected")
    public void selectClientDetailsCert(String cert) {
        clientInfoPageObj.details.certificateByName(cert)
                .scrollIntoView(false)
                .shouldBe(visible)
                .click();
    }

    @Step("Local groups sub-tab is selected")
    public void navigateLocalGroups() {
        clientInfoPageObj.navigation.localGroupsTab().click();
    }

    @Step("Internal servers sub-tab is selected")
    public void navigateInternalServers() {
        clientInfoPageObj.navigation.internalServersTab().click();
    }

    @Step("Services sub-tab is selected")
    public void navigateServices() {
        clientInfoPageObj.navigation.servicesTab().click();
    }

    @Step("Service clients sub-tab is selected")
    public void navigateServiceClients() {
        clientInfoPageObj.navigation.serviceClientsTab().click();
    }

    @Step("Client Disable button is clicked")
    public void clickDisableClientButton() {
        clientInfoPageObj.details.btnDisable()
                .should(visible)
                .click();
        commonPageObj.dialog.btnSave()
                .shouldBe(enabled)
                .click();
    }
}
