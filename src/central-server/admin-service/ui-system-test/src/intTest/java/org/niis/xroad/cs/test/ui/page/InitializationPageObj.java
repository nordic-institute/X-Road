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
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

public class InitializationPageObj {

    public SelenideElement initializationView() {
        return $x("//div[@data-test='central-server-initialization-page-title']");
    }

    public SelenideElement initializationPhaseId() {
        return $x("//div[@data-test='app-toolbar-server-init-phase-id']");
    }

    public SelenideElement confirmPinOKIcon() {
        return $x("//*[@data-test='confirm-pin-append-input-icon']");
    }

    public SelenideElement confirmPinInput() {
        return $x("//input[@data-test='confirm-pin--input']");
    }

    public SelenideElement pinInput() {
        return $x("//input[@data-test='pin--input']");
    }

    public SelenideElement pinValidation() {
        return $x("//span[@data-test='pin--validation']");
    }

    public SelenideElement submitButton() {
        return $x("//button[@data-test='submit-button']");
    }

    public SelenideElement instanceIdentifierInput() {
        return $x("//input[@data-test='instance-identifier--input']");
    }

    public SelenideElement instanceIdentifierValidation() {
        return $x("//span[@data-test='instance-identifier--validation']");
    }

    public SelenideElement serverAddressInput() {
        return $x("//input[@data-test='address--input']");
    }

    public SelenideElement serverAddressValidation() {
        return $x("//span[@data-test='address--validation']");
    }

    public SelenideElement contextualAlertsNote() {
        return $x("//div[@data-test='contextual-alert']");
    }

    public SelenideElement initNotificationNote() {
        return $x("//div[@data-test='continue-init-notification']");
    }
}
