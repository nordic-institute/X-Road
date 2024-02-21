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
package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import org.niis.xroad.common.test.glue.BaseStepDefs;
import org.niis.xroad.ss.test.ui.TargetHostUrlProvider;
import org.niis.xroad.ss.test.ui.page.CommonPageObj;
import org.openqa.selenium.OutputType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static com.codeborne.selenide.Condition.empty;
import static org.openqa.selenium.Keys.COMMAND;
import static org.openqa.selenium.Keys.CONTROL;
import static org.openqa.selenium.Keys.DELETE;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public abstract class BaseUiStepDefs extends BaseStepDefs {
    protected final CommonPageObj commonPageObj = new CommonPageObj();

    @Autowired
    protected TargetHostUrlProvider targetHostUrlProvider;

    /**
     * Vue.JS adds additional elements on top of input and simple clear just does not work.
     *
     * @param element element to clear
     */
    protected SelenideElement clearInput(SelenideElement element) {
        element.clear();
        element.sendKeys(isMacOsBrowser() ? COMMAND : CONTROL, "a");
        element.sendKeys(DELETE);

        element.shouldBe(empty);
        return element;
    }

    /**
     * Takes a screenshot and adds it to report.
     *
     * @param screenshotName filename that will be visible in report
     */
    protected void takeScreenshot(String screenshotName) {
        var scr = Selenide.screenshot(OutputType.BYTES);
        scenarioProvider.getCucumberScenario().attach(scr, MediaType.IMAGE_PNG_VALUE, screenshotName);
    }


    private boolean isMacOsBrowser() {
        return Selenide.webdriver().driver().getUserAgent().toUpperCase().contains("MAC OS");
    }

}
