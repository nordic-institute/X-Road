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
import io.cucumber.java.en.Then;
import org.niis.xroad.cs.test.ui.page.MemberPageObj;

public class MemberStepDefs extends BaseUiStepDefs {
    private final MemberPageObj memberPageObj = new MemberPageObj();

    @Then("Member {} is selected")
    public void memberIsSelected(String memberName) {
        memberPageObj.listRowOf(memberName).click();
    }

    @Step("A new member with name: {}, code: {} & member class: {} is added")
    public void memberIsAdded(String memberName, String memberCode, String memberClass) {
        memberPageObj.btnAddMember().click();

        memberPageObj.addDialog().inputMemberCode().setValue(memberCode);
        memberPageObj.addDialog().inputMemberName().setValue(memberName);

        memberPageObj.addDialog().selectMemberClass().click();
        memberPageObj.addDialog().selectMemberClassOption(memberClass).click();

        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("A member with name: {}, code: {} & member class: {} is listed")
    public void newMemberIsListed(String memberName, String memberCode, String memberClass) {
        memberPageObj.listRowOf(memberName, memberCode, memberClass).shouldBe(Condition.visible);
    }

}
