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

import com.github.dockerjava.api.model.ContainerNetwork;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.container.BaseComposeSetup;
import org.springframework.stereotype.Component;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.common.vault.VaultClient.MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@Slf4j
@Component
public class EnvSetup extends BaseComposeSetup {

    private static final String COMPOSE_AUX_FILE = "compose.aux.yaml";
    private static final String DS_HTTPS_KEYSTORE_VOLUME = "e2e-ds-https-keystore";
    private static final String COMPOSE_SS_FILE = "compose.main.yaml";
    private static final String COMPOSE_SS_E2E_FILE = "compose.e2e.yaml";
    private static final String COMPOSE_SS_E2E_DS_FILE = "compose.e2e.ds.yaml";
    private static final String COMPOSE_SS_HSM_FILE = "compose.ss-hsm.e2e.yaml";
    private static final String COMPOSE_SS_BATCH_SIGNATURES_FILE = "compose.ss-batch-signature-enabled.e2e.yaml";
    private static final String COMPOSE_SS_SOFTTOKEN_SIGNER_FILE = "compose.ss-softtoken-signer-enabled.e2e.yaml";
    private static final String COMPOSE_SS_MSGLOG_ENCRYPTION = "compose.ss-msglog-encryption.e2e.yaml";
    private static final String COMPOSE_SS_MSGLOG_CLI = "compose.ss-msglog.e2e.yaml";
    private static final String COMPOSE_SS_OPMONITOR_FILE = "compose.ss-opmonitor.e2e.yaml";

    private static final String CS = "cs";
    private static final String OPENBAO = "openbao";
    private static final String PROXY = "proxy";
    private static final String UI = "ui";
    private static final String SIGNER = "signer";
    private static final String SOFTTOKEN_SIGNER = "softtoken-signer";
    private static final String CONFIGURATION_CLIENT = "configuration-client";
    private static final String AUX_SERVICE = "auxiliary-service";
    private static final String MESSAGE_LOG_CLI = "message-log-cli";
    private static final String OP_MONITOR_SERVICE = "op-monitor";
    public static final String DS_CONTROL_PLANE = "ds-control-plane";
    public static final String DS_IDENTITY_HUB = "ds-identity-hub";
    public static final String DS_ISSUER_SERVICE = "ds-issuer-service";
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
        ensureDsHttpsKeystoreVolume();

        envAux = new ComposeContainer("aux-", getComposeFilePath(COMPOSE_AUX_FILE))
                .withExposedService(CS, Port.UI, forListeningPort())
                .withExposedService(DS_ISSUER_SERVICE, Port.ISSUER_SERVICE_ADMIN, forListeningPort())
                .withExposedService(DS_ISSUER_SERVICE, Port.ISSUER_SERVICE_IDENTITY, forListeningPort())
                .withEnv("PROXY_UI_0", "ss0-ui")
                .withEnv("PROXY_0", "xrd-ss0")
                .withEnv("PROXY_UI_1", "ss1-ui")
                .withEnv("PROXY_1", "xrd-ss1")
                .withEnv("IH_HOST_0", "ss0-ds-identity-hub")
                .withEnv("IH_HOST_1", "ss1-ds-identity-hub")
                .withEnv("CP_HOST_0", "ss0-ds-control-plane")
                .withEnv("CP_HOST_1", "ss1-ds-control-plane")
                .withEnv("PARTICIPANT_ID_0", "xrd-ss0")
                .withEnv("PARTICIPANT_ID_1", "xrd-ss1")
                .withLogConsumer(HURL, createLogConsumer("aux", HURL))
                .withLogConsumer(CS, createLogConsumer("aux", CS))
                .withLogConsumer(DS_ISSUER_SERVICE, createLogConsumer("aux", DS_ISSUER_SERVICE))
                .waitingFor(CS, Wait.forLogMessage("^.*xroad-center entered RUNNING state.*$", 1));
        envAux.start();

        envSs0 = createSSEnvironment("ss0", Set.of(Feature.BATCH_SIGNATURES, Feature.SOFTTOKEN_SIGNER, Feature.OP_MONITOR));

        envSs1 = createSSEnvironment("ss1", Set.of(Feature.HSM, Feature.MESSAGE_LOG_ENCRYPTION));

        waitForHurl();
    }

    @Override
    protected ComposeContainer initEnv() {
        return null;
    }

    private void ensureDsHttpsKeystoreVolume() {
        var dockerClient = DockerClientFactory.lazyClient();
        dockerClient.createVolumeCmd().withName(DS_HTTPS_KEYSTORE_VOLUME).exec();
        log.info("Ensured external docker volume {} exists", DS_HTTPS_KEYSTORE_VOLUME);
    }

    private void connectToExternalNetwork(ComposeContainer env, List<String> serviceNames, String envName) {
        for (String serviceName : serviceNames) {
            var containerState = env.getContainerByServiceName(serviceName).orElseThrow();
            var dockerClient = containerState.getDockerClient();

            String networkId = dockerClient.listNetworksCmd().exec().stream()
                    .filter(n -> XROAD_NETWORK.equals(n.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find external network '%s'".formatted(XROAD_NETWORK)))
                    .getId();

            var aliases = new ArrayList<String>();
            aliases.add("%s-%s".formatted(envName, serviceName));
            if (PROXY.equals(serviceName)) {
                aliases.add("xrd-" + envName);
            }

            dockerClient.connectToNetworkCmd()
                    .withContainerId(containerState.getContainerId())
                    .withNetworkId(networkId)
                    .withContainerNetwork(new ContainerNetwork()
                            .withAliases(aliases.toArray(String[]::new)))
                    .exec();
        }
    }

    private ComposeContainer createSSEnvironment(String name, Set<Feature> features) {
        var files = new ArrayList<>(List.of(
                getComposeFilePath(COMPOSE_SS_FILE),
                getComposeFilePath(COMPOSE_SS_E2E_FILE),
                getComposeFilePath(COMPOSE_SS_MSGLOG_CLI),
                getComposeFilePath(COMPOSE_SS_E2E_DS_FILE)));

        features.forEach(f -> files.add(getComposeFilePath(f.getComposeFile())));

        var env = new ComposeContainer(name + "-", files)
                .withEnv("ENV_PREFIX", name + "-")
                .withEnv("DSP_PARTICIPANT_ID", "xrd-" + name)
                .withEnv("DSP_MGMT_CONTEXT", "true")
                .withExposedService(PROXY, Port.PROXY, forListeningPort())
                .withExposedService(PROXY, Port.PROXY_HEALTHCHECK, forListeningPort())
                .withExposedService(UI, Port.UI, forListeningPort())
                .withLogConsumer(DB_MESSAGELOG, createLogConsumer(name, DB_MESSAGELOG))
                .withExposedService(DS_CONTROL_PLANE, Port.CONTROL_PLANE_MANAGEMENT, forListeningPort())
                .withExposedService(DS_IDENTITY_HUB, Port.IDENTITY_HUB_IDENTITY, forListeningPort())
                .withLogConsumer(UI, createLogConsumer(name, UI))
                .withLogConsumer(PROXY, createLogConsumer(name, PROXY))
                .withLogConsumer(CONFIGURATION_CLIENT, createLogConsumer(name, CONFIGURATION_CLIENT))
                .withLogConsumer(SIGNER, createLogConsumer(name, SIGNER))
                .withLogConsumer(OPENBAO, createLogConsumer(name, OPENBAO))
                .withLogConsumer(AUX_SERVICE, createLogConsumer(name, AUX_SERVICE))
                .withLogConsumer(MESSAGE_LOG_CLI, createLogConsumer(name, MESSAGE_LOG_CLI))
                .withLogConsumer(DS_IDENTITY_HUB, createLogConsumer(name, DS_IDENTITY_HUB))
                .withLogConsumer(DS_CONTROL_PLANE, createLogConsumer(name, DS_CONTROL_PLANE));

        if (features.contains(Feature.SOFTTOKEN_SIGNER)) {
            env.withLogConsumer(SOFTTOKEN_SIGNER, createLogConsumer(name, SOFTTOKEN_SIGNER));
        }

        if (features.contains(Feature.OP_MONITOR)) {
            env.withLogConsumer(OP_MONITOR_SERVICE, createLogConsumer(name, OP_MONITOR_SERVICE));
        }

        env.start();

        List<String> services = new ArrayList<>(List.of(UI, PROXY, CONFIGURATION_CLIENT, SIGNER));
        if (features.contains(Feature.SOFTTOKEN_SIGNER)) {
            services.add(SOFTTOKEN_SIGNER);
        }
        connectToExternalNetwork(env, services, name);

        if (features.contains(Feature.MESSAGE_LOG_ENCRYPTION)) {
            importPublicKeysToBao(env);
        }

        return env;
    }

    private File getComposeFilePath(String fileName) {
        return new File(coreProperties.resourceDir() + fileName);
    }

    @SneakyThrows
    private void importPublicKeysToBao(ComposeContainer env) {
        var container = env.getContainerByServiceName(OPENBAO).orElseThrow();
        container.execInContainer("bao", "write", "xrd-secret/" + MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH,
                "payload=@/gpg-keys/public-keys.asc");
    }

    public String getContainerName(String env, String container) {
        return getContainerName(mapEnvironment(env), container);
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

        awaitProxyReadiness("ss0");
        awaitProxyReadiness("ss1");

        var gracePeriod = Duration.ofSeconds(20);
        log.info("Waiting grace period of {} for global configuration to propagate..", gracePeriod);
        await().pollDelay(gracePeriod).timeout(gracePeriod.plusMinutes(1)).until(() -> true);
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void awaitProxyReadiness(String env) {
        var mapping = getContainerMapping(env, PROXY, Port.PROXY_HEALTHCHECK);
        var readinessUrl = "http://%s:%d/q/health/ready".formatted(mapping.host(), mapping.port());
        log.info("Waiting for {} proxy readiness at {}", env, readinessUrl);

        await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(5))
                .ignoreExceptions()
                .until(() -> {
                    var json = given().get(readinessUrl).jsonPath();
                    var overall = json.getString("status");
                    var authKeyOcsp = json.getString(
                            "checks.find { it.name == 'PROXY_AUTH_KEY_OCSP_READINESS_CHECK' }.data.status");
                    log.info("{} proxy readiness: status={}, authKeyOcsp={}", env, overall, authKeyOcsp);
                    return "UP".equals(overall) && "OK".equals(authKeyOcsp);
                });
    }

    @Override
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

    @SneakyThrows
    public String execMessagelogSql(String env, String sql) {
        var container = getContainerByServiceName(env, DB_MESSAGELOG).orElseThrow();
        var result = container.execInContainer("psql", "-U", "postgres", "-d", "messagelog", "-tAX", "-c", sql);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql query on %s failed: %s".formatted(env, result.getStderr()));
        }
        return result.getStdout().trim();
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
        public static final int PROXY_HEALTHCHECK = 5588;
        public static final int DB = 5432;
        public static final int CONTROL_PLANE_MANAGEMENT = 8182;
        public static final int CONTROL_PLANE_PROTOCOL = 8183;
        public static final int IDENTITY_HUB_IDENTITY = 7182;
        public static final int IDENTITY_HUB_STS = 7184;
        public static final int ISSUER_SERVICE_IDENTITY = 6182;
        public static final int ISSUER_SERVICE_ADMIN = 6186;
    }

    enum Feature {
        HSM(COMPOSE_SS_HSM_FILE),
        BATCH_SIGNATURES(COMPOSE_SS_BATCH_SIGNATURES_FILE),
        SOFTTOKEN_SIGNER(COMPOSE_SS_SOFTTOKEN_SIGNER_FILE),
        MESSAGE_LOG_ENCRYPTION(COMPOSE_SS_MSGLOG_ENCRYPTION),
        OP_MONITOR(COMPOSE_SS_OPMONITOR_FILE);

        private final String composeFile;

        Feature(String composeFile) {
            this.composeFile = composeFile;
        }

        String getComposeFile() {
            return composeFile;
        }
    }
}
