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
package org.niis.xroad.e2e.glue;

import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.e2e.CustomProperties;
import org.niis.xroad.e2e.container.EnvSetup;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.ContainerState;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.niis.xroad.e2e.container.EnvSetup.HURL;

@Slf4j
@SuppressWarnings(value = {"SpringJavaInjectionPointsAutowiringInspection"})
public class CsInitStepDefs extends BaseE2EStepDefs {
    @Autowired
    private EnvSetup envSetup;
    @Autowired
    private CustomProperties customProperties;

    @SuppressWarnings("squid:S5960")
    @Step("Environment is initialized")
    public void csIsInitialized() {
        if (customProperties.isUseCustomEnv()) {
            testReportService.attachText("Using custom environment. Docker compose is not used.", "Step was ignored.");
            return;
        }

        var hulRunning = envSetup.getContainerByServiceName(HURL)
                .map(ContainerState::isRunning)
                .orElse(true);

        assertFalse(hulRunning);
    }
}
