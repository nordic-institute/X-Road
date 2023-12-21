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

package org.niis.xroad.common.test.ui.utils;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebElementCondition;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.focused;
import static com.codeborne.selenide.Condition.tagName;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$x;
import static java.lang.String.format;

public final class VuetifyHelper {

    private static final String ROOT_TAG = "div";
    private static final String INPUT_XPATH = ".//input";

    public static TextField vTextField(final SelenideElement vuetifyTextField) {
        return new TextField(vuetifyTextField);
    }

    public static Checkbox vCheckbox(final SelenideElement vuetifyCheckboxField) {

        return new Checkbox(vuetifyCheckboxField);
    }

    public static Radio vRadio(final SelenideElement vuetifyRadioField) {
        return new Radio(vuetifyRadioField);
    }

    public static SelenideElement selectorOptionOf(String value) {
        var xpath = "//div[@role='listbox']//div[contains(@class, 'v-list-item') and contains(./descendant-or-self::*/text(),'%s')]";
        return $x(format(xpath, value));
    }

    private VuetifyHelper() {
    }


    public static final class Checkbox {
        private final SelenideElement controlElement;
        private final SelenideElement input;

        private Checkbox(final SelenideElement vuetifyCheckbox) {
            this.controlElement = vuetifyCheckbox.shouldBe(tagName(ROOT_TAG))
                    .shouldHave(cssClass("v-checkbox"));
            this.input = this.controlElement.$x(INPUT_XPATH);
        }

        public Checkbox shouldBeChecked() {
            controlElement.$x(".//i").shouldHave(cssClass("mdi-checkbox-marked"));
            return this;
        }

        public boolean isChecked() {
            return controlElement.$x(".//i").has(cssClass("mdi-checkbox-marked"));
        }

        public Checkbox shouldBeUnchecked() {
            controlElement.$x(".//i").shouldHave(cssClass("mdi-checkbox-blank-outline"));
            return this;
        }

        public Checkbox scrollIntoView(boolean alignToTop) {
            controlElement.scrollIntoView(alignToTop);
            return this;
        }

        public void click() {
            controlElement.shouldBe(visible);
            input.click();
        }
    }

    public static final class TextField {

        private final SelenideElement controlElement;
        private final SelenideElement input;

        private TextField(final SelenideElement vuetifyTextField) {
            this.controlElement = vuetifyTextField.shouldBe(tagName(ROOT_TAG))
                    .shouldHave(cssClass("v-text-field"));
            this.input = this.controlElement.$x(INPUT_XPATH);
        }

        private void focus() {
            controlElement
                    .shouldBe(enabled)
                    .shouldBe(visible);
            controlElement.$(".v-input__control").click();
        }

        public TextField clear() {
            focus();
            SeleniumUtils.clearInput(this.input.shouldBe(enabled, visible, focused));

            return this;
        }

        public TextField setValue(String text) {
            focus();
            input.shouldBe(focused)
                    .shouldBe(visible)
                    .shouldBe(enabled)
                    .setValue(text);
            return this;
        }

        public void sendKeys(CharSequence... keysToSend) {
            input.sendKeys(keysToSend);
        }

        public void shouldHaveText(String text) {
            input.shouldHave(value(text));
        }

        public TextField shouldBe(WebElementCondition condition) {
            input.shouldBe(condition);
            return this;
        }
    }

    public static final class Radio {
        private final SelenideElement controlElement;

        private Radio(final SelenideElement vuetifyRadio) {
            this.controlElement = vuetifyRadio.shouldBe(tagName(ROOT_TAG))
                    .shouldHave(cssClass("v-radio"));
        }

        public Radio shouldBeChecked() {
            controlElement.$x(".//i").shouldHave(cssClass("mdi-radiobox-marked"));
            return this;
        }

        public Radio shouldBeUnChecked() {
            controlElement.$x(".//i").shouldHave(cssClass("mdi-radiobox-blank"));
            return this;
        }

        public void click() {
            controlElement.shouldBe(visible)
                    .$x(INPUT_XPATH)
                    .click();
        }
    }
}
