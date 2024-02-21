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
import lombok.SneakyThrows;
import org.niis.xroad.ss.test.ui.page.ClientPageObj;

import java.io.File;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.selectorOptionOf;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vRadio;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

@SuppressWarnings("checkstyle:MagicNumber")
public class ClientAddStepDefs extends BaseUiStepDefs {
    private final ClientPageObj clientPageObj = new ClientPageObj();

    @Step("Add Client details is filled with preselected client {string} is opened")
    public void addClientDetails(String client) {
        clientPageObj.addClientDetails.btnNext().shouldBe(disabled);

        clientPageObj.addClientDetails.btnSelectClient().click();
        clientPageObj.addClientDetails.btnAddSelected().shouldBe(disabled);

        clientPageObj.addClientDetails.radioClientById(client).click();
        clientPageObj.addClientDetails.btnAddSelected().click();

        String[] idParts = client.split(":");

        clientPageObj.addClientDetails.selectMemberClass().shouldBe(text(idParts[1]));
        vTextField(clientPageObj.addClientDetails.inputMemberCode()).shouldHaveText(idParts[2]);
        vTextField(clientPageObj.addClientDetails.inputSubsystemCode()).shouldHaveText(idParts[3]);

        clientPageObj.addClientDetails.btnNext().click();
    }

    @Step("Add Client Token wizard page is closed")
    public void wizardClientTokenClose() {
        clientPageObj.addClientToken.cancelButton().click();
    }

    @Step("Add Client Token is set as {string}")
    public void wizardClientToken(String token) {
        clientPageObj.addClientToken.radioByTokenName(token).shouldBe(visible);
        vRadio(clientPageObj.addClientToken.radioByTokenName(token)).shouldBeChecked();
        clientPageObj.addClientToken.btnNext().click();
    }

    @Step("Add Client Sign key label set to {string}")
    public void wizardSignKey(String label) {
        vTextField(clientPageObj.addClientSignKey.inputLabel()).setValue(label);
        clientPageObj.addClientSignKey.btnNext().click();
    }

    @Step("Add Client CSR details Certification Service to {string} and CSR format {string}")
    public void setAuthCsrDetails(String certificationService, String csrFormat) {
        clientPageObj.addClientCsrDetails.csrService().click();
        selectorOptionOf(certificationService).click();

        clientPageObj.addClientCsrDetails.csrFormat().click();
        selectorOptionOf(csrFormat).click();
        clientPageObj.addClientCsrDetails.btnNext().click();
    }

    @SneakyThrows
    @Step("Add Client Generate CSR is set to organization {string} and csr is created")
    public void wizardGenerateCsr(String org) {
        vTextField(clientPageObj.addClientGenerateCsr.inputOrganizationName()).setValue(org);
        clientPageObj.addClientGenerateCsr.btnNext().click();

        File file = clientPageObj.addClientFinish.submitButton().download();
        assertTrue(file.exists());
    }
}
