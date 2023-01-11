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
import org.niis.xroad.cs.test.ui.page.GlobalConfigurationPageObj;

public class GlobalConfigurationStepDefs extends BaseUiStepDefs {

    private final GlobalConfigurationPageObj globalConfigurationPageObj = new GlobalConfigurationPageObj();

    @Step("Internal configuration sub-tab is selected")
    public void selectInternalCfgTab() {
        globalConfigurationPageObj.internalConfiguration().click();
    }

    @Step("Details for Token: {} is expanded")
    public void expandToken(final String tokenKey) {
        globalConfigurationPageObj.tokenLabel(tokenKey).click();
    }

    @Step("User logs in token: {} with PIN: {}")
    public void loginToken(final String tokenKey, final String tokenPin) {
        globalConfigurationPageObj.loginButton(tokenKey)
                .shouldBe(Condition.enabled)
                .click();

        globalConfigurationPageObj.tokenLoginDialog.inputPin().setValue(tokenPin);
        globalConfigurationPageObj.tokenLoginDialog.btnLogin()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Token: {} is logged-out")
    public void tokenIsLoggedOut(final String tokenKey) {
        globalConfigurationPageObj.loginButton(tokenKey).shouldBe(Condition.enabled);
    }
    @Step("Add key button is disabled for token: {}")
    public void addKeyDisabled(final String tokenKey) {
        globalConfigurationPageObj.addSigningKey(tokenKey).shouldBe(Condition.disabled);
    }
    @Step("User adds signing key for token: {} with name: {}")
    public void addSigningKey(final String tokenKey, final String keyLabel) {
        globalConfigurationPageObj.addSigningKey(tokenKey)
                .shouldBe(Condition.enabled)
                .click();

        globalConfigurationPageObj.addSigningKeyDialog.inputLabel().setValue(keyLabel);
        globalConfigurationPageObj.addSigningKeyDialog.btnSave()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

}
