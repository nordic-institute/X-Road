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

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.Selenide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.niis.xroad.test.framework.core.context.ScenarioExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelenideManager {
    private static final String TAG_DOWNLOAD = "@Download";

    private final ScenarioExecutionContext scenarioExecutionContext;

    public void open(String baseUrl) {
        var stopWatch = StopWatch.createStarted();

        if (Selenide.webdriver().driver().hasWebDriverStarted()) {
            try {
                Selenide.clearBrowserCookies();
                Selenide.clearBrowserLocalStorage();
                Selenide.sessionStorage().clear();
            } catch (Exception e) {
                log.warn("Failed to clear browser cookies and storage", e);
            }
            log.debug("Clearing Selenide browser cookies and storage took {} ms", stopWatch.getTime());
        }

        if (isDownloadScenario()) {
            var config = Configuration.config();
            config.proxyEnabled(true);
            config.fileDownload(FileDownloadMode.PROXY);
            Selenide.open(baseUrl, config);
        } else {
            Selenide.open(baseUrl);
        }

        log.info("Opening {} page took {} ms", baseUrl, stopWatch.getTime());
    }

    private boolean isDownloadScenario() {
        return scenarioExecutionContext.getCucumberScenario().getSourceTagNames()
                .stream()
                .anyMatch(TAG_DOWNLOAD::equalsIgnoreCase);
    }
}
