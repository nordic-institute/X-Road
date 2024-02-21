/*
 * The MIT License
 *
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

package org.niis.xroad.cs.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.SecurityServerDetailsPageObj;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class SecurityServerDetailsStepDefs extends BaseUiStepDefs {

    private final SecurityServerDetailsPageObj securityServerDetailsPageObj = new SecurityServerDetailsPageObj();

    @Step("security server owner name: {string}, class: {string} and code: {string} are properly displayed")
    public void ownerDetailsAreVisible(final String ownerName, final String ownerClass, final String ownerCode) {
        securityServerDetailsPageObj.ownerName()
                .shouldBe(visible)
                .shouldHave(Condition.exactText(ownerName));
        securityServerDetailsPageObj.ownerClass()
                .shouldBe(visible)
                .shouldHave(Condition.exactText(ownerClass));
        securityServerDetailsPageObj.ownerCode()
                .shouldBe(visible)
                .shouldHave(Condition.exactText(ownerCode));
    }

    @Step("security server code: {string} is properly displayed")
    public void serverCodeIsVisible(final String serverCode) {
        securityServerDetailsPageObj.serverCode()
                .shouldBe(visible).
                shouldHave(Condition.exactText(serverCode));
    }

    @Step("security server address: {string} is displayed")
    public void serverAddressIsVisible(final String serverAddress) {
        securityServerDetailsPageObj.serverAddress()
                .shouldBe(visible).
                shouldHave(Condition.exactText(serverAddress));
    }

    @Step("security server registration date and time is properly displayed")
    public void serverRegisteredIsVisible() {
        securityServerDetailsPageObj.serverRegistered().shouldHave(Condition.matchText("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Step("user opens server edit address dialog")
    public void openAddressEditDialog() {
        securityServerDetailsPageObj.btnChangeAddress()
                .shouldBe(visible)
                .shouldBe(Condition.enabled)
                .click();
    }

    @Step("enters new server address: {string}")
    public void enterNewServerAddress(final String serverAddress) {
        vTextField(securityServerDetailsPageObj.editAddressDialog().inputAddress())
                .clear()
                .setValue(serverAddress);
    }

    @Step("saves server address")
    public void saveNewAddress() {
        securityServerDetailsPageObj.editAddressDialog().btnSave().shouldBe(visible)
                .shouldBe(Condition.enabled)
                .click();
        securityServerDetailsPageObj.editAddressDialog().dialog().shouldNotBe(visible);

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("closes server address dialog")
    public void closeEditAddressDialog() {
        securityServerDetailsPageObj.editAddressDialog().btnCancel().shouldBe(visible)
                .shouldBe(Condition.enabled)
                .click();
        securityServerDetailsPageObj.editAddressDialog().dialog().shouldNotBe(visible);
    }

    @Step("user opens delete security server dialog")
    public void openDeleteServerDialog() {
        securityServerDetailsPageObj.btnDeleteSecurityServer()
                .shouldBe(visible)
                .shouldBe(Condition.enabled)
                .click();

        securityServerDetailsPageObj.deleteSecurityServerDialog().btnDelete().shouldBe(disabled);
    }

    @Step("enters server code: {string}")
    public void enterServerCode(final String serverCode) {
        vTextField(securityServerDetailsPageObj.deleteSecurityServerDialog().inputSeverCode())
                .setValue(serverCode);
    }

    @Step("delete button is disabled")
    public void deleteBtnIsDisabled() {
        securityServerDetailsPageObj.deleteSecurityServerDialog().btnDelete()
                .shouldBe(disabled);
    }

    @Step("deletes security server")
    public void deleteSecurityServer() {
        securityServerDetailsPageObj.deleteSecurityServerDialog().btnDelete()
                .shouldBe(enabled)
                .shouldBe(visible)
                .click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("closes delete security server dialog")
    public void closeDeleteServerDialog() {
        securityServerDetailsPageObj.deleteSecurityServerDialog().btnCancel().shouldBe(visible)
                .shouldBe(Condition.enabled)
                .click();
        securityServerDetailsPageObj.deleteSecurityServerDialog().dialog().shouldNotBe(visible);
    }
}

