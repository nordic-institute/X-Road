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

package org.niis.xroad.cs.test.ui;

import lombok.experimental.UtilityClass;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.container.BaseComposeSetup;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@Component
public class CsSystemTestContainerSetup extends BaseComposeSetup {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    public static final String CS = "cs";

    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    public CsSystemTestContainerSetup(TestFrameworkCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    public ComposeContainer initEnv() {
        return new ComposeContainer("cs-", new File(coreProperties.resourceDir() + "/" + COMPOSE_FILE))
                .withExposedService(CS, Port.UI, Wait.forHealthcheck().withStartupTimeout(STARTUP_TIMEOUT))
                .withExposedService(BROWSER, PORT_CHROMEDRIVER, forListeningPort())
                .withLogConsumer(CS, createLogConsumer(CS));
    }

    @Override
    protected void onPostStart() {
        initSelenideRemoteWebDriver();
    }

    @Override
    public void destroy() {
        // copy log files from CS container
        copyXRoadLogsFromContainer(CS, "cs");

        super.destroy();
    }

    @UtilityClass
    public final class Port {
        public static final int UI = 4000;
    }
}
