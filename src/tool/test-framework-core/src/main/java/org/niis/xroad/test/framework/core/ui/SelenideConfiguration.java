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
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelectorMode;
import com.codeborne.selenide.logevents.SelenideLogger;
import io.qameta.allure.selenide.AllureSelenide;
import io.qameta.allure.selenide.LogType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Level;

@Configuration
@RequiredArgsConstructor
public class SelenideConfiguration {
    private final TestFrameworkCoreProperties testFrameworkCoreProperties;

    @PostConstruct
    public void setUp() {
        var selenideProperties = testFrameworkCoreProperties.selenide();

        if (selenideProperties.enabled()) {
            setUpSelenide(selenideProperties);
        }
    }

    @SuppressWarnings("java:S2696")
    private void setUpSelenide(TestFrameworkCoreProperties.Selenide selenideProperties) {
        System.setProperty("chromeoptions.args", selenideProperties.chromeOptionsArgs());

        com.codeborne.selenide.Configuration.timeout = selenideProperties.timeout();
        com.codeborne.selenide.Configuration.pollingInterval = selenideProperties.pollingInterval();
        com.codeborne.selenide.Configuration.reopenBrowserOnFail = selenideProperties.reopenBrowserOnFail();
        com.codeborne.selenide.Configuration.browser = "chrome";

        com.codeborne.selenide.Configuration.remote = selenideProperties.remote().orElse(null);
        com.codeborne.selenide.Configuration.browserSize = selenideProperties.browserSize();
        com.codeborne.selenide.Configuration.pageLoadStrategy = selenideProperties.pageLoadStrategy();
        com.codeborne.selenide.Configuration.pageLoadTimeout = selenideProperties.pageLoadTimeout();
        com.codeborne.selenide.Configuration.clickViaJs = selenideProperties.clickViaJs();
        com.codeborne.selenide.Configuration.screenshots = selenideProperties.screenshots();
        com.codeborne.selenide.Configuration.savePageSource = selenideProperties.savePageSource();
        com.codeborne.selenide.Configuration.reportsFolder = selenideProperties.reportsFolder();
        com.codeborne.selenide.Configuration.downloadsFolder = selenideProperties.downloadsFolder();
        com.codeborne.selenide.Configuration.fastSetValue = selenideProperties.fastSetValue();
        com.codeborne.selenide.Configuration.selectorMode = SelectorMode.valueOf(selenideProperties.selectorMode());
        com.codeborne.selenide.Configuration.assertionMode = AssertionMode.valueOf(selenideProperties.assertionMode());
        com.codeborne.selenide.Configuration.fileDownload = FileDownloadMode.valueOf(selenideProperties.fileDownload());
        com.codeborne.selenide.Configuration.proxyEnabled = selenideProperties.proxyEnabled();
        com.codeborne.selenide.Configuration.proxyHost = selenideProperties.proxyHost();
        com.codeborne.selenide.Configuration.proxyPort = selenideProperties.proxyPort();
        com.codeborne.selenide.Configuration.webdriverLogsEnabled = selenideProperties.webdriverLogsEnabled();
        com.codeborne.selenide.Configuration.headless = selenideProperties.headless();

        SelenideLogger.addListener("AllureSelenide",
                new AllureSelenide()
                        .screenshots(true)
                        .enableLogs(LogType.BROWSER, Level.ALL)
                        .savePageSource(true));
    }
}
