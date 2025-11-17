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

import com.nortal.test.testcontainers.TestableContainerInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.test.logging.ComposeLoggerFactory;
import org.niis.xroad.e2e.CustomProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@Primary
@Service
@Slf4j
@RequiredArgsConstructor
public class EnvSetup implements TestableContainerInitializer, DisposableBean {

    private static final String COMPOSE_AUX_FILE = "build/resources/intTest/compose.aux.yaml";
    private static final String COMPOSE_SS_FILE = "build/resources/intTest/compose.main.yaml";
    private static final String COMPOSE_SS_E2E_FILE = "build/resources/intTest/compose.e2e.yaml";
    private static final String COMPOSE_SS_HSM_FILE = "build/resources/intTest/compose.ss-hsm.e2e.yaml";

    private static final String CS = "cs";
    private static final String OPENBAO = "openbao";
    private static final String PROXY = "proxy";
    private static final String UI = "ui";
    private static final String SIGNER = "signer";
    private static final String CONFIGURATION_CLIENT = "configuration-client";
    private static final String XROAD_NETWORK = "xroad-network";

    public static final String DB_MESSAGELOG = "db-messagelog";
    public static final String HURL = "hurl";

    private final CustomProperties customProperties;

    private ComposeContainer envSs0;
    private ComposeContainer envSs1;
    private ComposeContainer envAux;

    @Override
    public void initialize() {
        if (customProperties.isUseCustomEnv()) {
            log.warn("Using custom environment. Docker compose is not used.");
        } else {
            envSs0 = createSSEnvironment("ss0", false);

            envSs1 = createSSEnvironment("ss1", true);

            envAux = new ComposeContainer("aux-", new File(COMPOSE_AUX_FILE))
                    .withLocalCompose(true)
                    .withExposedService(CS, Port.UI, forListeningPort())
                    .withEnv("CA_IMG", customProperties.getCaImage())
                    .withEnv("IS_OPENAPI_IMG", customProperties.getIsopenapiImage())
                    .withEnv("IS_SOAP_IMG", customProperties.getIssoapImage())
                    .withEnv("PROXY_UI_0", getContainerName(envSs0, UI))
                    .withEnv("PROXY_0", getContainerName(envSs0, PROXY))
                    .withEnv("PROXY_UI_1", getContainerName(envSs1, UI))
                    .withEnv("PROXY_1", getContainerName(envSs1, PROXY))
                    .withLogConsumer(HURL, createLogConsumer("aux", HURL))
                    .withLogConsumer(CS, createLogConsumer("aux", CS))
                    .waitingFor(CS, Wait.forLogMessage("^.*xroad-center entered RUNNING state.*$", 1));
            envAux.start();

            waitForHurl();
        }
    }

    private void connectToExternalNetwork(ComposeContainer env, String... serviceNames) {
        for (String serviceName : serviceNames) {
            var containerState = env.getContainerByServiceName(serviceName).orElseThrow();
            var dockerClient = containerState.getDockerClient();

            String networkId = dockerClient.listNetworksCmd().exec().stream()
                    .filter(n -> XROAD_NETWORK.equals(n.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find external network '%s'".formatted(XROAD_NETWORK)))
                    .getId();

            dockerClient.connectToNetworkCmd()
                    .withContainerId(containerState.getContainerId())
                    .withNetworkId(networkId)
                    .exec();
        }
    }

    private ComposeContainer createSSEnvironment(String name, boolean enableHsm) {
        var files = enableHsm
                ? new File[]{new File(COMPOSE_SS_FILE), new File(COMPOSE_SS_E2E_FILE), new File(COMPOSE_SS_HSM_FILE)}
                : new File[]{new File(COMPOSE_SS_FILE), new File(COMPOSE_SS_E2E_FILE)};

        var env = new ComposeContainer(name + "-", files)
                .withLocalCompose(true)
                .withExposedService(PROXY, Port.PROXY, forListeningPort())
                .withExposedService(UI, Port.UI, forListeningPort())
                .withExposedService(DB_MESSAGELOG, Port.DB, forListeningPort())
                .withLogConsumer(UI, createLogConsumer(name, UI))
                .withLogConsumer(PROXY, createLogConsumer(name, PROXY))
                .withLogConsumer(CONFIGURATION_CLIENT, createLogConsumer(name, CONFIGURATION_CLIENT))
                .withLogConsumer(SIGNER, createLogConsumer(name, SIGNER))
                .withLogConsumer(OPENBAO, createLogConsumer(name, OPENBAO));

        env.start();
        connectToExternalNetwork(env, UI, PROXY, CONFIGURATION_CLIENT, SIGNER);

        return env;
    }

    private String getContainerName(ComposeContainer env, String container) {
        return env.getContainerByServiceName(container)
                .map(c -> c.getContainerInfo().getName().substring(1)).orElseThrow();
    }

    private Slf4jLogConsumer createLogConsumer(String env, String containerName) {
        return new Slf4jLogConsumer(new ComposeLoggerFactory().create("%s-%s".formatted(env, containerName)));
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void waitForHurl() {
        await()
                .atMost(Duration.ofMinutes(20))
                .pollDelay(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(10))
                .until(() -> {
                    log.info("Waiting for hurl to finish..");
                    return envAux.getContainerByServiceName(HURL)
                            .map(container -> !container.isRunning())
                            .orElse(false);
                });

        var gracePeriod = Duration.ofSeconds(60); //match globalconf refresh
        log.info("Waiting grace period of {} before continuing..", gracePeriod);
        await().pollDelay(gracePeriod).timeout(gracePeriod.plusMinutes(1)).until(() -> true);
    }

    public void destroy() {
        if (!customProperties.isUseCustomEnv()) {
            envSs0.stop();
            envSs1.stop();
            envAux.stop();
        }
    }

    public Optional<ContainerState> getContainerByServiceName(String env, String serviceName) {
        if (customProperties.isUseCustomEnv()) {
            return Optional.empty();
        }
        return mapEnvironment(env).getContainerByServiceName(serviceName);
    }

    public ContainerMapping getContainerMapping(String env, String serviceName, int originalPort) {
        if (customProperties.isUseCustomEnv()) {
            // todo should be refactored if needed
            String key = serviceName + "_" + originalPort;
            var mappedValue = customProperties.getCustomEnvMapping().get(key);
            if (mappedValue == null) {
                throw new IllegalArgumentException("No mapping found for " + key);
            }
            var splittedStr = mappedValue.split("_");
            return new ContainerMapping(splittedStr[0], Integer.parseInt(splittedStr[1]));
        }

        ComposeContainer environment = mapEnvironment(env);
        return new ContainerMapping(
                environment.getServiceHost(serviceName, originalPort),
                environment.getServicePort(serviceName, originalPort)
        );
    }

    private ComposeContainer mapEnvironment(String name) {
        return switch (name) {
            case "ss0" -> envSs0;
            case "ss1" -> envSs1;
            case "aux" -> envAux;
            default -> throw new IllegalArgumentException("Unknown environment: " + name);
        };
    }

    public record ContainerMapping(String host, int port) {
    }
}
