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

public class SecurityServerSettingsStepDefs extends BaseUiStepDefs {
    private static final By TAB_SYSTEM_PARAMETERS = xpath("//a[@data-test=\"system-parameters-tab-button\"]");
    private static final By BTN_ANCHOR_DOWNLOAD =
            xpath("//*[@data-test=\"system-parameters-configuration-anchor-download-button\"]");

    @Given("SecurityServer Settings tab is selected")
    public void userNavigatesToSettings() {
        $(By.xpath("//a[@data-test='settings']")).click();
    }

    @Given("System parameters tab is selected")
    public void selectSystemParametersTab() {
        $(TAB_SYSTEM_PARAMETERS).click();
    }

    @Then("Anchor download button is visible")
    public void anchorDownloadButtonIsVisible() {
        $(BTN_ANCHOR_DOWNLOAD).shouldBe(Condition.visible);
    }
}
