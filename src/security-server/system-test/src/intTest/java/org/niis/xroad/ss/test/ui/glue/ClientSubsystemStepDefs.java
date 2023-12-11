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
import org.niis.xroad.ss.test.ui.page.ClientPageObj;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vCheckbox;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class ClientSubsystemStepDefs extends BaseUiStepDefs {
    private final ClientPageObj clientPageObj = new ClientPageObj();

    @Step("Subsystem selection window is opened")
    public void openSelect() {
        clientPageObj.subsystem.btnSelect().click();
    }

    @Step("Subsystem with ID {string} is selected from the window")
    public void selectSubsystem(String subsystem) {
        clientPageObj.subsystem.radioSubsystemById(subsystem).click();
        clientPageObj.subsystem.btnSelectDialogSave().click();
    }

    @Step("Subsystem code is set to {string}")
    public void setSubsystem(String subsystem) {
        vTextField(clientPageObj.subsystem.inputSubsystem()).setValue(subsystem);
    }

    @Step("Register subsystem is unchecked")
    public void checkRegisterSubsystem() {
        vCheckbox(clientPageObj.subsystem.inputRegisterSubsystem()).click();
    }

    @Step("Register client send registration request dialog is confirmed")
    public void confirmSendRequest() {
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Add subsystem form is set to MemberName: {string}, MemberClass: {string}, MemberCode: {string}, SubsystemCode: {string}")
    public void validateAddSubsystemForm(String memberName, String memberClass, String memberCode, String subsystemCode) {
        clientPageObj.subsystem.memberNameValue().shouldBe(text(memberName));
        clientPageObj.subsystem.memberClassValue().shouldBe(text(memberClass));
        clientPageObj.subsystem.memberCodeValue().shouldBe(text(memberCode));
        vTextField(clientPageObj.subsystem.inputSubsystem()).shouldHaveText(subsystemCode);
    }

    @Step("Add subsystem form is submitted")
    public void submitForm() {
        clientPageObj.subsystem.btnSubmit()
                .shouldBe(enabled)
                .click();
    }

    @Step("Add subsystem form is closed")
    public void closeForm() {
        clientPageObj.subsystem.btnCancel().click();
    }
}
