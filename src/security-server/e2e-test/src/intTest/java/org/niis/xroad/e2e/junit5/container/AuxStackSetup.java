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
package org.niis.xroad.e2e.junit5.container;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

/**
 * The auxiliary stack: the Central Server plus the one-shot hurl setup container that seeds global
 * configuration and registers ss0/ss1 as members before any scenario runs.
 */
@Slf4j
public class AuxStackSetup extends BaseComposeSetup {

    private static final String COMPOSE_AUX_FILE = "compose.aux.yaml";
    private static final String CS = "cs";
    public static final String HURL = "hurl";

    public AuxStackSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "aux-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(), new File(coreProperties.resourceDir() + COMPOSE_AUX_FILE))
                .withExposedService(CS, Port.UI, forListeningPort())
                .withEnv("PROXY_UI_0", "ss0-ui")
                .withEnv("PROXY_0", "xrd-ss0")
                .withEnv("PROXY_UI_1", "ss1-ui")
                .withEnv("PROXY_1", "xrd-ss1")
                .withLogConsumer(HURL, createLogConsumer(HURL))
                .withLogConsumer(CS, createLogConsumer(CS))
                .waitingFor(CS, Wait.forLogMessage("^.*xroad-center entered RUNNING state.*$", 1));
    }

    /**
     * Blocks until the one-shot hurl setup container exits, then verifies it exited cleanly. This is
     * the JUnit5 equivalent of the legacy Cucumber "Environment is initialized" background step.
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public void waitForHurlToFinish() {
        await()
                .atMost(Duration.ofMinutes(20))
                .pollDelay(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(10))
                .until(() -> !isHurlRunning());

        verifyHurlSucceeded();
    }

    public boolean isHurlRunning() {
        return env.getContainerByServiceName(HURL).map(ContainerState::isRunning).orElse(false);
    }

    private void verifyHurlSucceeded() {
        var exitCode = env.getContainerByServiceName(HURL)
                .orElseThrow(() -> new IllegalStateException("hurl container not found"))
                .getCurrentContainerInfo().getState().getExitCodeLong();
        if (exitCode == null || exitCode != 0L) {
            throw new IllegalStateException(
                    "hurl setup container exited with code %s (expected 0); setup scenarios failed".formatted(exitCode));
        }
        log.info("hurl setup completed successfully");
    }

    /**
     * Exposed ports on the aux stack's containers.
     */
    public static final class Port {
        public static final int UI = 4000;

        private Port() {
        }
    }
}
