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

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.nortal.test.core.report.TestReportService;
import com.nortal.test.core.services.CucumberScenarioProvider;
import com.nortal.test.core.services.ScenarioContext;
import org.niis.xroad.common.test.ui.utils.SeleniumUtils;
import org.niis.xroad.cs.test.ui.TargetHostUrlProvider;
import org.niis.xroad.cs.test.ui.configuration.TestProperties;
import org.niis.xroad.cs.test.ui.page.CommonPageObj;
import org.niis.xroad.cs.test.ui.utils.ChromiumDevTools;
import org.openqa.selenium.OutputType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Optional;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public abstract class BaseUiStepDefs {
    protected final CommonPageObj commonPageObj = new CommonPageObj();

    @Autowired
    protected TestProperties testProperties;
    @Autowired
    protected CucumberScenarioProvider scenarioProvider;
    @Autowired
    protected ScenarioContext scenarioContext;
    @Autowired
    protected TargetHostUrlProvider targetHostUrlProvider;
    @Autowired
    protected TestReportService testReportService;
    @Autowired
    protected ChromiumDevTools chromiumDevTools;

    /**
     * Vue.JS adds additional elements on top of input and simple clear just does not work.
     *
     * @param element element to clear
     */
    protected SelenideElement clearInput(SelenideElement element) {
        return SeleniumUtils.clearInput(element);
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

    /**
     * Put a value in scenario context. Value can be accessed through getStepData.
     *
     * @param key   value key. Non-null.
     * @param value value
     */
    protected void putStepData(StepDataKey key, Object value) {
        scenarioContext.putStepData(key.name(), value);
    }

    /**
     * Get value from scenario context.
     *
     * @param key value key
     * @return value from the context
     */
    protected <T> Optional<T> getStepData(StepDataKey key) {
        return Optional.ofNullable(scenarioContext.getStepData(key.name()));
    }

    /**
     * An enumerated key for data transfer between steps.
     */
    public enum StepDataKey {
        TOKEN_TYPE,
        MANAGEMENT_REQUEST_ID,
        DOWNLOADED_FILE,
    }
}
