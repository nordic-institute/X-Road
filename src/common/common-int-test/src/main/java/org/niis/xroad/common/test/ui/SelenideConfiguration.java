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
package org.niis.xroad.common.test.ui;

import com.codeborne.selenide.logevents.SelenideLogger;
import io.qameta.allure.selenide.AllureSelenide;
import io.qameta.allure.selenide.LogType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Level;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SelenideProperties.class)
@ConditionalOnProperty(value = "test-automation.selenide.enabled", havingValue = "true")
public class SelenideConfiguration {
    private final SelenideProperties selenideProperties;

    @PostConstruct
    @SuppressWarnings("java:S2696")
    public void setUp() {
        System.setProperty("chromeoptions.args", selenideProperties.getChromeOptionsArgs());

        com.codeborne.selenide.Configuration.timeout = selenideProperties.getTimeout();
        com.codeborne.selenide.Configuration.pollingInterval = selenideProperties.getPollingInterval();
        com.codeborne.selenide.Configuration.reopenBrowserOnFail = selenideProperties.getReopenBrowserOnFail();
        com.codeborne.selenide.Configuration.browser = "chrome";

        com.codeborne.selenide.Configuration.remote = selenideProperties.getRemote();
        com.codeborne.selenide.Configuration.browserSize = selenideProperties.getBrowserSize();
        com.codeborne.selenide.Configuration.pageLoadStrategy = selenideProperties.getPageLoadStrategy();
        com.codeborne.selenide.Configuration.pageLoadTimeout = selenideProperties.getPageLoadTimeout();
        com.codeborne.selenide.Configuration.clickViaJs = selenideProperties.getClickViaJs();
        com.codeborne.selenide.Configuration.screenshots = selenideProperties.getScreenshots();
        com.codeborne.selenide.Configuration.savePageSource = selenideProperties.getSavePageSource();
        com.codeborne.selenide.Configuration.reportsFolder = selenideProperties.getReportsFolder();
        com.codeborne.selenide.Configuration.downloadsFolder = selenideProperties.getDownloadsFolder();
        com.codeborne.selenide.Configuration.fastSetValue = selenideProperties.getFastSetValue();
        com.codeborne.selenide.Configuration.selectorMode = selenideProperties.getSelectorMode();
        com.codeborne.selenide.Configuration.assertionMode = selenideProperties.getAssertionMode();
        com.codeborne.selenide.Configuration.fileDownload = selenideProperties.getFileDownload();
        com.codeborne.selenide.Configuration.proxyEnabled = selenideProperties.getProxyEnabled();
        com.codeborne.selenide.Configuration.proxyHost = selenideProperties.getProxyHost();
        com.codeborne.selenide.Configuration.proxyPort = selenideProperties.getProxyPort();
        com.codeborne.selenide.Configuration.webdriverLogsEnabled = selenideProperties.getWebdriverLogsEnabled();
        com.codeborne.selenide.Configuration.headless = selenideProperties.getHeadless();

        SelenideLogger.addListener("AllureSelenide",
                new AllureSelenide()
                        .screenshots(true)
                        .enableLogs(LogType.BROWSER, Level.ALL)
                        .savePageSource(true));
    }

}
