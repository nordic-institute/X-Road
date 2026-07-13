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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.common.vault.VaultClient.MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

/**
 * Docker Compose-based implementation of the e2e environment.
 */
@Slf4j
public class ComposeEnvSetup extends BaseComposeSetup
        implements E2eEnvironment, ComposeContainerOps, MessagelogDbOps, MessagelogArchiveOps {

    private static final Pattern PROCESSED_FILES_PATTERN = Pattern.compile("Processed (\\d+) files\\.");

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

    private ComposeContainer envSs0;
    private ComposeContainer envSs1;
    private ComposeContainer envAux;

    public ComposeEnvSetup(TestFrameworkCoreProperties coreProperties) {
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

    @Override
    public boolean isInitialized() {
        return envAux.getContainerByServiceName(HURL)
                .map(container -> !container.isRunning())
                .orElse(false);
    }

    @Override
    public String peerControlPlaneHost(String env) {
        return env + "-ds-control-plane";
    }

    @Override
    public String participantContextId(String env) {
        return "xrd-" + env;
    }

    @Override
    public String participantDid(String env) {
        return "did:web:%s-ds-identity-hub%%3A7183".formatted(env);
    }

    @Override
    public String securityServerAddress(String env) {
        return "xrd-" + env;
    }

    @Override
    public E2eEnvironment.ContainerMapping getContainerMapping(String env, String service, int port) {
        var environment = mapEnvironment(env);
        return new E2eEnvironment.ContainerMapping(
                environment.getServiceHost(service, port),
                environment.getServicePort(service, port)
        );
    }

    @Override
    public Optional<ContainerState> getContainerByServiceName(String env, String serviceName) {
        return mapEnvironment(env).getContainerByServiceName(serviceName);
    }

    @Override
    @SneakyThrows
    public String execMessagelogSql(String env, String sql) {
        var container = getContainerByServiceName(env, DB_MESSAGELOG).orElseThrow();
        var result = container.execInContainer("psql", "-U", "postgres", "-d", "messagelog", "-tAX", "-c", sql);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql query on %s failed: %s".formatted(env, result.getStderr()));
        }
        return result.getStdout().trim();
    }

    @Override
    @SneakyThrows
    public void triggerMessageLogCommand(String env, String command) {
        var container = getContainerByServiceName(env, MESSAGE_LOG_CLI).orElseThrow();
        var javaCmd = "java -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
                + " -Dquarkus.profile=containerized"
                + " -jar /opt/app/quarkus-run.jar " + command
                + " 2>&1 | tee /proc/1/fd/1";
        var result = container.execInContainer("sh", "-c", javaCmd);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Message log %s failed (exit code %d): %s".formatted(
                    command, result.getExitCode(), result.getStderr()));
        }
    }

    @Override
    @SneakyThrows
    public void downloadMessageLogArchives(String env, String localDir) {
        downloadArchivesTarball(env, MESSAGE_LOG_CLI, "/var/lib/xroad", localDir);
    }

    @Override
    @SneakyThrows
    public int decryptArchives(String env, String filePrefix, String keyId, String passphrase, String outputDir) {
        String keyFile = "/gpg-keys/%s.asc".formatted(keyId);
        String remoteOutputDir = "/tmp/decrypt-" + UUID.randomUUID();

        var container = getContainerByServiceName(env, MESSAGE_LOG_CLI).orElseThrow();
        Container.ExecResult execResult = container.execInContainer("/gpg-keys/scripts/decrypt-archives.sh",
                filePrefix, keyFile, passphrase, remoteOutputDir);

        downloadArchivesTarball(env, MESSAGE_LOG_CLI, remoteOutputDir, outputDir);

        return getProcessedFilesCountFromOutput(execResult.getStdout());
    }

    @SneakyThrows
    private void downloadArchivesTarball(String env, String service, String remoteDir, String localDir) {
        Files.createDirectories(Paths.get(localDir));
        var localCompressedArchivesPath = localDir + "/messagelog-archives.tar.gz";
        var container = getContainerByServiceName(env, service).orElseThrow();
        container.execInContainer("tar", "czf", "/tmp/messagelog-archives.tar.gz", "-C", remoteDir, ".");
        container.copyFileFromContainer("/tmp/messagelog-archives.tar.gz", localCompressedArchivesPath);
        container.execInContainer("rm", "/tmp/messagelog-archives.tar.gz");
    }

    private int getProcessedFilesCountFromOutput(String output) {
        Matcher matcher = PROCESSED_FILES_PATTERN.matcher(output);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new IllegalStateException("Could not find processed files count in decrypt-archives.sh output: " + output);
    }

    /**
     * Returns the Docker container name for the named service in the given environment.
     */
    public String getContainerName(String env, String container) {
        return getContainerName(mapEnvironment(env), container);
    }

    private String getContainerName(ComposeContainer environment, String container) {
        return environment.getContainerByServiceName(container)
                .map(c -> c.getContainerInfo().getName().substring(1)).orElseThrow();
    }

    private void ensureDsHttpsKeystoreVolume() {
        var dockerClient = DockerClientFactory.lazyClient();
        dockerClient.createVolumeCmd().withName(DS_HTTPS_KEYSTORE_VOLUME).exec();
        log.info("Ensured external docker volume {} exists", DS_HTTPS_KEYSTORE_VOLUME);
    }

    private void connectToExternalNetwork(ComposeContainer environment, List<String> serviceNames, String envName) {
        for (String serviceName : serviceNames) {
            var containerState = environment.getContainerByServiceName(serviceName).orElseThrow();
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

        var environment = new ComposeContainer(name + "-", files)
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
            environment.withLogConsumer(SOFTTOKEN_SIGNER, createLogConsumer(name, SOFTTOKEN_SIGNER));
        }

        if (features.contains(Feature.OP_MONITOR)) {
            environment.withLogConsumer(OP_MONITOR_SERVICE, createLogConsumer(name, OP_MONITOR_SERVICE));
        }

        environment.start();

        List<String> services = new ArrayList<>(List.of(UI, PROXY, CONFIGURATION_CLIENT, SIGNER));
        if (features.contains(Feature.SOFTTOKEN_SIGNER)) {
            services.add(SOFTTOKEN_SIGNER);
        }
        connectToExternalNetwork(environment, services, name);

        if (features.contains(Feature.MESSAGE_LOG_ENCRYPTION)) {
            importPublicKeysToBao(environment);
        }

        return environment;
    }

    private File getComposeFilePath(String fileName) {
        return new File(coreProperties.resourceDir() + fileName);
    }

    @SneakyThrows
    private void importPublicKeysToBao(ComposeContainer environment) {
        var container = environment.getContainerByServiceName(OPENBAO).orElseThrow();
        container.execInContainer("bao", "write", "xrd-secret/" + MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH,
                "payload=@/gpg-keys/public-keys.asc");
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

    private ComposeContainer mapEnvironment(String name) {
        return switch (name) {
            case "ss0" -> envSs0;
            case "ss1" -> envSs1;
            case "aux" -> envAux;
            default -> throw new IllegalArgumentException("Unknown environment: " + name);
        };
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
