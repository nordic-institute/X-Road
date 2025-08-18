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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;

public class AdminUsersPageObj {

    public final AddAdminUserWizard addAdminUserWizard = new AddAdminUserWizard();

    public SelenideElement table() {
        return $x("//div[@data-test='admin-users-table']//table");
    }

    public ElementsCollection tableAdminUsersRows() {
        return $$x("//div[@data-test='admin-users-table']//table//tbody//tr");
    }

    public SelenideElement checkboxRole(String role) {
        var xpath = "//div[@data-test='role-%s-checkbox']";
        return $x(String.format(xpath, role));
    }

    public SelenideElement btnAddUser() {
        return $x("//button[@data-test='add-admin-user-button']");
    }

    public SelenideElement adminUserRow(String username) {
        var xpath = "//div[@data-test='admin-users-table']//div[@class='username' and contains(text(), '%s')]";
        return $x(String.format(xpath, username));
    }

    public SelenideElement adminUserRoles(String username) {
        var xpath = "//span[@data-test='admin-user-row-%s-roles']";
        return $x(String.format(xpath, username));
    }

    public SelenideElement btnEditAdminUser(String username) {
        var xpath = "//button[@data-test='admin-user-row-%s-edit-button']";
        return $x(String.format(xpath, username));
    }

    public SelenideElement btnChangeAdminUserPassword(String username) {
        var xpath = "//button[@data-test='admin-user-row-%s-change-password-button']";
        return $x(String.format(xpath, username));
    }

    public SelenideElement oldPasswordInput() {
        return $x("//div[@data-test='old-password-input']");
    }

    public SelenideElement newPasswordInput() {
        return $x("//div[@data-test='new-password-input']");
    }

    public SelenideElement newPasswordConfirmationInput() {
        return $x("//div[@data-test='new-password-confirm-input']");
    }

    public SelenideElement btnSavePasswordChange() {
        return $x("//button[@data-test='dialog-save-button']");
    }

    public SelenideElement btnDeleteAdminUser(String username) {
        var xpath = "//button[@data-test='admin-user-row-%s-delete-button']";
        return $x(String.format(xpath, username));
    }

    public static class AddAdminUserWizard {

        public SelenideElement stepOneWindow() {
            return $x("//div[@data-test='add-admin-user-step-1']");
        }

        public SelenideElement checkboxRole(String role) {
            var xpath = "//div[@data-test='add-admin-user-step-1']//div[@data-test='role-%s-checkbox']";
            return $x(String.format(xpath, role));
        }

        public SelenideElement btnNext() {
            return $x("//div[@data-test='add-admin-user-step-1']//button[@data-test='next-button']");
        }

        public SelenideElement usernameInput() {
            return $x("//div[@data-test='add-admin-user-step-2']//div[@data-test='username-input']");
        }

        public SelenideElement passwordInput() {
            return $x("//div[@data-test='add-admin-user-step-2']//div[@data-test='password-input']");
        }

        public SelenideElement confirmationPasswordInput() {
            return $x("//div[@data-test='add-admin-user-step-2']//div[@data-test='confirm-password-input']");
        }

        public SelenideElement btnSave() {
            return $x("//div[@data-test='add-admin-user-step-2']//button[@data-test='add-button']");
        }

        public SelenideElement btnCancel() {
            return $x("//div[@data-test='add-admin-user-stepper-view']//button[@data-test='cancel-button']");
        }

    }

}
