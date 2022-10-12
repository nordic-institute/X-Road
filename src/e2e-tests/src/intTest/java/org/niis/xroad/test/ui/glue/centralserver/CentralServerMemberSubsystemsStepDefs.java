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
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_CLOSE_SNACKBAR;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_CANCEL;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_DELETE;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_SAVE;
import static org.niis.xroad.test.ui.glue.constants.Constants.SNACKBAR_SUCCESS;
import static org.openqa.selenium.By.xpath;

public class CentralServerMemberSubsystemsStepDefs extends BaseUiStepDefs {
    private static final By TAB_SUBSYSTEMS = xpath("//a[@href=\"#/members/CS:E2E:e2e-test-member-subsystem/subsystems\"]");
    private static final String SUBSYSTEMS_TABLE = "//div[@data-test=\"subsystems-table\"]";
    private static final By BTN_ADD_SUBSYSTEM = xpath("//button[@data-test=\"add-subsystem\"]");
    private static final By INPUT_MEMBER_CODE = xpath("//input[@data-test=\"add-subsystem-input\"]");
    private static final String SUBSYSTEM_ROW =
            SUBSYSTEMS_TABLE + "//div//table//tbody//tr[td[contains(text(), \"%s\")] and td[contains(text(), \"%s\")]]";
    private static final By BTN_DELETE_SUBSYSTEM = xpath("//button[@data-test=\"delete-subsystem\"]");

    @When("Subsystems tab is selected")
    public void subsystemsTabIsSelected() {
        $(TAB_SUBSYSTEMS).click();
    }

    @Then("Subsystems table are correctly shown")
    public void subsystemsTableIsShown() {
        $(xpath(SUBSYSTEMS_TABLE)).shouldBe(Condition.enabled);
    }

    @When("A new subsystem with code: {} is added")
    public void subsystemIsAdded(String subsystemCode) {
        $(BTN_ADD_SUBSYSTEM).click();
        $(BTN_DIALOG_SAVE).shouldNotBe(Condition.enabled);
        $(BTN_DIALOG_CANCEL).shouldBe(Condition.enabled);

        $(INPUT_MEMBER_CODE).setValue(subsystemCode);
        $(BTN_DIALOG_SAVE).shouldBe(Condition.enabled).click();

        $(SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Then("Subsystem with code: {} and status: {} is listed")
    public void subsystemIsShown(String subsystemCode, String subsystemStatus) {
        $(xpath(String.format(SUBSYSTEM_ROW, subsystemCode, subsystemStatus))).shouldBe(Condition.visible);
    }

    @When("Subsystem is deleted")
    public void subsystemIsDeleted() {
        $(BTN_DELETE_SUBSYSTEM).click();

        $(BTN_DIALOG_CANCEL).shouldBe(Condition.enabled);
        $(BTN_DIALOG_DELETE).shouldBe(Condition.enabled).click();

        $(SNACKBAR_SUCCESS).shouldBe(Condition.visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Then("Subsystem with code: {} and status: {} not listed any more")
    public void subsystemIsNotShown(String subsystemCode, String subsystemStatus) {
        $(xpath(String.format(SUBSYSTEM_ROW, subsystemCode, subsystemStatus))).shouldNotBe(Condition.visible);
    }
}
