/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
import org.niis.xroad.ss.test.ui.page.InitializationPageObj;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.selectorOptionOf;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class InitializationStepDefs extends BaseUiStepDefs {
    private final InitializationPageObj initializationPageObj = new InitializationPageObj();

    @Step("Initial Configuration form is visible")
    public void initialConfigFormVisible() {
        initializationPageObj.initializationView().shouldBe(visible);
    }

    @Step("Configuration anchor {string} is uploaded")
    public void uploadAnchor(String fileName) {

        initializationPageObj.wizardAnchor.inputFile().uploadFromClasspath("files/trusted-anchor/" + fileName);
    }

    @Step("Configuration anchor details are confirmed")
    public void confirmAnchor() {
        initializationPageObj.wizardAnchor.btnConfirmAnchorDetails()
                .shouldBe(visible)
                .click();
    }

    @Step("Configuration anchor selection is submitted")
    public void submitAnchor() {
        initializationPageObj.wizardAnchor.btnContinue()
                .shouldBe(visible)
                .click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Initial configuration of Owner member is set to class: {}, code: {} & Security Server Code: {}")
    public void ownerMemberConfigure(String memberClass, String memberCode, String securityServerCode) {

        initializationPageObj.wizardOwnerMember.selectMemberClass().click();
        selectorOptionOf(memberClass).click();

        vTextField(initializationPageObj.wizardOwnerMember.inputMemberCode()).setValue(memberCode);

        vTextField(initializationPageObj.wizardOwnerMember.securityServerCode()).setValue(securityServerCode);

        initializationPageObj.wizardOwnerMember.btnContinue().shouldBe(enabled);
    }

    @Step("Owner member configuration is submitted")
    public void memberOwnerMemberSubmit() {
        initializationPageObj.wizardOwnerMember.btnContinue().shouldBe(enabled).click();
        initializationPageObj.wizardTokenPin.btnContinue().shouldNotBe(enabled);
    }

    @Step("Alert about token policy being enforced is present")
    public void alertTokenPolicyIsPresent() {
        initializationPageObj.wizardTokenPin.alertTokenPolicyEnabled().shouldBe(visible);
    }

    @Step("PIN is set to {string}")
    public void pinInputIsPresentAndNotVerified(String pin) {
        initializationPageObj.initializationView().shouldBe(visible);
        initializationPageObj.wizardTokenPin.inputPin().shouldBe(visible);
        vTextField(initializationPageObj.wizardTokenPin.inputPin()).setValue(pin);
    }

    @Step("Confirmation PIN is set to {string}")
    public void pinInputIsPresentAndVerified(String pin) {
        initializationPageObj.initializationView().shouldBe(visible);
        initializationPageObj.wizardTokenPin.inputConfirmPin().shouldBe(visible);
        vTextField(initializationPageObj.wizardTokenPin.inputConfirmPin()).setValue(pin);

        initializationPageObj.wizardTokenPin.btnContinue().shouldBe(enabled);
    }

    @Step("Server id exist warning is confirmed")
    public void confirmExistingServer() {
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldHave(text("Server initialised"));
        commonPageObj.snackBar.btnClose().shouldBe(visible).click();
    }

    @Step("Initial Configuration is submitted")
    public void initSubmitted() {
        initializationPageObj.wizardTokenPin.btnContinue().shouldBe(enabled).click();
    }

    @Step("Soft token pin alert is clicked")
    public void tokenAlertNotLogged() {
        initializationPageObj.alertSoftTokenPin().shouldBe(visible).click();
    }
}
