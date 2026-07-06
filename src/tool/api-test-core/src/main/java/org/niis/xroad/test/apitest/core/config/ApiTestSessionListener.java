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
package org.niis.xroad.test.apitest.core.config;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.niis.xroad.test.apitest.core.logging.LogbackAppenderFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * JUnit Platform {@link LauncherSessionListener} for the API tier. Initialises the config
 * singleton, registers the execution log appender, and configures Allure results directory. Spring-free —
 * no Cucumber or Feign initialisation.
 *
 * <p>Registered via SPI in {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}.
 */
@Slf4j
public class ApiTestSessionListener implements LauncherSessionListener {

    private static final String ALLURE_RESULTS_DIRECTORY_PROPERTY_NAME = "allure.results.directory";

    @Override
    public void launcherSessionOpened(@Nonnull LauncherSession session) {
        try {
            var configSource = ApiTestConfigSource.getInstance();
            var props = configSource.getCoreProperties();

            LogbackAppenderFactory.registerReportAppender(props.workingDir());

            System.setProperty(ALLURE_RESULTS_DIRECTORY_PROPERTY_NAME, props.allure().resultsDirectory());

            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();

            log.info("api-test-core configuration loaded (workingDir={}, allureResults={})",
                    props.workingDir(), props.allure().resultsDirectory());
        } catch (Exception e) {
            log.error("Failed to load api-test-core configuration", e);
            throw new IllegalStateException("api-test-core configuration loading failed", e);
        }
    }
}
