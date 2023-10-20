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

package org.niis.xroad.ss.test.ui.utils;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.tagName;
import static com.codeborne.selenide.Condition.visible;

public class VuetifyHelper {

    private static final String TEXT_FIELD_ROOT_TAG = "div";
    private static final String INPUT_XPATH = ".//input";

    public static Checkbox vCheckbox(final SelenideElement vuetifyTextField) {
        return new Checkbox(vuetifyTextField);
    }

    public static final class Checkbox {
        private final SelenideElement controlElement;

        private Checkbox(final SelenideElement vuetifyCheckbox) {
            this.controlElement = vuetifyCheckbox.shouldBe(tagName(TEXT_FIELD_ROOT_TAG))
                    .shouldHave(cssClass("v-input--checkbox"));
        }

        public void click() {
            controlElement.shouldBe(visible)
                    .$x(".//label")
                    .click();
        }
    }

}
