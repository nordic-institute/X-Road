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

@SuppressWarnings("InnerClassMayBeStatic")
public class MemberPageObj {
    private final AddDialog addDialog = new AddDialog();

    public SelenideElement btnAddMember() {
        return $x("//button[@data-test='add-member-button']");
    }

    public SelenideElement searchIcon() {
        return $x("//div[@data-test='search-query-field']");
    }

    public SelenideElement searchInput() {
        return $x("//div[@data-test='search-query-field']//input");
    }

    public SelenideElement listRowOf(String memberName) {
        var xpath = "//div[@data-test='members-table']//table//tbody//tr//td//div[contains(text(), '%s')]";
        return $x(String.format(xpath, memberName));
    }

    public SelenideElement listRowOf(String memberName, String memberCode, String memberClass) {
        var xpath = "//div[@data-test='members-view']//table/tbody/tr[(normalize-space(td[1]/div/text()) = '%s') "
                + " and (td[2] = '%s') and (td[3] = '%s')]";

        return $x(String.format(xpath, memberName, memberClass, memberCode));
    }

    public AddDialog addDialog() {
        return addDialog;
    }

    public class AddDialog {
        public SelenideElement inputMemberName() {
            return $x("//div[@data-test='add-member-name-input']");
        }

        public SelenideElement inputMemberCode() {
            return $x("//div[@data-test='add-member-code-input']");
        }

        public SelenideElement selectMemberClass() {
            return $x("//div[@data-test='add-member-class-input']");
        }

        public SelenideElement selectMemberClassOption(String option) {
            var xpath = "//div[@role='listbox']//div[contains(@class, 'v-list-item') and contains(./descendant-or-self::*/text(),'%s')]";
            return $x(String.format(xpath, option));
        }
    }
}
