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
package org.niis.xroad.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;
import static org.openqa.selenium.By.xpath;

public class SecurityServerClientsStepDefs extends BaseUiStepDefs {
    private static final By BTN_ADD_CLIENT = xpath("//button[@data-test=\"add-client-button\"]");
    private static final String SELECT_CLIENT =
            "//div[.//a[contains(@class, \"v-tab--active\") "
                    + "and @data-test=\"clients\"]]//div[contains(@class, \"base-full-width\")]"
                    + "//tbody//span[contains(text(),\"%s\")]";
    private static final By TAB_DETAILS =
            xpath("//div[contains(@class, \"v-tabs-bar__content\")]//a[contains(@class, \"v-tab\") "
                    + "and contains(text(), \"Details\")]");

    @Given("Clients tab is selected")
    public void userNavigatesToClients() {
        $(By.xpath("//a[@data-test='clients']")).click();
    }

    @Given("Magnifying glass is clicked")
    public void clickMagnifyingGlass() {
        $(By.xpath("///*[contains(@class, \"mdi-magnify\")]")).click();
    }

    @Given("Client {string} is selected")
    public void selectClient(String clientName) {
        $(By.xpath(String.format(SELECT_CLIENT, clientName))).click();
    }

    @Then("Add client button is not visible")
    public void addClientIsNotVisible() {
        $(BTN_ADD_CLIENT).shouldNotBe(Condition.visible);
    }

    @Then("Client details tab is not visible")
    public void clientTabDetailsIsNotVisible() {
        $(TAB_DETAILS).shouldNotBe(Condition.visible);
    }

//
//    @When("User clicks on checkbox marked as {}")
//    public void clickOnCheckbox(String marking) {
//        elementXpath("//input[@data-test='" + marking + "']/following-sibling::div", true, 500L).click();
//    }
//

}
