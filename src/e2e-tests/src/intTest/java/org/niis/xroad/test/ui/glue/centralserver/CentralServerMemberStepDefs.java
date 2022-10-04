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
package org.niis.xroad.test.ui.glue.centralserver;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;
import org.niis.xroad.test.ui.glue.constants.Constants;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;
import static org.openqa.selenium.By.xpath;

public class CentralServerMemberStepDefs extends BaseUiStepDefs {
    private static final By TAB_MEMBERS = xpath("//div[contains(@class, \"v-tabs-bar__content\")]//*[contains(@class,"
            + "\"v-tab\") and contains(text(), \"Members\")]");
    private static final By BTN_ADD_MEMBER = xpath("//button[@data-test=\"add-member-button\"]");
    private static final By INPUT_MEMBER_NAME = xpath("//input[@data-test=\"add-member-name-input\"]");
    private static final By SELECT_MEMBER_CLASS = xpath("//input[@data-test=\"add-member-class-input\"]");
    private static final By INPUT_MEMBER_CODE = xpath("//input[@data-test=\"add-member-code-input\"]");

    @Given("Members tab is selected")
    public void userNavigatesToMembersTab() {
        $(TAB_MEMBERS).click();
    }

    @When("A new member with name: {}, code: {} & memberclass: {} is added")
    public void memberIsAdded(String memberName, String memberCode, String memberClass) {
        scenarioContext.putStepData("memberName", memberName);
        scenarioContext.putStepData("memberCode", memberCode);
        scenarioContext.putStepData("memberClass", memberClass);

        $(BTN_ADD_MEMBER).click();
        $(INPUT_MEMBER_NAME).setValue("E2E Test Member");
        $(SELECT_MEMBER_CLASS).click();
        getOption(memberClass).click();
        $(INPUT_MEMBER_CODE).setValue("e2e-test-member");
        $(Constants.BTN_DIALOG_SAVE).shouldBe(Condition.enabled).click();

        $(Constants.SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(Constants.BTN_CLOSE_SNACKBAR).click();
    }

    @Then("A new member is listed")
    public void newMemberIsListed() {
        String memberName = scenarioContext.getStepData("memberName");
        String memberCode = scenarioContext.getStepData("memberCode");
        String memberClass = scenarioContext.getStepData("memberClass");
        $(xpath("//div[@data-test=\"members-view\"]//table/tbody/tr[(normalize-space(td[1]/div/text()) = '" + memberName + "') "
                + " and (td[2] = '" + memberClass + "') and (td[3] = '" + memberCode + "')]")).shouldBe(Condition.visible);

    }

    private SelenideElement getOption(String option) {
        return $(xpath("//div[@role=\"listbox\"]//div[@role=\"option\" and contains(./descendant-or-self::*/text(),\""
                + option + "\")]"));
    }

}
