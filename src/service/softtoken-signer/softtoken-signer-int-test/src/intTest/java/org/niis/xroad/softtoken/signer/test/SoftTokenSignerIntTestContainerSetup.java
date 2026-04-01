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

package org.niis.xroad.softtoken.signer.test;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.container.BaseComposeSetup;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Slf4j
@Service
public class SoftTokenSignerIntTestContainerSetup extends BaseComposeSetup {
    private static final int SIGNER_GRPC_PORT = Integer.parseInt(SignerRpcChannelProperties.DEFAULT_PORT);
    private static final Duration SIGNER_STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration SOFTTOKEN_SIGNER_STARTUP_TIMEOUT = Duration.ofSeconds(45);

    public static final String SIGNER = "signer";
    public static final String SOFTTOKEN_SIGNER = "softtoken-signer";
    public static final String DB_SERVERCONF = "db-serverconf";

    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    public SoftTokenSignerIntTestContainerSetup(TestFrameworkCoreProperties coreProperties) {
        super(coreProperties);
    }

    /**
     * Get the Docker Compose environment.
     * @return the compose container environment
     */
    public ComposeContainer getEnv() {
        return env;
    }

    @Override
    public ComposeContainer initEnv() {
        return new ComposeContainer("softtoken-signer-", new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withExposedService(SIGNER, SIGNER_GRPC_PORT, Wait.forHealthcheck().withStartupTimeout(SIGNER_STARTUP_TIMEOUT))
                .withExposedService(SOFTTOKEN_SIGNER, Port.SOFTTOKEN_SIGNER_GRPC,
                        Wait.forHealthcheck().withStartupTimeout(SOFTTOKEN_SIGNER_STARTUP_TIMEOUT))
                .withExposedService(SOFTTOKEN_SIGNER, Port.HEALTH_PORT)
                .withExposedService(DB_SERVERCONF, Port.DB, Wait.forListeningPort())
                .withLogConsumer(SOFTTOKEN_SIGNER, createLogConsumer(SOFTTOKEN_SIGNER));
    }

    /**
     * Stop a container by service name.
     * @param service the Docker Compose service name
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public void stopContainer(String service) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();
        var dockerClient = containerState.getDockerClient();
        dockerClient.stopContainerCmd(containerState.getContainerId()).exec();
        await().atMost(30, TimeUnit.SECONDS).until(() -> !containerState.isRunning());
    }

    /**
     * Start a container by service name.
     * @param service the Docker Compose service name
     * @param waitForHealthy whether to wait for the container health check to pass
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public void startContainer(String service, boolean waitForHealthy) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();
        var dockerClient = containerState.getDockerClient();
        dockerClient.startContainerCmd(containerState.getContainerId()).exec();
        if (waitForHealthy) {
            await().atMost(45, TimeUnit.SECONDS).until(containerState::isHealthy);
        }
    }

    /**
     * Restart a container by service name and wait for it to become healthy.
     * @param service the Docker Compose service name
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public void restartContainer(String service) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();
        var dockerClient = containerState.getDockerClient();
        dockerClient.restartContainerCmd(containerState.getContainerId()).exec();
        await().atMost(45, TimeUnit.SECONDS).until(containerState::isHealthy);
    }

    /**
     * Port constants for containerized services.
     */
    @UtilityClass
    public final class Port {
        public static final int DB = 5432;
        public static final int SOFTTOKEN_SIGNER_GRPC = 5561;
        public static final int HEALTH_PORT = 4099;
    }
}
