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
package org.niis.xroad.test.framework.core.ui;

import com.codeborne.selenide.AssertionMode;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelectorMode;
import com.codeborne.selenide.logevents.SelenideLogger;
import io.qameta.allure.selenide.AllureSelenide;
import io.qameta.allure.selenide.LogType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.logging.Level;

@Component
@RequiredArgsConstructor
public class SelenideConfiguration {
    private final TestFrameworkCoreProperties testFrameworkCoreProperties;

    @PostConstruct
    public void setUp() {
        var selenideProperties = testFrameworkCoreProperties.selenide();

        if (selenideProperties.enabled()) {
            setUpSelenide();
        }
    }

    @SuppressWarnings("java:S2696")
    private void setUpSelenide() {
        TestFrameworkCoreProperties.Selenide selenideProperties = testFrameworkCoreProperties.selenide();

        Configuration.browser = "chrome";
        Configuration.browserCapabilities = new ChromeOptions()
                .addArguments(
                        "--headless=new",
                        "--disable-features=OptimizationGuideModelDownloading,OptimizationHintsFetching",
                        "--disable-popup-blocking",
                        "--disable-infobars",
                        "--allow-running-insecure-content")
                .setEnableDownloads(true);
        Configuration.timeout = selenideProperties.timeout();
        Configuration.pollingInterval = selenideProperties.pollingInterval();
        Configuration.reopenBrowserOnFail = selenideProperties.reopenBrowserOnFail();
        Configuration.browserSize = selenideProperties.browserSize();
        Configuration.pageLoadStrategy = selenideProperties.pageLoadStrategy();
        Configuration.pageLoadTimeout = selenideProperties.pageLoadTimeout();
        Configuration.screenshots = selenideProperties.screenshots();
        Configuration.savePageSource = selenideProperties.savePageSource();
        Configuration.reportsFolder = testFrameworkCoreProperties.workingDir() + "/selenide-reports";
        Configuration.downloadsFolder = testFrameworkCoreProperties.workingDir() + "/selenide-downloads";
        Configuration.fastSetValue = selenideProperties.fastSetValue();
        Configuration.selectorMode = SelectorMode.valueOf(selenideProperties.selectorMode());
        Configuration.assertionMode = AssertionMode.valueOf(selenideProperties.assertionMode());
        Configuration.fileDownload = FileDownloadMode.CDP;
        Configuration.webdriverLogsEnabled = selenideProperties.webdriverLogsEnabled();
        Configuration.headless = selenideProperties.headless();

        SelenideLogger.addListener("AllureSelenide",
                new AllureSelenide()
                        .screenshots(true)
                        .enableLogs(LogType.BROWSER, Level.ALL)
                        .savePageSource(true));
    }
}
