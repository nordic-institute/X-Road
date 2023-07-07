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

@SuppressWarnings("InnerClassMayBeStatic")
public class MemberDetailsPageObj {
    private final EditNameDialog editNameDialog = new EditNameDialog();
    private final DeleteDialog deleteDialog = new DeleteDialog();


    public SelenideElement memberNameCard(String name) {
        var xpath = "//div[@data-test='member-name-card']//div[2]//div[contains(text(), '%s')]";
        return $x(String.format(xpath, name));
    }

    public SelenideElement memberClassCard(String name) {
        var xpath = "//div[@data-test='member-class-card']//div[2]//div[contains(text(), '%s')]";
        return $x(String.format(xpath, name));
    }

    public SelenideElement memberCodeCard(String name) {
        var xpath = "//div[@data-test='member-code-card']//div[2]//div[contains(text(), '%s')]";
        return $x(String.format(xpath, name));
    }

    public SelenideElement tableTitle(String name) {
        var xpath = "//div[@class='xrd-view-title' and contains(text(), '%s')]";
        return $x(String.format(xpath, name));
    }

    public SelenideElement btnDelete() {
        return $x("//div[@data-test='delete-member']");
    }

    public SelenideElement btnEdit() {
        return $x("//button[@data-test='info-card-edit-button']");
    }

    public SelenideElement ownerServersSearch() {
        return $x("//div[@data-test='search-owned-servers']");
    }

    public SelenideElement globalGroupsSearch() {
        return $x("//div[@data-test='search-global-groups']");
    }

    public SelenideElement ownerServersTable() {
        return $x("//div[@data-test='owned-servers-table']");
    }

    public SelenideElement globalGroupsTable() {
        return $x("//div[@data-test='global-groups-table']");
    }

    public EditNameDialog editNameDialog() {
        return editNameDialog;
    }

    public DeleteDialog deleteDialog() {
        return deleteDialog;
    }

    public class EditNameDialog {
        public SelenideElement inputMemberName() {
            return $x("//input[@data-test='edit-member-name']");
        }
    }


    public class DeleteDialog {
        public SelenideElement inputMemberCode() {
            return $x("//input[@data-test='member-code']");
        }
    }
}
