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
package org.niis.xroad.e2e;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.container.BaseComposeSetup;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Slf4j
@Component
public class EnvSetup extends BaseComposeSetup {

    private static final String COMPOSE_AUX_FILE = "compose.aux.yaml";
    private static final String COMPOSE_SS_FILE = "compose.main.yaml";
    private static final String COMPOSE_SS_E2E_FILE = "compose.e2e.yaml";
    private static final String COMPOSE_SS_HSM_FILE = "compose.ss-hsm.e2e.yaml";
    private static final String COMPOSE_SS_BATCH_SIGNATURES_FILE = "compose.ss-batch-signature-enabled.e2e.yaml";

    private static final String CS = "cs";
    private static final String OPENBAO = "openbao";
    private static final String PROXY = "proxy";
    private static final String UI = "ui";
    private static final String SIGNER = "signer";
    private static final String CONFIGURATION_CLIENT = "configuration-client";
    private static final String XROAD_NETWORK = "xroad-network";

    public static final String DB_MESSAGELOG = "db-messagelog";
    public static final String HURL = "hurl";

    private ComposeContainer envSs0;
    private ComposeContainer envSs1;
    private ComposeContainer envAux;

    public EnvSetup(TestFrameworkCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    public void init() {
        envSs0 = createSSEnvironment("ss0", false, true);

        envSs1 = createSSEnvironment("ss1", true, false);

        envAux = new ComposeContainer("aux-", getComposeFilePath(COMPOSE_AUX_FILE))

                .withExposedService(CS, Port.UI, forListeningPort())
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

    @Override
    protected ComposeContainer initEnv() {
        return null;
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

    private ComposeContainer createSSEnvironment(String name, boolean enableHsm, boolean enableBatchSignatures) {
        var files = new ArrayList<>(List.of(getComposeFilePath(COMPOSE_SS_FILE), getComposeFilePath(COMPOSE_SS_E2E_FILE)));

        if (enableHsm) {
            files.add(getComposeFilePath(COMPOSE_SS_HSM_FILE));
        }

        if (enableBatchSignatures) {
            files.add(getComposeFilePath(COMPOSE_SS_BATCH_SIGNATURES_FILE));
        }

        var env = new ComposeContainer(name + "-", files)
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

    private File getComposeFilePath(String fileName) {
        return new File(coreProperties.resourceDir() + fileName);
    }

    private String getContainerName(ComposeContainer env, String container) {
        return env.getContainerByServiceName(container)
                .map(c -> c.getContainerInfo().getName().substring(1)).orElseThrow();
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
        if (envSs0 != null) {
            envSs0.stop();
        }
        if (envSs1 != null) {
            envSs1.stop();
        }
        if (envAux != null) {
            envAux.stop();
        }
    }

    public Optional<ContainerState> getContainerByServiceName(String env, String serviceName) {
        return mapEnvironment(env).getContainerByServiceName(serviceName);
    }

    public ContainerMapping getContainerMapping(String env, String serviceName, int originalPort) {
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

    public static class Port {
        public static final int UI = 4000;
        public static final int PROXY = 8080;
        public static final int DB = 5432;

    }
}
