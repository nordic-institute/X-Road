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

package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.WebElementCondition;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers.SelenideValidation;
import org.niis.xroad.ss.test.ui.page.AdminUsersPageObj;


import java.util.List;
import java.util.Map;

import static java.time.Duration.ofSeconds;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vCheckbox;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class AdminUsersStepDefs extends BaseUiStepDefs {

    private final AdminUsersPageObj adminUsersPageObj = new AdminUsersPageObj();

    @Step("Admin Users table has {} entries")
    public void validateTimestampingTable(int size) {
        adminUsersPageObj.table().shouldBe(Condition.visible);
        adminUsersPageObj.tableAdminUsersRows().shouldBe(CollectionCondition.size(size));
    }

    @Step("Add Admin Users wizard is opened")
    public void addAdminUserWizardIsOpened() {
        adminUsersPageObj.btnAddUser().click();
        adminUsersPageObj.addAdminUserWizard.stepOneWindow().shouldBe(Condition.visible);
    }

    @Step("Role {string} is being checked in the wizard")
    public void selectRoleInWizard(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        vCheckbox(adminUsersPageObj.addAdminUserWizard.checkboxRole(roleEnum)).click();
    }

    @Step("Wizard's Next button is clicked")
    public void clickWizardNext() {
        adminUsersPageObj.addAdminUserWizard.btnNext().click();
    }

    @Step("Username {} is entered")
    public void usernameIsEntered(String username) {
        vTextField(adminUsersPageObj.addAdminUserWizard.usernameInput()).setValue(username);
    }

    @Step("Password {} is entered")
    public void passwordIsEntered(String password) {
        vTextField(adminUsersPageObj.addAdminUserWizard.passwordInput()).setValue(password);
    }

    @Step("Confirmation password {} is entered")
    public void confirmationPasswordIsEntered(String confirmationPassword) {
        vTextField(adminUsersPageObj.addAdminUserWizard.confirmationPasswordInput()).setValue(confirmationPassword);
    }

    @Step("Wizard's Save button is clicked")
    public void clickWizardSave() {
        adminUsersPageObj.addAdminUserWizard.btnSave().click();
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
    }

    @Step("Wizard's Save button is clicked and error: {string} is displayed")
    @SuppressWarnings("checkstyle:MagicNumber")
    public void clickWizardSaveAndErrorIsShown(final String error) {
        adminUsersPageObj.addAdminUserWizard.btnSave().click();
        commonPageObj.alerts.alert(error).shouldBe(Condition.visible, ofSeconds(15));
    }

    @Step("Wizard's Cancel button is clicked")
    public void clickWizardCancel() {
        adminUsersPageObj.addAdminUserWizard.btnCancel().click();
    }

    @Step("Old password {} is entered")
    public void oldPasswordIsEntered(String password) {
        vTextField(adminUsersPageObj.oldPasswordInput()).setValue(password);
    }

    @Step("Old password input is not visible")
    public void oldPasswordInputIsNotVisible() {
        adminUsersPageObj.oldPasswordInput().shouldNotBe(Condition.visible);
    }

    @Step("New password {} is entered")
    public void newPasswordIsEntered(String password) {
        vTextField(adminUsersPageObj.newPasswordInput()).setValue(password);
    }

    @Step("New password's confirmation {} is entered")
    public void newPasswordConfirmationIsEntered(String confirmationPassword) {
        vTextField(adminUsersPageObj.newPasswordConfirmationInput()).setValue(confirmationPassword);
    }

    @Step("Change password dialog's Save button is clicked")
    public void saveChangePassword() {
        adminUsersPageObj.btnSavePasswordChange().click();
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
    }

    @Step("Change password dialog's Save button is clicked and error: {string} is displayed")
    @SuppressWarnings("checkstyle:MagicNumber")
    public void saveChangePasswordAndErrorIsShown(final String error) {
        adminUsersPageObj.btnSavePasswordChange().click();
        commonPageObj.alerts.alert(error).shouldBe(Condition.visible, ofSeconds(15));
    }

    @Step("Admin user {} is present in the list and has roles")
    public void adminUserIsPresentInListWithRoles(String username, DataTable table) {
        adminUsersPageObj.adminUserRow(username).shouldBe(Condition.visible);

        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        for (Map<String, String> columns : rows) {
            var role = columns.get("$role");

            WebElementCondition condition;
            if (SelenideValidation.PRESENT.name().equalsIgnoreCase(columns.get("$condition"))) {
                condition = Condition.partialText(role);
            } else {
                condition = Condition.not(Condition.partialText(role));
            }
            adminUsersPageObj.adminUserRoles(username).shouldHave(condition);
        }
    }

    @Step("Admin user {} is not present in the list")
    public void adminUserIsNotPresentInList(String username) {
        adminUsersPageObj.adminUserRow(username).shouldNotBe(Condition.visible);
    }

    @Step("Admin user {}'s edit dialog is opened")
    public void adminUserEditDialogOpened(String username) {
        adminUsersPageObj.btnEditAdminUser(username).shouldBe(Condition.visible).click();
    }

    @Step("Admin user {}'s password change dialog is opened")
    public void adminUserPasswordChangeDialogOpened(String username) {
        adminUsersPageObj.btnChangeAdminUserPassword(username).shouldBe(Condition.visible).click();
    }

    @Step("Admin user {} is deleted")
    public void adminUserDeleteButtonClicked(String username) {
        adminUsersPageObj.btnDeleteAdminUser(username).shouldBe(Condition.visible).click();
        commonPageObj.dialog.btnSave().click();
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
    }

    @Step("Role {string} is being checked")
    public void selectRoleInEditDialog(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        vCheckbox(adminUsersPageObj.checkboxRole(roleEnum)).click();
    }

    @Step("Role {string} should be visible in the wizard")
    public void roleShouldBeVisibleInWizard(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        adminUsersPageObj.addAdminUserWizard.checkboxRole(roleEnum).shouldBe(Condition.visible);
    }

    @Step("Role {string} should not be visible in the wizard")
    public void roleShouldNotBeVisibleInWizard(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        adminUsersPageObj.addAdminUserWizard.checkboxRole(roleEnum).shouldNotBe(Condition.visible);
    }

    @Step("Role {string} should be visible")
    public void roleShouldBeVisible(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        adminUsersPageObj.checkboxRole(roleEnum).shouldBe(Condition.visible);
    }

    @Step("Role {string} should not be visible")
    public void roleShouldNotBeVisible(String role) {
        String roleEnum = mapRoleTextToEnum(role);
        adminUsersPageObj.checkboxRole(roleEnum).shouldNotBe(Condition.visible);
    }

    private String mapRoleTextToEnum(String roleText) {
        return switch (roleText) {
            case "Security Officer" -> "XROAD_SECURITY_OFFICER";
            case "Registration Officer" -> "XROAD_REGISTRATION_OFFICER";
            case "Service Administrator" -> "XROAD_SERVICE_ADMINISTRATOR";
            case "System Administrator" -> "XROAD_SYSTEM_ADMINISTRATOR";
            case "Server Observer" -> "XROAD_SECURITYSERVER_OBSERVER";
            default -> throw new IllegalArgumentException("Role [" + roleText + "] is not supported");
        };
    }
}
