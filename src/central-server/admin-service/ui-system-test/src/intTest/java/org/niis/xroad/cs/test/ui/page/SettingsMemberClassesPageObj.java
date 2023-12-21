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
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;
import static org.openqa.selenium.By.xpath;

@SuppressWarnings("InnerClassMayBeStatic")
public class SettingsMemberClassesPageObj {
    public final AddEditDialog addEditDialog = new AddEditDialog();

    public SelenideElement btnAddMemberClass() {
        return $x("//*[@data-test='system-settings-add-member-class-button']");
    }

    public SelenideElement listRowOf(String code, String desc) {
        var xpath = "//div[@data-test='member-classes-list']//table/tbody/tr[td[1]= '%s'  and (td[2] = '%s')]";

        return $x(String.format(xpath, code, desc));
    }

    public SelenideElement listRowOf(String code) {
        var xpath = "//div[@data-test='member-classes-list']//table/tbody/tr[td[1]= '%s']";

        return $x(String.format(xpath, code));
    }

    public SelenideElement listRowPartialDescOf(String desc) {
        String selector =
                String.format("//table//tbody//tr//td[contains(text(), \"%s\")]", desc);
        return $x(selector);
    }

    public SelenideElement listRowBtnEditOf(String code) {
        var xpath = String.format("//div[@data-test='member-classes-list']//table/tbody/tr[td[1]= '%s']", code);

        return $x(xpath)
                .find(xpath(".//button[@data-test='system-settings-edit-member-class-button']"));
    }

    public SelenideElement listRowBtnDeleteOf(String code) {
        var xpath = String.format("//div[@data-test='member-classes-list']//table/tbody/tr[td[1]= '%s']", code);

        return $x(xpath)
                .find(xpath(".//button[@data-test='system-settings-delete-member-class-button']"));
    }

    public SelenideElement listSizeSelector() {
        return $x("//div[@data-test='member-classes-list']"
                + "//div[contains(@class, 'v-data-table-footer__items-per-page')]"
                + "/div[contains(@class, 'v-select')]");
    }

    public SelenideElement listSizeSelectorText() {
        return $x("//div[@data-test='member-classes-list']"
                + "//div[contains(@class, 'v-data-table-footer__items-per-page')]"
                + "//span[@class='v-select__selection-text']");
    }

    public SelenideElement listSizeSelectorOptionOf(String value) {
        return $x(String.format("//div[@class='v-list-item__content']//div[text() = '%s']", value));
    }

    public class AddEditDialog {
        public SelenideElement inputMemberClassCode() {
            return $x("//div[@data-test='system-settings-member-class-code-edit-field']");
        }

        public SelenideElement inputMemberClassDescription() {
            return $x("//div[@data-test='system-settings-member-class-description-edit-field']");
        }
    }
}
