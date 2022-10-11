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
import io.cucumber.java.en.Then;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_CLOSE_SNACKBAR;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_CANCEL;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_DELETE;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_SAVE;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_INFO_CARD_EDIT;
import static org.niis.xroad.test.ui.glue.constants.Constants.SNACKBAR_SUCCESS;
import static org.openqa.selenium.By.xpath;

public class CentralServerMemberDetailsStepDefs extends BaseUiStepDefs {
    private static final String MEMBER_NAME_CARD =
            "//div[@data-test=\"member-name-card\"]//div[2]//div[contains(text(), \"%s\")]";
    private static final String INPUT_EDIT_MEMBER_NAME = "//input[@data-test=\"edit-member-name\"]";
    private static final String DELETE_MEMBER = "//div[@data-test=\"delete-member\"]";
    private static final String INPUT_MEMBER_CODE = "//input[@data-test=\"member-code\"]";
    private static final String MEMBER_CLASS_CARD =
            "//div[@data-test=\"member-class-card\"]//div[2]//div[contains(text(), \"%s\")]";
    private static final String MEMBER_CODE_CARD =
            "//div[@data-test=\"member-code-card\"]//div[2]//div[contains(text(), \"%s\")]";
    private static final String TABLE_TITLE = "//div[@class=\"xrd-view-title\" and contains(text(), \"%s\")]";
    private static final String OWNED_SERVERS_SEARCH = "//div[@data-test=\"search-owned-servers\"]";
    private static final String GLOBAL_GROUPS_SEARCH = "//div[@data-test=\"search-global-groups\"]";
    private static final String OWNED_SERVERS_TABLE = "//div[@data-test=\"owned-servers-table\"]";
    private static final String GLOBAL_GROUPS_TABLE = "//div[@data-test=\"global-groups-table\"]";

    @Then("Member {} is selected")
    public void memberIsSelected(String memberName) {
        getMemberElement(memberName).click();
    }

    @Then("The member name: {}, code: {} and class: {} are correctly shown")
    public void memberNameAndCodeAndClassAreShown(String memberName, String memberCode, String memberClass) {
        $(xpath(String.format(MEMBER_NAME_CARD, memberName))).shouldBe(Condition.enabled);
        $(xpath(String.format(MEMBER_CLASS_CARD, memberClass))).shouldBe(Condition.enabled);
        $(xpath(String.format(MEMBER_CODE_CARD, memberCode))).shouldBe(Condition.enabled);
    }

    @Then("The Owned Servers table is correctly shown")
    public void ownedSecurityServersTableIsShown() {
        $(By.xpath(String.format(TABLE_TITLE, "Owned Servers"))).shouldBe(Condition.enabled);
        $(By.xpath(OWNED_SERVERS_SEARCH)).shouldBe(Condition.enabled);
        $(By.xpath(OWNED_SERVERS_TABLE)).shouldBe(Condition.enabled);
    }

    @Then("The Global Groups table is correctly shown")
    public void globalGroupsTableIsShown() {
        $(By.xpath(String.format(TABLE_TITLE, "Global Groups"))).shouldBe(Condition.enabled);
        $(By.xpath(GLOBAL_GROUPS_SEARCH)).shouldBe(Condition.enabled);
        $(By.xpath(GLOBAL_GROUPS_TABLE)).shouldBe(Condition.enabled);
    }

    @Then("The name of the member is able to changed")
    public void memberNameIsChanged() {
        $(BTN_INFO_CARD_EDIT).click();
        $(BTN_DIALOG_CANCEL).shouldBe(Condition.enabled);
        $(BTN_DIALOG_SAVE).shouldNotBe(Condition.enabled);

        $(xpath(INPUT_EDIT_MEMBER_NAME)).setValue(" Other");
        $(BTN_DIALOG_SAVE).shouldBe(Condition.enabled).click();

        $(SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Then("Deleting the member requires the user to input the member code: {}")
    public void deleteMember(String memberCode) {
        $(xpath(DELETE_MEMBER)).click();
        $(BTN_DIALOG_CANCEL).shouldBe(Condition.enabled);
        $(BTN_DIALOG_DELETE).shouldNotBe(Condition.enabled);

        $(xpath(INPUT_MEMBER_CODE)).setValue(memberCode);
        $(BTN_DIALOG_DELETE).shouldBe(Condition.enabled).click();

        $(SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    private SelenideElement getMemberElement(String memberName) {
        String selector =
                String.format("//div[@data-test=\"members-table\"]//table//tbody//tr//td//div[contains(text(), \"%s\")]",
                        memberName);
        return $(xpath(selector));
    }
}
