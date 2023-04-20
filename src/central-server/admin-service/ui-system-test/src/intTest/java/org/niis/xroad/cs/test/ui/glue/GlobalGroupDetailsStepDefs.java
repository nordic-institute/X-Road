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
package org.niis.xroad.cs.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.GlobalGroupDetailsPageObj;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;

public class GlobalGroupDetailsStepDefs extends BaseUiStepDefs {

    private final GlobalGroupDetailsPageObj globalGroupDetailsPage = new GlobalGroupDetailsPageObj();

    @Step("user opens add members dialog")
    public void openAddMembersDialog() {
        globalGroupDetailsPage.btnAddMembersButton()
                .shouldBe(enabled, visible)
                .click();

        globalGroupDetailsPage.getAddMembersDialogObj().btnAddMembers()
                .shouldNotBe(enabled);
    }

    @Step("group members list contains:")
    public void membersListContains(final DataTable identifiers) {
        identifiers.asList().forEach(this::membersListContains);
    }

    @Step("group has {int} member")
    @Step("group has {int} members")
    public void membersListContains(final int count) {
        globalGroupDetailsPage.membersCount()
                .shouldHave(text(Integer.toString(count)));
    }

    public void membersListContains(final String identifier) {
        globalGroupDetailsPage.memberRow(identifier)
                .shouldBe(visible);
    }

    @Step("user selects members:")
    public void selectMember(final DataTable identifiers) {
        identifiers.asList().forEach(this::selectMember);
    }

    public void selectMember(final String identifier) {
        globalGroupDetailsPage.getAddMembersDialogObj()
                .rowCheckbox(identifier)
                .shouldBe(enabled, visible)
                .click();
    }

    @Step("user adds selected members")
    public void addSelectedMembers() {
        globalGroupDetailsPage.getAddMembersDialogObj().btnAddMembers()
                .shouldBe(enabled)
                .click();
    }

    @Step("user closes add members dialog")
    public void closeAddMembersDialog() {
        globalGroupDetailsPage.getAddMembersDialogObj().btnClose()
                .shouldBe(enabled)
                .click();
    }

    @Step("selected members are successfully added")
    public void memberAddedSuccessfully() {
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }


    @Step("user gets error ")
    public void failedToAddMembers() {
        commonPageObj.snackBar.success().shouldNotBe(Condition.visible);
    }

    @Step("user can't select members:")
    public void cantSelectMembers(final DataTable identifiers) {
        identifiers.asList()
                .forEach(identifier -> globalGroupDetailsPage.getAddMembersDialogObj().selectableRow(identifier)
                        .shouldNotBe(visible));
    }

    @Step("user can select members:")
    public void canSelectMembers(final DataTable identifiers) {
        identifiers.asList()
                .forEach(identifier -> globalGroupDetailsPage.getAddMembersDialogObj().selectableRow(identifier)
                        .shouldBe(visible));
    }

    @Step("selected members are:")
    public void membersAreSelected(final DataTable identifiers) {
        identifiers.asList()
                .forEach(identifier -> globalGroupDetailsPage.getAddMembersDialogObj().rowCheckbox(identifier)
                        .$("i")
                        .shouldHave(cssClass("mdi-checkbox-marked")));
    }

    @Step("user filters selectable members list with query: {string}")
    public void filterCandidates(final String query) {
        clearInput(globalGroupDetailsPage.getAddMembersDialogObj().inputFilter())
                .shouldBe(visible, enabled)
                .setValue(query);
    }

    @Step("user deletes selectable members filter query")
    public void clearCandidatesFilter() {
        clearInput(globalGroupDetailsPage.getAddMembersDialogObj().inputFilter());
    }

    @Step("user opens delete member dialog for {string}")
    public void openMemberDeleteDialog(final String identifier) {
        globalGroupDetailsPage.btnDeleteMember(identifier)
                .shouldBe(visible, enabled)
                .click();
    }

    @Step("user can't press delete button")
    public void deleteMemberButtonDisabled() {
        globalGroupDetailsPage.getDeleteMemberDialogObj().btnDelete()
                .shouldBe(visible)
                .shouldNotBe(enabled);
    }

    @Step("user deletes group member")
    public void deletesGroupMember() {
        globalGroupDetailsPage.getDeleteMemberDialogObj().btnDelete()
                .shouldBe(visible, enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("user enters member code: {string}")
    public void enterMemberCode(final String code) {
        clearInput(globalGroupDetailsPage.getDeleteMemberDialogObj().inputCode())
                .shouldBe(visible, enabled)
                .setValue(code);
    }
}
