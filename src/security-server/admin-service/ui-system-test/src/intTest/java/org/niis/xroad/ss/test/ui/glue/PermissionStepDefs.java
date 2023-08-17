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
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.ClientPageObj;

public class PermissionStepDefs extends BaseUiStepDefs {
    private final ClientPageObj clientPageObj = new ClientPageObj();

    @Step("Add clients button is {selenideValidation}")
    public void validateAddClients(ParameterMappers.SelenideValidation validation) {
        clientPageObj.btnAddClient()
                .shouldBe(validation.getSelenideCondition());
    }

    @Step("Clients Tab is {selenideValidation}")
    public void validateClients(ParameterMappers.SelenideValidation validation) {
        commonPageObj.menu.clientsTab().shouldBe(validation.getSelenideCondition());
    }

    @Step("Settings Tab is {selenideValidation}")
    public void validateSettings(ParameterMappers.SelenideValidation validation) {
        commonPageObj.menu.settingsTab().shouldBe(validation.getSelenideCondition());
    }

    @Step("Keys and Certificates Tab is {selenideValidation}")
    public void validateKeys(ParameterMappers.SelenideValidation validation) {
        commonPageObj.menu.keysAndCertificatesTab().shouldBe(validation.getSelenideCondition());
    }

    @Step("Diagnostics Tab is {selenideValidation}")
    public void validateDiagnostics(ParameterMappers.SelenideValidation validation) {
        commonPageObj.menu.diagnosticsTab().shouldBe(validation.getSelenideCondition());
    }

    @Step("Api Keys Sub-Tab is {selenideValidation}")
    public void validateApiKeys(ParameterMappers.SelenideValidation validation) {
        commonPageObj.subMenu.apiKeysTab().shouldBe(validation.getSelenideCondition());
    }

    @Step("Backup and Restore Sub-Tab is {selenideValidation}")
    public void validateBackupAndRestore(ParameterMappers.SelenideValidation validation) {
        commonPageObj.subMenu.backupAndRestoresTab().shouldBe(validation.getSelenideCondition());
    }
}

