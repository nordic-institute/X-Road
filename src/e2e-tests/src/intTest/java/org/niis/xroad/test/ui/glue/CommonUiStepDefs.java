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
import com.codeborne.selenide.Selenide;
import io.cucumber.java.en.Given;
import org.openqa.selenium.By;

import java.time.Duration;

import static com.codeborne.selenide.Selenide.$;

public class CommonUiStepDefs extends BaseUiStepDefs {
    public static final int MAX_WAIT_IN_SECONDS = 120;

    @Given("logout button is being clicked")
    public void logoutButtonIsClicked() {
        $(By.xpath("//button[@data-test='username-button']"))
                .shouldBe(Condition.visible)
                .shouldBe(Condition.enabled)
                .click();
    }

    @Given("User becomes idle")
    public void userBecomesIdle() {
        Selenide.sleep(1);
    }


    @Given("after 120 seconds, session timeout popup appears")
    public void errorMessageAboutTimeoutAppears() {
        $(By.xpath("//button[@data-test='session-expired-ok-button']"))
                .shouldBe(Condition.visible, Duration.ofSeconds(MAX_WAIT_IN_SECONDS));

    }

    @Given("OK is clicked on timeout notification popup")
    public void okIsClickedOnTimeoutNotificationPopup() {
        $(By.xpath("//button[@data-test='session-expired-ok-button']"))
                .shouldBe(Condition.visible)
                .shouldBe(Condition.enabled)
                .click();
    }

    @Given("Page is prepared to be tested")
    public void preparePage() {
        Selenide.executeJavaScript("window.e2eTestingMode = true;\n"
                + "      const style = `\n"
                + "      <style>\n"
                + "        *, ::before, ::after {\n"
                + "            transition:none !important;\n"
                + "        }\n"
                + "      </style>`;\n"
                + "      document.head.insertAdjacentHTML('beforeend', style);");
    }

}
