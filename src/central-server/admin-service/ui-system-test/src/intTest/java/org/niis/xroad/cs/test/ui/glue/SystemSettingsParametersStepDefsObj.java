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
package org.niis.xroad.cs.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.SystemSettingsParametersPageObj;

public class SystemSettingsParametersStepDefsObj extends BaseUiStepDefs {
    private final SystemSettingsParametersPageObj systemSettingsParametersPageObj = new SystemSettingsParametersPageObj();

    @Step("Central Server address {} entered in popup")
    public void setCentralServerAddress(String address) {
        clearInput(systemSettingsParametersPageObj.editDialog.inputCentralServerAddress())
                .setValue(address);
    }

    @Step("System Parameters card is visible")
    public void systemParametersIsVisible() {
        systemSettingsParametersPageObj.systemParametersCard().shouldBe(Condition.visible);
    }

    @Step("Instance Identifier is {}")
    public void instanceIdentifierIs(String value) {
        systemSettingsParametersPageObj.instanceIdentifierField().shouldBe(Condition.text(value));
    }

    @Step("Central Server address is {}")
    public void centralServerAddressIs(String value) {
        systemSettingsParametersPageObj.centralServerAddressField().shouldBe(Condition.text(value));
    }

    @Step("Central Server address edit dialog is opened")
    public void centralServerAddressEditDialogIsOpened() {
        systemSettingsParametersPageObj.btnEdit().click();
        systemSettingsParametersPageObj.editDialog.inputCentralServerAddress().shouldBe(Condition.visible);
    }
}
