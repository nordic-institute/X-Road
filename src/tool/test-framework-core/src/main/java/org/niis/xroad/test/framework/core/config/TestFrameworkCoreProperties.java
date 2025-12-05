/*
 * The MIT License
 *
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
package org.niis.xroad.test.framework.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "test-framework")
public interface TestFrameworkCoreProperties {

    @WithDefault("build/")
    @WithName("working-dir")
    String workingDir();

    @WithDefault("build/resources/intTest/")
    @WithName("resource-dir")
    String resourceDir();

    @WithName("feign")
    Feign feign();

    @WithName("junit")
    Junit junit();

    @WithName("cucumber")
    Cucumber cucumber();

    @WithName("allure")
    Allure allure();

    @WithName("component-scan")
    ComponentScan componentScan();

    interface ComponentScan {
        @WithName("additional-packages")
        Optional<List<String>> additionalPackages();
    }

    interface Feign {
        @WithDefault("10s")
        @WithName("connect-timeout")
        Duration connectTimeout();

        @WithDefault("60s")
        @WithName("read-timeout")
        Duration readTimeout();
    }

    interface Junit {
        @WithDefault("false")
        @WithName("parallel-enabled")
        boolean parallelEnabled();

        @WithDefault("concurrent")
        @WithName("parallel-mode-default")
        String parallelModeDefault();

        @WithDefault("fixed")
        @WithName("parallel-config-strategy")
        String parallelConfigStrategy();

        @WithDefault("3")
        @WithName("parallel-config-fixed-parallelism")
        int parallelConfigFixedParallelism();

        @WithDefault("Modifying")
        @WithName("isolation-tag")
        Optional<String> isolationTag();
    }

    interface Cucumber {

        @WithName("glue")
        Optional<List<String>> glue();

        @WithDefault("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
        @WithName("plugin")
        String plugin();

        @WithDefault("not @Skip")
        @WithName("filter-tags")
        Optional<String> filterTags();

        @WithName("filter-name")
        Optional<String> filterName();

        @WithDefault("lexical")
        @WithName("execution-order")
        String executionOrder();

        @WithDefault("false")
        @WithName("execution-dry-run")
        boolean executionDryRun();

        @WithDefault("false")
        @WithName("ansi-colors-disabled")
        boolean ansiColorsDisabled();

        @WithDefault("false")
        @WithName("publish-enabled")
        boolean publishEnabled();

        @WithDefault("true")
        @WithName("publish-quiet")
        boolean publishQuiet();
    }

    @WithName("selenide")
    Selenide selenide();

    interface Selenide {
        @WithDefault("false")
        @WithName("enabled")
        boolean enabled();

        @WithDefault("15000")
        @WithName("timeout")
        long timeout();

        @WithDefault("70")
        @WithName("polling-interval")
        long pollingInterval();

        @WithDefault("true")
        @WithName("reopen-browser-on-fail")
        boolean reopenBrowserOnFail();

        @WithDefault("1920x1080")
        @WithName("browser-size")
        String browserSize();

        @WithDefault("normal")
        @WithName("page-load-strategy")
        String pageLoadStrategy();

        @WithDefault("20000")
        @WithName("page-load-timeout")
        long pageLoadTimeout();

        @WithDefault("true")
        @WithName("screenshots")
        boolean screenshots();

        @WithDefault("true")
        @WithName("save-page-source")
        boolean savePageSource();

        @WithDefault("false")
        @WithName("fast-set-value")
        boolean fastSetValue();

        @WithDefault("CSS")
        @WithName("selector-mode")
        String selectorMode();

        @WithDefault("STRICT")
        @WithName("assertion-mode")
        String assertionMode();

        @WithDefault("false")
        @WithName("webdriver-logs-enabled")
        boolean webdriverLogsEnabled();

        @WithDefault("false")
        @WithName("headless")
        boolean headless();

        @WithDefault("selenium/standalone-chromium:142.0")
        @WithName("remote-selenium-image")
        String remoteSeleniumImage();
    }

    interface Allure {
        @WithDefault("build/allure-results")
        @WithName("results-directory")
        String resultsDirectory();
    }
}
