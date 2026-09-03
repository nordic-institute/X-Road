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

import com.github.dockerjava.api.model.ContainerNetwork;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.common.vault.VaultClient.MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

/**
 * A single Security Server compose stack (ss0 or ss1), including the DSP control-plane/identity-hub
 * containers, connected to the shared {@code xroad-network} so it can reach the other SS stack and
 * the aux stack's Central Server.
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
public class SsStackSetup extends BaseComposeSetup {

    public static final String UI = "ui";
    public static final String PROXY = "proxy";
    public static final String SIGNER = "signer";
    public static final String CONFIGURATION_CLIENT = "configuration-client";
    public static final String SOFTTOKEN_SIGNER = "softtoken-signer";
    public static final String OPENBAO = "openbao";
    public static final String AUX_SERVICE = "auxiliary-service";
    public static final String MESSAGE_LOG_CLI = "message-log-cli";
    public static final String MONITOR = "monitor";
    public static final String OP_MONITOR_SERVICE = "op-monitor";
    public static final String DB_MESSAGELOG = "db-messagelog";
    public static final String DS_CONTROL_PLANE = "ds-control-plane";
    public static final String DS_IDENTITY_HUB = "ds-identity-hub";

    private static final String COMPOSE_SS_FILE = "compose.main.yaml";
    private static final String COMPOSE_SS_E2E_FILE = "compose.e2e.yaml";
    private static final String COMPOSE_SS_E2E_DS_FILE = "compose.e2e.ds.yaml";
    private static final String COMPOSE_SS_MSGLOG_CLI = "compose.ss-msglog.e2e.yaml";
    private static final String XROAD_NETWORK = "xroad-network";

    private final String name;
    private final Set<Feature> features;

    public SsStackSetup(ApiTestCoreProperties coreProperties, String name, Set<Feature> features) {
        super(coreProperties);
        this.name = name;
        this.features = features;
    }

    @Override
    protected String composeProjectName() {
        return name + "-";
    }

    @Override
    protected ComposeContainer initEnv() {
        var files = new ArrayList<>(List.of(
                composeFile(COMPOSE_SS_FILE),
                composeFile(COMPOSE_SS_E2E_FILE),
                composeFile(COMPOSE_SS_MSGLOG_CLI),
                composeFile(COMPOSE_SS_E2E_DS_FILE)));
        features.forEach(f -> files.add(composeFile(f.composeFile)));

        var env = new ComposeContainer(composeProjectName(), files)
                .withEnv("ENV_PREFIX", composeProjectName())
                .withEnv("DSP_PARTICIPANT_ID", "xrd-" + name)
                .withEnv("DSP_MGMT_CONTEXT", "true")
                .withExposedService(PROXY, Port.PROXY, forListeningPort())
                .withExposedService(PROXY, Port.PROXY_HEALTHCHECK, forListeningPort())
                .withExposedService(UI, Port.UI, forListeningPort())
                // No start-time waits on the ds-* services: they fail fast on the empty
                // tls/ds-https vault slot and retry until setup.hurl provisions the
                // certificate through the admin API, which runs after all stacks are up;
                // the hurl scenario's DSP readiness gates assert their convergence.
                .withLogConsumer(DB_MESSAGELOG, createLogConsumer(name, DB_MESSAGELOG))
                .withLogConsumer(UI, createLogConsumer(name, UI))
                .withLogConsumer(PROXY, createLogConsumer(name, PROXY))
                .withLogConsumer(CONFIGURATION_CLIENT, createLogConsumer(name, CONFIGURATION_CLIENT))
                .withLogConsumer(SIGNER, createLogConsumer(name, SIGNER))
                .withLogConsumer(OPENBAO, createLogConsumer(name, OPENBAO))
                .withLogConsumer(AUX_SERVICE, createLogConsumer(name, AUX_SERVICE))
                .withLogConsumer(MESSAGE_LOG_CLI, createLogConsumer(name, MESSAGE_LOG_CLI))
                .withLogConsumer(MONITOR, createLogConsumer(name, MONITOR))
                .withLogConsumer(DS_IDENTITY_HUB, createLogConsumer(name, DS_IDENTITY_HUB))
                .withLogConsumer(DS_CONTROL_PLANE, createLogConsumer(name, DS_CONTROL_PLANE));

        if (features.contains(Feature.SOFTTOKEN_SIGNER)) {
            env.withLogConsumer(SOFTTOKEN_SIGNER, createLogConsumer(name, SOFTTOKEN_SIGNER));
        }
        if (features.contains(Feature.OP_MONITOR)) {
            env.withLogConsumer(OP_MONITOR_SERVICE, createLogConsumer(name, OP_MONITOR_SERVICE));
        }
        return env;
    }

    @Override
    protected void onPostStart() {
        var services = new ArrayList<>(List.of(UI, PROXY, CONFIGURATION_CLIENT, SIGNER));
        if (features.contains(Feature.SOFTTOKEN_SIGNER)) {
            services.add(SOFTTOKEN_SIGNER);
        }
        connectToExternalNetwork(services);

        if (features.contains(Feature.MESSAGE_LOG_ENCRYPTION)) {
            importPublicKeysToBao();
        }
    }

    /**
     * Blocks until this stack's proxy reports readiness, including OCSP status for the auth key.
     */
    public void awaitProxyReadiness() {
        var mapping = getContainerMapping(PROXY, Port.PROXY_HEALTHCHECK);
        var readinessUrl = "http://%s:%d/q/health/ready".formatted(mapping.host(), mapping.port());
        log.info("Waiting for {} proxy readiness at {}", name, readinessUrl);

        await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(5))
                .ignoreExceptions()
                .until(() -> {
                    var json = given().get(readinessUrl).jsonPath();
                    var overall = json.getString("status");
                    var authKeyOcsp = json.getString(
                            "checks.find { it.name == 'PROXY_AUTH_KEY_OCSP_READINESS_CHECK' }.data.status");
                    log.info("{} proxy readiness: status={}, authKeyOcsp={}", name, overall, authKeyOcsp);
                    return "UP".equals(overall) && "OK".equals(authKeyOcsp);
                });
    }

    private void connectToExternalNetwork(List<String> serviceNames) {
        for (String serviceName : serviceNames) {
            var containerState = env.getContainerByServiceName(serviceName).orElseThrow();
            var dockerClient = containerState.getDockerClient();

            String networkId = dockerClient.listNetworksCmd().exec().stream()
                    .filter(n -> XROAD_NETWORK.equals(n.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find external network '%s'".formatted(XROAD_NETWORK)))
                    .getId();

            var aliases = new ArrayList<String>();
            aliases.add("%s-%s".formatted(name, serviceName));
            if (PROXY.equals(serviceName)) {
                aliases.add("xrd-" + name);
            }

            dockerClient.connectToNetworkCmd()
                    .withContainerId(containerState.getContainerId())
                    .withNetworkId(networkId)
                    .withContainerNetwork(new ContainerNetwork().withAliases(aliases.toArray(String[]::new)))
                    .exec();
        }
    }

    @SneakyThrows
    private void importPublicKeysToBao() {
        var container = env.getContainerByServiceName(OPENBAO).orElseThrow();
        container.execInContainer("bao", "write", "xrd-secret/" + MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH,
                "payload=@/gpg-keys/public-keys.asc");
    }

    private Slf4jLogConsumer createLogConsumer(String envName, String containerName) {
        return createLogConsumer("%s-%s".formatted(envName, containerName));
    }

    private File composeFile(String fileName) {
        return new File(coreProperties.resourceDir() + fileName);
    }

    /**
     * Exposed ports on an SS stack's containers.
     */
    public static final class Port {
        public static final int UI = 4000;
        public static final int PROXY = 8080;
        public static final int PROXY_HEALTHCHECK = 5588;

        private Port() {
        }
    }

    /**
     * Optional service combinations layered onto the base SS compose files via an extra compose file each.
     */
    enum Feature {
        HSM("compose.ss-hsm.e2e.yaml"),
        BATCH_SIGNATURES("compose.ss-batch-signature-enabled.e2e.yaml"),
        SOFTTOKEN_SIGNER("compose.ss-softtoken-signer-enabled.e2e.yaml"),
        MESSAGE_LOG_ENCRYPTION("compose.ss-msglog-encryption.e2e.yaml"),
        OP_MONITOR("compose.ss-opmonitor.e2e.yaml");

        private final String composeFile;

        Feature(String composeFile) {
            this.composeFile = composeFile;
        }
    }
}
