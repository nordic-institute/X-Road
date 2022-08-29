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

public class SecurityServerKeysStepDefs extends BaseUiStepDefs {
    private static final By TAB_SIGN_AND_AUTH_KEYS =
            xpath("//div[contains(@class, \"v-tabs-bar__content\")]//*[contains(@class, \"v-tab\")"
                    + " and contains(text(), \"SIGN and AUTH Keys\")]");
    private static final By TAB_API_KEYS =
            xpath("//div[contains(@class, \"v-tabs-bar__content\")]//*[contains(@class, \"v-tab\")"
                    + " and contains(text(), \"API Keys\")]");
    private static final By TAB_TLS_KEYS =
            xpath("//div[contains(@class, \"v-tabs-bar__content\")]//*[contains(@class, \"v-tab\")"
                    + " and contains(text(), \"Security Server TLS Key\")]");
    private static final By TOKEN_NAME = xpath("//*[@data-test=\"token-name\"]");
    private static final By BTN_GENERATE_KEY =
            xpath("//*[@data-test=\"security-server-tls-certificate-generate-key-button\"]");

    @Given("Keys and certificates tab is selected")
    public void userNavigatesToKeys() {
        $(By.xpath("//a[@data-test='keys']")).click();
    }

    @Given("SIGN and AUTH Keys tab is selected")
    public void clickSignAndAuthKeys() {
        $(TAB_SIGN_AND_AUTH_KEYS).click();
    }

    @Given("Security Server TLS Key tab is selected")
    public void clickTlsKeys() {
        $(TAB_TLS_KEYS).click();
    }

    @Then("Token name is visible")
    public void tokenNameIsVisible() {
        $(TOKEN_NAME).shouldBe(Condition.visible);
    }

    @Then("Generate key button is visible")
    public void generateKeyButtonIsVisible() {
        $(BTN_GENERATE_KEY).shouldBe(Condition.visible);
    }

    @Then("Tab api keys is not visible")
    public void apiKeysIsNotVisible() {
        $(TAB_API_KEYS).shouldNotBe(Condition.visible);
    }
}
