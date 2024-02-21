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
package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Selenide;
import io.cucumber.java.en.Step;

import static com.codeborne.selenide.Condition.text;
import static java.time.Duration.ofSeconds;

@SuppressWarnings("checkstyle:MagicNumber")
public class CommonStepDefs extends BaseUiStepDefs {

    @Step("Page is prepared to be tested")
    public void preparePage() {
        Selenide.executeJavaScript("""
                window.e2eTestingMode = true;
                      const style = `
                      <style>
                        *, ::before, ::after {
                            transition:none !important;
                        }
                      </style>`;
                      document.head.insertAdjacentHTML('beforeend', style);""");
    }

    @Step("error: {string} was displayed")
    public void errorIsShown(final String error) {
        commonPageObj.alerts.alert(error)
                .shouldBe(Condition.visible, ofSeconds(15));
    }

    @Step("Dialog Save button is clicked")
    public void clickDialogSave() {
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Dialog data is saved and success message {string} is shown")
    public void dialogSave(String message) {
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().should(text(message));
    }

    @Step("Dialog data is saved and error message {string} is shown")
    public void dialogSaveError(String message) {
        commonPageObj.dialog.btnSave().click();

        errorIsShown(message);
    }

    @Step("Dialog is closed")
    public void dialogClose() {
        commonPageObj.dialog.btnCancel().click();
    }

    @Step("Form shows an error {string}")
    public void formError(String errorMessage) {
        commonPageObj.form.inputErrorMessage().should(Condition.partialText(errorMessage));
    }

    @Step("snackbar is closed")
    public void snackbarIsClosed() {
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("take screenshot {string}")
    public void takeScreenShot(String name) {
        super.takeScreenshot(name);
    }

}
