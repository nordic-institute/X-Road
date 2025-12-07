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
package org.niis.xroad.test.framework.core.glue;

import io.cucumber.java.AfterAll;
import io.qameta.allure.Commands;
import io.qameta.allure.option.ConfigOptions;
import io.qameta.allure.option.ReportLanguageOptions;
import io.qameta.allure.option.ReportNameOptions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkConfigSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cucumber hook for generating Allure reports after test execution.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AllureReportHook {
    private static final String REPORT_DIR = "allure-report";

    @AfterAll
    public static void generateReport() {
        var coreProperties = TestFrameworkConfigSource.getInstance().getCoreProperties();

        if (!coreProperties.allure().generateReport()) {
            log.info("Allure report generation is disabled");
            return;
        }

        log.info("Generating Allure Report...");

        var reportDir = Path.of(coreProperties.workingDir() + REPORT_DIR);
        var resultsDir = Path.of(coreProperties.allure().resultsDirectory());

        if (!Files.exists(resultsDir)) {
            log.warn("Allure results directory does not exist: {}", resultsDir);
            return;
        }

        try {
            var allureHome = Files.createTempDirectory("allure-home");
            allureHome.toFile().deleteOnExit();

            var startTime = Instant.now();
            var exitCode = new Commands(allureHome).generate(
                    reportDir,
                    List.of(resultsDir),
                    true,
                    true,
                    new ConfigOptions(),
                    new ReportNameOptions(),
                    new ReportLanguageOptions()
            );
            var elapsed = Duration.between(startTime, Instant.now());

            if (exitCode.isSuccess()) {
                log.info("Allure report generated in {}ms at: {}", elapsed.toMillis(), reportDir.toAbsolutePath());
            } else {
                log.error("Allure report generation failed with exit code: {}", exitCode);
            }
        } catch (IOException e) {
            log.error("Failed to generate Allure report", e);
        }
    }
}
