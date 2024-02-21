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
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.MemberDetailsPageObj;

import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class MemberDetailsStepDefs extends BaseUiStepDefs {
    private final MemberDetailsPageObj memberDetailsPageObj = new MemberDetailsPageObj();

    @Step("The member name: {}, code: {} and class: {} are correctly shown")
    public void memberNameAndCodeAndClassAreShown(String memberName, String memberCode, String memberClass) {
        memberDetailsPageObj.memberNameCard(memberName).shouldBe(visible);
        memberDetailsPageObj.memberClassCard(memberClass).shouldBe(visible);
        memberDetailsPageObj.memberCodeCard(memberCode).shouldBe(visible);
    }

    @Step("The Owned Servers table is correctly shown")
    public void ownedSecurityServersTableIsShown() {
        memberDetailsPageObj.tableTitle("Owned Servers").shouldBe(visible);
        memberDetailsPageObj.ownerServersSearch().shouldBe(visible);
        memberDetailsPageObj.ownedServers().table().shouldBe(visible);
    }

    @Step("The Global Groups table is correctly shown")
    public void globalGroupsTableIsShown() {
        memberDetailsPageObj.tableTitle("Global Groups").shouldBe(visible);
        memberDetailsPageObj.globalGroupsSearch().shouldBe(visible);
        memberDetailsPageObj.globalGroupsTable().shouldBe(visible);
    }

    @Step("The name of the member is changed to {}")
    public void memberNameIsChanged(final String newName) {
        memberDetailsPageObj.btnEdit().click();
        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldNotBe(Condition.enabled);

        vTextField(memberDetailsPageObj.editNameDialog().inputMemberName())
                .clear()
                .setValue(newName);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Deleting the member requires the user to input the member code: {}")
    public void deleteMember(String memberCode) {
        memberDetailsPageObj.btnDelete().click();
        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnDelete().shouldNotBe(Condition.enabled);

        vTextField(memberDetailsPageObj.deleteDialog().inputMemberCode())
                .setValue(memberCode + "-invalid");
        commonPageObj.dialog.btnDelete().shouldNotBe(Condition.enabled);

        vTextField(memberDetailsPageObj.deleteDialog().inputMemberCode())
                .clear()
                .setValue(memberCode);
        commonPageObj.dialog.btnDelete().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("owned servers contains server {string}")
    public void ownedServersContainsServer(String serverCode) {
        memberDetailsPageObj.ownedServers().server(serverCode).shouldBe(visible);
    }

    @Step("user clicks on owned server {string}")
    public void userClicksOnOwnedServer(String serverCode) {
        memberDetailsPageObj.ownedServers().server(serverCode).click();
    }

}
