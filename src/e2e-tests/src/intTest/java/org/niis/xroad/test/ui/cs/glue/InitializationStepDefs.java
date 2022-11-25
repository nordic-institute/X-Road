/**
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
package org.niis.xroad.test.ui.cs.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.test.ui.cs.page.InitializationPage;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;

public class InitializationStepDefs extends BaseUiStepDefs {
    private final InitializationPage initializationPage = new InitializationPage();

    @Step("Initial Configuration form is visible")
    public void initialConfigFormVisible() {
        initializationPage.initializationView().shouldBe(Condition.visible);
        initializationPage.initializationPhaseId().shouldBe(Condition.visible);
    }

    @Step("PIN input is visible, but not confirmed")
    public void pinInputIsPresentAndNotVerified() {
        initializationPage.initializationView().shouldBe(Condition.visible);
        initializationPage.initializationPhaseId().shouldBe(Condition.visible);

        initializationPage.confirmPinOKIcon().shouldNotBe(Condition.visible);
    }

    @Step("PIN {} is entered")
    public void pinIsEntered(String pin) {
        initializationPage.pinInput().setValue(pin);
        initializationPage.initializationPhaseId().shouldBe(Condition.visible);

        initializationPage.confirmPinOKIcon().shouldNotBe(Condition.visible);
    }

    @Step("PIN confirmation is required")
    public void pinConfirmationRequired() {
        initializationPage.confirmPinInput().shouldBe(Condition.visible);
    }

    @Step("Confirmation PIN {} is entered")
    public void confirmationPinIsEntered(String pin) {
        initializationPage.confirmPinInput().setValue(pin);
    }

    @Step("PIN should be marked as matching")
    public void pinConfirmed() {
        initializationPage.confirmPinOKIcon().shouldBe(Condition.visible);
    }

    @Step("PIN should be marked as mismatching")
    public void pinNotConfirmed() {
        initializationPage.confirmPinOKIcon().shouldNotBe(Condition.visible);
    }

    @Step("Submit button is disabled")
    public void submitDisabled() {
        initializationPage.submitButton().shouldBe(Condition.disabled);
    }

    @Step("Submit button is enabled")
    public void submitEnabled() {
        initializationPage.submitButton().shouldBe(Condition.enabled);
    }

    @Step("Submit button is clicked")
    public void doSubmit() {
        initializationPage.submitButton().click();
    }

    @Step("Instance identifier {} is entered")
    public void setInstanceIdentifier(String value) {
        initializationPage.instanceIdentifierInput().setValue(value);
    }

    @Step("Central Server Address {} is entered")
    public void setCentralServerAddress(String value) {
        initializationPage.serverAddressInput().setValue(value);
    }

    @Step("Submission failed with highlighted errors {}")
    public void validateForm(String value) {
        switch (value) {
            case "IDENTIFIER-ERROR":
                initializationPage.instanceIdentifierValidation().shouldBe(Condition.visible);
                break;
            case "ADDRESS-ERROR":
                initializationPage.serverAddressValidation().shouldBe(Condition.visible);
                break;
            case "PIN-ERROR":
                initializationPage.pinValidation().shouldBe(Condition.visible);
                break;
            default:
                throw new IllegalArgumentException("Cannot process undefined error type " + value);
        }

        initializationPage.contextualAlertsNote().shouldBe(Condition.visible);
    }

    @Step("Central Server is successfully initialized")
    public void validateInitialized() {
        initializationPage.initNotificationNote().shouldBe(Condition.visible);
    }
}
