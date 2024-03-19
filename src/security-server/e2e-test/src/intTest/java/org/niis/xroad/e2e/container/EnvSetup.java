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
package org.niis.xroad.e2e.container;

import com.nortal.test.testcontainers.TestContainerNetworkProvider;
import com.nortal.test.testcontainers.TestContainerService;
import com.nortal.test.testcontainers.configuration.TestableContainerProperties;
import com.nortal.test.testcontainers.configurator.TestContainerConfigurator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.e2e.CustomProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.niis.xroad.e2e.container.Port.PROXY;
import static org.niis.xroad.e2e.container.Port.UI;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@Primary
@Service
@Slf4j
public class EnvSetup extends TestContainerService {
    private static final String COMPOSE_BASE_FILE = "../../../Docker/xrd-dev-stack/compose.yaml";
    private static final String COMPOSE_E2E_FILE = "../../../Docker/xrd-dev-stack/compose.e2e.yaml";

    public static final String CS = "cs";
    public static final String SS0 = "ss0";
    public static final String SS1 = "ss1";
    public static final String HURL = "hurl";

    private final CustomProperties customProperties;

    private ComposeContainer environment;

    public EnvSetup(@NotNull TestContainerNetworkProvider testContainerNetworkProvider,
                    @NotNull TestableContainerProperties testableContainerProperties,
                    @NotNull TestContainerConfigurator testContainerConfigurator,
                    @NotNull List<? extends TestContainerConfigurator.TestContainerInitListener> initListeners,
                    @NotNull CustomProperties customProperties) {
        super(testContainerNetworkProvider, testableContainerProperties, testContainerConfigurator, initListeners);
        this.customProperties = customProperties;
    }

    @Override
    public void initialize() {
        if (customProperties.isUseCustomEnv()) {
            log.warn("Using custom environment. Docker compose is not used.");
        } else {
            environment =
                    new ComposeContainer(new File(COMPOSE_BASE_FILE), new File(COMPOSE_E2E_FILE))
                            .withLocalCompose(true)

                            .withExposedService(CS, UI, forListeningPort())

                            .withExposedService(SS0, UI, forListeningPort())
                            .withExposedService(SS0, PROXY, forListeningPort())

                            .withExposedService(SS1, UI, forListeningPort())
                            .withExposedService(SS1, PROXY, forListeningPort())

                            .withEnv("CS_IMG", "xrd-centralserver-dev")
                            .withEnv("SS_IMG", "xrd-securityserver-dev")
                            .withEnv("CA_IMG", "xrd-testca")
                            .withEnv("IS_SOAP_IMG", "xrd-is-soap")

                            .waitingFor(CS, Wait.forLogMessage("^.*xroad-center entered RUNNING state.*$", 1));

            environment.start();

            waitForHurl();
        }
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void waitForHurl() {
        await()
                .atMost(Duration.ofMinutes(10))
                .pollDelay(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(10))
                .until(() -> {
                    log.info("Waiting for hurl to finish..");
                    return environment.getContainerByServiceName(HURL)
                            .map(container -> !container.isRunning())
                            .orElse(false);
                });
    }

    @PreDestroy
    public void stop() {
        if (!customProperties.isUseCustomEnv()) {
            environment.stop();
        }
    }

    @Override
    public int getPort() {
        return -1;
    }

    @NotNull
    @Override
    public String getHost() {
        return "localhost";
    }

    public Optional<ContainerState> getContainerByServiceName(String serviceName) {
        if (customProperties.isUseCustomEnv()) {
            return Optional.empty();
        }
        return environment.getContainerByServiceName(serviceName);
    }

    public ContainerMapping getContainerMapping(String serviceName, int originalPort) {
        if (customProperties.isUseCustomEnv()) {
            String key = serviceName + "_" + originalPort;
            var mappedValue = customProperties.getCustomEnvMapping().get(key);
            if (mappedValue == null) {
                throw new IllegalArgumentException("No mapping found for " + key);
            }
            var splittedStr = mappedValue.split("_");
            return new ContainerMapping(splittedStr[0], Integer.parseInt(splittedStr[1]));
        }

        return new ContainerMapping(
                environment.getServiceHost(serviceName, originalPort),
                environment.getServicePort(serviceName, originalPort)
        );
    }

    public record ContainerMapping(String host, int port) {
    }
}
