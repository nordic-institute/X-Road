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
package org.niis.xroad.e2e.hook;

import com.nortal.test.core.services.hooks.AfterSuiteHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static java.nio.file.Files.move;
import static org.niis.xroad.e2e.container.ComposeLoggerFactory.CONTAINER_LOGS_DIR;

@Slf4j
@Component
public class LogPrepHook implements AfterSuiteHook {
    @Override
    public void afterSuite() {
        log.info("Moving container logs to final destination.");
        try {
            move(
                    Path.of(System.getProperty("testExecLogDir"), CONTAINER_LOGS_DIR),
                    Path.of(System.getProperty("testExecLogDir"), "allure-report", CONTAINER_LOGS_DIR)
            );
        } catch (Exception e) {
            log.error("Failed to move container logs to their final destination.", e);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:MagicNumber")
    public int afterSuitOrder() {
        // Exec after allure report is generated. We just want to attach it to final report.
        return 50100;
    }
}
