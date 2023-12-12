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

import com.codeborne.selenide.Selenide;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.LoginPageObj;

import java.time.Duration;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class AuthStepDefs extends BaseUiStepDefs {
    private final LoginPageObj loginPageObj = new LoginPageObj();

    @Step("SecurityServer login page is open")
    public void openPage() {
        Selenide.open(targetHostUrlProvider.getUrl());
    }

    @Step("Login form is visible")
    public void loginFormVisible() {
        loginPageObj.inputUsername().shouldBe(visible);
        loginPageObj.inputPassword().shouldBe(visible);
    }

    @Step("User {} logs in to {} with password {}")
    public void doLogin(final String username, final String target, final String password) {

        loginPageObj.inputUsername().shouldBe(visible);
        vTextField(loginPageObj.inputUsername()).setValue(username);
        loginPageObj.inputPassword().shouldBe(visible);
        vTextField(loginPageObj.inputPassword()).setValue(password);

        loginPageObj.btnLogin()
                .shouldBe(visible)
                .shouldBe(enabled)
                .click();
    }

    @Step("Error message for incorrect credentials is shown")
    public void errorMessageIsShown() {
        loginPageObj.inputErrorMessageWithText("Wrong username or password")
                .shouldBe(visible);
    }

    @Step("logout button is being clicked")
    public void logoutButtonIsClicked() {
        commonPageObj.menu.usernameButton().click();
        commonPageObj.menu.logout().click();
    }

    @Given("User becomes idle")
    public void userBecomesIdle() {
        Selenide.sleep(1);
    }


    @Given("after {} seconds, session timeout popup appears")
    public void errorMessageAboutTimeoutAppears(int duration) {
        commonPageObj.btnSessionExpired()
                .shouldBe(visible, Duration.ofSeconds(duration));

    }

    @Given("OK is clicked on timeout notification popup")
    public void okIsClickedOnTimeoutNotificationPopup() {
        commonPageObj.btnSessionExpired()
                .shouldBe(visible)
                .shouldBe(enabled)
                .click();
    }
}
