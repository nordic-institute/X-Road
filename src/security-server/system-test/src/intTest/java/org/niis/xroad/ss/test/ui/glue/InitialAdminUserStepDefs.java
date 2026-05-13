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

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.CommonPageObj;
import org.niis.xroad.ss.test.ui.page.InitialAdminUserPageObj;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.test.framework.core.ui.utils.VuetifyHelper.vTextField;

public class InitialAdminUserStepDefs extends BaseUiStepDefs {

    private final InitialAdminUserPageObj initialAdminUserPageObj = new InitialAdminUserPageObj();
    private final CommonPageObj commonPageObj = new CommonPageObj();

    @Given("SecurityServer page {} is open")
    public void openPage(String path) {
        selenideManager.open("https://ui:4000/#" + path);
    }

    @Step("Initial admin user creation form is visible")
    public void formVisible() {
        initialAdminUserPageObj.view().shouldBe(visible);
        initialAdminUserPageObj.inputUsername().shouldBe(visible);
        initialAdminUserPageObj.inputPassword().shouldBe(visible);
        initialAdminUserPageObj.inputConfirmPassword().shouldBe(visible);
    }

    @Step("Initial admin username is set to {}")
    public void setUsername(String username) {
        vTextField(initialAdminUserPageObj.inputUsername()).clear().setValue(username);
    }

    @Step("Initial admin password is set to {}")
    public void setPassword(String password) {
        vTextField(initialAdminUserPageObj.inputPassword()).clear().setValue(password);
    }

    @Step("Initial admin password confirmation is set to {}")
    public void setConfirmPassword(String password) {
        vTextField(initialAdminUserPageObj.inputConfirmPassword()).clear().setValue(password);
    }

    @Step("Initial admin user creation is submitted")
    public void submit() {
        initialAdminUserPageObj.btnSubmit().shouldBe(visible).shouldBe(enabled).click();
    }

    @Step("Initial admin user creation form shows weak password error")
    public void weakPasswordError() {
        commonPageObj.alerts.alert("The provided password was too weak").shouldBe(visible);
        initialAdminUserPageObj.view().shouldBe(visible);
    }

    @Step("Initial admin user creation submit button is disabled")
    public void submitDisabled() {
        initialAdminUserPageObj.btnSubmit().shouldBe(visible).shouldBe(disabled);
    }
}
