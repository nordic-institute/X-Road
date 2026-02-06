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

import io.cucumber.core.options.Constants;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.niis.xroad.test.framework.core.logging.LogbackAppenderFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static io.cucumber.core.options.Constants.ANSI_COLORS_DISABLED_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_DRY_RUN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_ORDER_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FILTER_NAME_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.EXECUTION_EXCLUSIVE_RESOURCES_READ_WRITE_TEMPLATE;
import static io.cucumber.junit.platform.engine.Constants.EXECUTION_EXCLUSIVE_RESOURCES_TAG_TEMPLATE_VARIABLE;
import static io.cucumber.junit.platform.engine.Constants.PARALLEL_CONFIG_FIXED_PARALLELISM_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME;
import static org.junit.jupiter.engine.Constants.PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME;

/**
 * JUnit Platform Launcher Session Listener that loads test framework configuration early during test execution initialization.
 * Registration: This listener is registered via SPI in
 * {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener}
 */
@Slf4j
public class TestConfigurationSessionListener implements LauncherSessionListener {
    private static final String ALLURE_RESULTS_DIRECTORY_PROPERTY_NAME = "allure.results.directory";

    @Override
    public void launcherSessionOpened(@Nonnull LauncherSession session) {
        try {
            // Initialize config source (singleton)
            TestFrameworkConfigSource configSource = TestFrameworkConfigSource.getInstance();
            TestFrameworkCoreProperties props = configSource.getCoreProperties();

            // Register report logger appender programmatically
            LogbackAppenderFactory.registerReportAppender(props.workingDir());

            // Load configuration for all components
            setJUnitProperties(props.junit());
            setCucumberProperties(props.cucumber());
            setAllureProperties(props.allure());

            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            log.info("Test framework configuration loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load test framework configuration", e);
            throw new IllegalStateException("Test framework configuration loading failed", e);
        }
    }

    private void setJUnitProperties(TestFrameworkCoreProperties.Junit junit) {
        log.debug("Configuring JUnit Platform properties");
        setProperty(PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, String.valueOf(junit.parallelEnabled()));
        setProperty(PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME, junit.parallelConfigStrategy());
        setProperty(PARALLEL_CONFIG_FIXED_PARALLELISM_PROPERTY_NAME,
                String.valueOf(junit.parallelConfigFixedParallelism()));

        junit.isolationTag().ifPresent(tag -> {
            String propertyKey = getExecutionGroupPropertyKey(tag);
            setProperty(propertyKey, "org.junit.platform.engine.support.hierarchical.ExclusiveResource.GLOBAL_KEY");
        });

        log.info("JUnit parallel execution: enabled={}, mode={}, strategy={}",
                junit.parallelEnabled(), junit.parallelModeDefault(), junit.parallelConfigStrategy());
    }

    private String getExecutionGroupPropertyKey(String tag) {
        return EXECUTION_EXCLUSIVE_RESOURCES_READ_WRITE_TEMPLATE.replace(
                EXECUTION_EXCLUSIVE_RESOURCES_TAG_TEMPLATE_VARIABLE,
                sanitizeTagName(tag));
    }

    private String sanitizeTagName(String tag) {
        return tag.replaceFirst("@", "");
    }

    private void setCucumberProperties(TestFrameworkCoreProperties.Cucumber cucumber) {
        log.debug("Configuring Cucumber properties");

        cucumber.glue().ifPresent(glueList -> {
            String glue = String.join(",", glueList);
            setProperty(GLUE_PROPERTY_NAME, glue);
        });
        setProperty(Constants.PLUGIN_PROPERTY_NAME, cucumber.plugin());
        cucumber.filterTags().ifPresent(tags -> setProperty(Constants.FILTER_TAGS_PROPERTY_NAME, tags));
        cucumber.filterName().ifPresent(name -> setProperty(FILTER_NAME_PROPERTY_NAME, name));
        setProperty(EXECUTION_ORDER_PROPERTY_NAME, cucumber.executionOrder());
        setProperty(EXECUTION_DRY_RUN_PROPERTY_NAME, String.valueOf(cucumber.executionDryRun()));
        setProperty(ANSI_COLORS_DISABLED_PROPERTY_NAME, String.valueOf(cucumber.ansiColorsDisabled()));
        setProperty(PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME, String.valueOf(cucumber.publishEnabled()));
        setProperty(PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, String.valueOf(cucumber.publishQuiet()));
        log.info("Cucumber configuration: plugin={},glue={}, filterTags={}, executionOrder={}",
                cucumber.plugin(), cucumber.glue(), cucumber.filterTags().orElse("none"), cucumber.executionOrder());
    }

    private void setAllureProperties(TestFrameworkCoreProperties.Allure allure) {
        log.debug("Configuring Allure properties");
        setProperty(ALLURE_RESULTS_DIRECTORY_PROPERTY_NAME, allure.resultsDirectory());
        log.info("Allure results directory: {}", allure.resultsDirectory());
    }

    private void setProperty(String key, String value) {
        System.setProperty(key, value);
        log.debug("Set property: {}={}", key, value);
    }
}
