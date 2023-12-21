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
package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

public class InitializationPageObj {

    public final WizardOwnerMember wizardOwnerMember = new WizardOwnerMember();
    public final WizardTokenPin wizardTokenPin = new WizardTokenPin();
    public final WizardAnchor wizardAnchor = new WizardAnchor();

    public SelenideElement alertSoftTokenPin() {
        return $x("//div[@data-test='global-alert-soft-token-pin']");
    }

    public SelenideElement initializationView() {
        return $x("//div[@data-test='wizard-title']")
                .$x(".//span[contains(text(), 'Initial configuration')]");
    }

    public static class WizardAnchor {

        public SelenideElement btnConfirmAnchorDetails() {
            return $x("//button[@data-test='system-parameters-upload-configuration-anchor-dialog-confirm-button']");
        }

        public SelenideElement inputFile() {
            return $x("//input[@type='file']");
        }

        public SelenideElement btnContinue() {
            return $x("//button[@data-test='configuration-anchor-save-button']");
        }
    }

    public static class WizardOwnerMember {
        public SelenideElement securityServerCode() {
            return $x("//div[@data-test='security-server-code-input']");
        }

        public SelenideElement inputMemberCode() {
            return $x("//div[@data-test='member-code-input']");
        }

        public SelenideElement selectMemberClass() {
            return $x("//div[@data-test='member-class-input']");
        }

        public SelenideElement selectMemberClassOption(String option) {
            var xpath = "//div[@role='listbox']//div[@role='option' and contains(./descendant-or-self::*/text(),'%s')]";
            return $x(String.format(xpath, option));
        }

        public SelenideElement btnContinue() {
            return $x("//button[@data-test='owner-member-save-button']");
        }
    }

    public static class WizardTokenPin {
        public SelenideElement alertTokenPolicyEnabled() {
            return $x("//*[@data-test='alert-token-policy-enabled']");
        }

        public SelenideElement inputPin() {
            return $x("//div[@data-test='pin-input']");
        }

        public SelenideElement inputConfirmPin() {
            return $x("//div[@data-test='confirm-pin-input']");
        }

        public SelenideElement btnContinue() {
            return $x("//button[@data-test='token-pin-save-button']");
        }
    }
}

