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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.e2e.AclParityProperties;
import org.niis.xroad.e2e.E2eEnvironment;
import org.niis.xroad.e2e.IssuerOutageOps;
import org.niis.xroad.e2e.MessagelogArchiveOps;
import org.niis.xroad.e2e.MessagelogDbOps;
import org.niis.xroad.e2e.parity.acl.AclMode;
import org.niis.xroad.e2e.parity.acl.AclModeProbe;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.Container;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

/**
 * Facade over the three compose stacks (aux, ss0, ss1). Extends {@link BaseComposeSetup} only to satisfy
 * the api-test-core session-listener/extension lifecycle; the inherited single-stack accessors are
 * overridden to fail loudly — test code must use the env-qualified overloads.
 */
@Slf4j
public class E2eEnvSetup extends BaseComposeSetup
        implements E2eEnvironment, MessagelogDbOps, MessagelogArchiveOps, IssuerOutageOps {

    private static final Pattern PROCESSED_FILES_PATTERN = Pattern.compile("Processed (\\d+) files\\.");
    private static final String MESSAGELOG_ARCHIVES_FILE = "messagelog-archives.tar.gz";

    /** Country profile ss0 runs with in every stack shape. */
    static final String SS0_COUNTRY_PROFILE = "FO";
    private static final String AUX_ENV = "aux";
    private static final String CA_ENV = "ca";
    private static final Duration GLOBALCONF_PROPAGATION_GRACE_PERIOD = Duration.ofSeconds(20);
    private static final Duration SERVICE_RESTART_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration SERVICE_RESTART_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final String ISSUER_SERVICE_PROGRAM = "xroad-ds-issuer-service";
    private static final String CENTRAL_SERVER_DATABASE = "centerui_production";
    private static final String ISSUER_DATA_FRESHNESS_KEY = "issuerDataFreshnessSeconds";
    private static final String GLOBALCONF_DIRECTORY = "/etc/xroad/globalconf";
    private static final Duration ISSUER_START_TIMEOUT = Duration.ofMinutes(3);

    private AuxStackSetup aux;
    private AbstractSsStack ss0;
    private SsStackSetup ss1;

    public E2eEnvSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    public void start() {
        AclParityProperties.configuredMode().ifPresent(mode -> AclModeProbe.verifyProviderStack(mode,
                coreProperties.ss0Stack() == ApiTestCoreProperties.Ss0Stack.SIDECAR));

        aux = new AuxStackSetup(coreProperties);
        aux.start();

        ss0 = buildSs0Stack(coreProperties);
        ss0.start();

        ss1 = new SsStackSetup(coreProperties, "ss1", ss1Features());
        ss1.start();

        aux.waitForHurlToFinish();
        ss0.awaitProxyReadiness();
        ss1.awaitProxyReadiness();

        log.info("Waiting grace period of {} for global configuration to propagate..", GLOBALCONF_PROPAGATION_GRACE_PERIOD);
        await().pollDelay(GLOBALCONF_PROPAGATION_GRACE_PERIOD)
                .timeout(GLOBALCONF_PROPAGATION_GRACE_PERIOD.plusMinutes(1))
                .until(() -> true);
    }

    /**
     * Makes ss0 refuse X-Road 7 style calls: stores {@code xroad.proxy.legacy-protocol-disabled} in its serverconf and
     * restarts its proxy, which reads the setting once at start, then waits until the proxy is ready again.
     * Only the per-service ss0 stack has a proxy container of its own to restart.
     */
    @SneakyThrows
    public void refuseLegacyCallsOnSs0() {
        if (!(ss0 instanceof SsStackSetup stack)) {
            throw new IllegalStateException(
                    "legacy refusal needs the default ss0 stack; the sidecar stack has no proxy container");
        }
        var upsert = """
                INSERT INTO configuration_properties (property_key, property_value, created_at, updated_at)
                VALUES ('%s', 'true', now(), now())
                ON CONFLICT (property_key, ha_node_name) DO UPDATE SET property_value = EXCLUDED.property_value, updated_at = now()
                """.formatted(ProxyConfigKeys.LEGACY_PROTOCOL_DISABLED.key());
        var stored = stack.execInContainer(SsStackSetup.DB_SERVERCONF,
                "env", "PGOPTIONS=-c search_path=serverconf,public",
                "psql", "-U", "postgres", "-d", "serverconf", "-v", "ON_ERROR_STOP=1", "-c", upsert);
        if (stored.getExitCode() != 0) {
            throw new IllegalStateException(
                    "storing legacy-protocol-disabled in the serverconf of ss0 failed: " + stored.getStderr());
        }
        restartAndAwaitHealthy(stack, List.of(new HealthProbe(SsStackSetup.PROXY,
                List.of("curl", "-fs", "http://localhost:5588/q/health/ready"))));
        log.info("ss0 proxy is ready with legacy calls refused");
    }

    @Override
    public boolean ss0RunsSeparateDataspaceServices() {
        return ss0 instanceof SsStackSetup;
    }

    @Override
    public void stopIssuerService() {
        if (issuerServiceRuns()) {
            superviseIssuerService("stop");
        }
    }

    @Override
    public void startIssuerService() {
        if (!issuerServiceRuns()) {
            superviseIssuerService("start");
        }
        await().pollDelay(Duration.ZERO).atMost(ISSUER_START_TIMEOUT).pollInterval(SERVICE_RESTART_POLL_INTERVAL)
                .until(this::issuerServiceRuns);
    }

    @Override
    public void restartSs0DataspaceServices() {
        if (!(ss0 instanceof SsStackSetup stack)) {
            throw new IllegalStateException("the sidecar stack has no separate dataspace containers to restart");
        }
        restartAndAwaitHealthy(stack, List.of(
                new HealthProbe(SsStackSetup.DS_CONTROL_PLANE, List.of("curl", "-fsS", "http://localhost:4099/q/health")),
                new HealthProbe(SsStackSetup.DS_IDENTITY_HUB,
                        List.of("curl", "-fksS", "https://localhost:7181/api/check/health"))));
        log.info("ss0 control plane and Identity Hub are healthy again");
    }

    @Override
    public long countSs0ControlPlaneLogLines(Pattern pattern) {
        if (!(ss0 instanceof SsStackSetup stack)) {
            throw new IllegalStateException("the sidecar stack has no separate control plane container");
        }
        return stack.containerLogLines(SsStackSetup.DS_CONTROL_PLANE).filter(line -> pattern.matcher(line).find()).count();
    }

    @Override
    public void setIssuerDataFreshnessSeconds(int seconds) {
        runCentralServerSql("""
                SET ROLE centerui;
                UPDATE centerui.system_parameters SET value = '%1$d', updated_at = now() WHERE key = '%2$s';
                INSERT INTO centerui.system_parameters (key, value, created_at, updated_at, ha_node_name)
                SELECT '%2$s', '%1$d', now(), now(), (SELECT min(ha_node_name) FROM centerui.system_parameters)
                WHERE NOT EXISTS (SELECT 1 FROM centerui.system_parameters WHERE key = '%2$s');
                """.formatted(seconds, ISSUER_DATA_FRESHNESS_KEY));
    }

    @Override
    public boolean ss0HasDownloadedIssuerDataFreshnessSeconds(int seconds) {
        return execInEnvContainer("ss0", SsStackSetup.CONFIGURATION_CLIENT, "grep", "-rlE",
                "%s>[[:space:]]*%d[[:space:]]*<".formatted(ISSUER_DATA_FRESHNESS_KEY, seconds), GLOBALCONF_DIRECTORY)
                .getExitCode() == 0;
    }

    @Override
    public void resetIssuerDataFreshnessSeconds() {
        runCentralServerSql("SET ROLE centerui; DELETE FROM centerui.system_parameters WHERE key = '%s';"
                .formatted(ISSUER_DATA_FRESHNESS_KEY));
    }

    private static void restartAndAwaitHealthy(SsStackSetup stack, List<HealthProbe> services) {
        services.forEach(service -> stack.restartService(service.container()));
        var lastFailure = new AtomicReference<>("no probe finished");
        try {
            await().pollDelay(Duration.ZERO).atMost(SERVICE_RESTART_TIMEOUT).pollInterval(SERVICE_RESTART_POLL_INTERVAL)
                    .until(() -> services.stream().allMatch(service -> probe(stack, service, lastFailure)));
        } catch (ConditionTimeoutException e) {
            var dockerHealth = services.stream()
                    .map(service -> service.container() + ": " + stack.dockerHealthLog(service.container()))
                    .collect(Collectors.joining(" | "));
            throw new IllegalStateException("not healthy %s after the restart, last failed probe: %s, docker healthchecks: %s"
                    .formatted(SERVICE_RESTART_TIMEOUT, lastFailure.get(), dockerHealth), e);
        }
    }

    private static boolean probe(SsStackSetup stack, HealthProbe service, AtomicReference<String> lastFailure) {
        try {
            var result = stack.execInContainer(service.container(), service.command().toArray(String[]::new));
            if (result.getExitCode() == 0) {
                return true;
            }
            lastFailure.set("%s exit=%d stdout=%s stderr=%s".formatted(service.container(), result.getExitCode(),
                    result.getStdout().strip(), result.getStderr().strip()));
        } catch (Exception e) {
            lastFailure.set("%s %s".formatted(service.container(), e));
        }
        log.info("Health probe failed: {}", lastFailure.get());
        return false;
    }

    private boolean issuerServiceRuns() {
        return execInEnvContainer(AUX_ENV, AuxStackSetup.CS, "supervisorctl", "status", ISSUER_SERVICE_PROGRAM)
                .getStdout().contains("RUNNING");
    }

    private void superviseIssuerService(String action) {
        var result = execInEnvContainer(AUX_ENV, AuxStackSetup.CS, "supervisorctl", action, ISSUER_SERVICE_PROGRAM);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("supervisorctl %s %s failed: %s %s"
                    .formatted(action, ISSUER_SERVICE_PROGRAM, result.getStdout(), result.getStderr()));
        }
    }

    private void runCentralServerSql(String sql) {
        var result = execInEnvContainer(AUX_ENV, AuxStackSetup.CS, "su", "-c",
                "psql -q -v ON_ERROR_STOP=1 %s -c \"%s\"".formatted(CENTRAL_SERVER_DATABASE, sql), "postgres");
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("SQL on the Central Server database failed: " + result.getStderr());
        }
    }

    @Override
    public void stop() {
        if (ss1 != null) {
            ss1.stop();
        }
        if (ss0 != null) {
            ss0.stop();
        }
        if (aux != null) {
            aux.stop();
        }
    }

    @Override
    public boolean isInitialized() {
        return aux.isSetupFinished();
    }

    @Override
    public String securityServerAddress(String envName) {
        return "xrd-" + envName;
    }

    @Override
    public ContainerMapping getContainerMapping(String envName, String service, int originalPort) {
        return mapEnvironment(envName).getContainerMapping(composeServiceName(envName, service), originalPort);
    }

    /**
     * The aux stack's admin UI is the compose service {@code cs}; the other environments' is {@code ui}.
     * The test CA env resolves to the aux stack's {@code testca}, exposed only in ACL parity mode.
     */
    private static String composeServiceName(String envName, String service) {
        if (CA_ENV.equals(envName) && SsStackSetup.CA.equals(service)) {
            return AuxStackSetup.TESTCA;
        }
        return AUX_ENV.equals(envName) && SsStackSetup.UI.equals(service) ? AuxStackSetup.CS : service;
    }

    /**
     * Not an {@code execInContainer} overload: both take a trailing {@code String...}, which would make
     * call sites arity-ambiguous.
     */
    public Container.ExecResult execInEnvContainer(String envName, String container, String... command) {
        return mapEnvironment(envName).execInContainer(container, command);
    }

    public void copyFileFromContainer(String envName, String container, String containerPath, String localPath) {
        mapEnvironment(envName).copyFileFromContainer(container, containerPath, localPath);
    }

    @Override
    @SneakyThrows
    public String execMessagelogSql(String envName, String sql) {
        var sidecar = sidecarSs0(envName);
        if (sidecar != null) {
            return sidecar.execMessagelogSql(sql);
        }
        var result = execInEnvContainer(envName, SsStackSetup.DB_MESSAGELOG,
                "env", "PGOPTIONS=-c search_path=messagelog,public",
                "psql", "-U", "postgres", "-d", "messagelog", "-tAX", "-c", sql);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql query on %s failed: %s".formatted(envName, result.getStderr()));
        }
        return result.getStdout().trim();
    }

    @Override
    @SneakyThrows
    public void triggerMessageLogCommand(String envName, String command) {
        var sidecar = sidecarSs0(envName);
        if (sidecar != null) {
            sidecar.triggerMessageLogCommand(command);
            return;
        }
        var javaCmd = "java -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
                + " -Dquarkus.profile=containerized"
                + " -jar /opt/app/quarkus-run.jar " + command
                + " 2>&1 | tee /proc/1/fd/1";
        var result = execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI, "sh", "-c", javaCmd);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Message log %s failed on %s (exit code %d): %s"
                    .formatted(command, envName, result.getExitCode(), result.getStderr()));
        }
    }

    @Override
    public void downloadMessageLogArchives(String envName, String localDir) {
        var sidecar = sidecarSs0(envName);
        if (sidecar != null) {
            sidecar.downloadMessageLogArchives(localDir);
            return;
        }
        downloadArchivesTarball(envName, "/var/lib/xroad", localDir);
    }

    @Override
    @SneakyThrows
    public int decryptArchives(String envName, String filePrefix, String keyId, String passphrase, String outputDir) {
        var sidecar = sidecarSs0(envName);
        if (sidecar != null) {
            return sidecar.decryptArchives(filePrefix, keyId, passphrase, outputDir);
        }
        var keyFile = "/gpg-keys/%s.asc".formatted(keyId);
        var remoteOutputDir = "/tmp/decrypt-" + UUID.randomUUID();

        var execResult = execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI,
                "/gpg-keys/scripts/decrypt-archives.sh", filePrefix, keyFile, passphrase, remoteOutputDir);

        downloadArchivesTarball(envName, remoteOutputDir, outputDir);

        return processedFilesCount(execResult.getStdout());
    }

    @SneakyThrows
    private void downloadArchivesTarball(String envName, String remoteDir, String localDir) {
        Files.createDirectories(Paths.get(localDir));
        var localCompressedArchivesPath = localDir + "/" + MESSAGELOG_ARCHIVES_FILE;
        execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI,
                "tar", "czf", "/tmp/" + MESSAGELOG_ARCHIVES_FILE, "-C", remoteDir, ".");
        copyFileFromContainer(envName, SsStackSetup.MESSAGE_LOG_CLI, "/tmp/" + MESSAGELOG_ARCHIVES_FILE, localCompressedArchivesPath);
        execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI, "rm", "/tmp/" + MESSAGELOG_ARCHIVES_FILE);
    }

    private int processedFilesCount(String output) {
        var matcher = PROCESSED_FILES_PATTERN.matcher(output);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new IllegalStateException("Could not find processed files count in decrypt-archives.sh output: " + output);
    }

    /**
     * ss1's optional stack features. In a legacy ACL parity run ss1 sends unnegotiated calls; in a DSP run it keeps
     * its default and negotiates. ss0 is the same in both.
     */
    private static Set<SsStackSetup.Feature> ss1Features() {
        var features = EnumSet.of(SsStackSetup.Feature.HSM, SsStackSetup.Feature.MESSAGE_LOG_ENCRYPTION);
        if (AclParityProperties.configuredMode().orElse(null) == AclMode.LEGACY) {
            features.add(SsStackSetup.Feature.LEGACY_PROTOCOL);
        }
        return features;
    }

    private BaseComposeSetup mapEnvironment(String name) {
        return switch (name) {
            case "ss0" -> ss0;
            case "ss1" -> ss1;
            case AUX_ENV, CA_ENV -> aux;
            default -> throw new IllegalArgumentException("Unknown environment: " + name);
        };
    }

    private AbstractSsStack buildSs0Stack(ApiTestCoreProperties coreProperties) {
        return switch (coreProperties.ss0Stack()) {
            case DEFAULT -> new SsStackSetup(coreProperties, "ss0",
                    Set.of(SsStackSetup.Feature.BATCH_SIGNATURES, SsStackSetup.Feature.SOFTTOKEN_SIGNER,
                            SsStackSetup.Feature.OP_MONITOR, SsStackSetup.Feature.AGREEMENT_TOKEN_KEY_PRUNING,
                            SsStackSetup.Feature.COUNTRY_PROFILE, SsStackSetup.Feature.ISSUER_OUTAGE));
            case SIDECAR -> new SidecarSsStackSetup(coreProperties, "ss0");
        };
    }

    /**
     * Returns {@code ss0} when it is running the sidecar stack variant, so messagelog DB and archive
     * operations can route to its single-container exec-based implementation instead of the
     * {@code db-messagelog}/{@code message-log-cli} containers the multi-container stack exposes.
     * Returns {@code null} for every other environment name (including {@code ss0} when it is the
     * multi-container stack).
     */
    private SidecarSsStackSetup sidecarSs0(String envName) {
        return "ss0".equals(envName) && ss0 instanceof SidecarSsStackSetup sidecar ? sidecar : null;
    }

    @Override
    protected String composeProjectName() {
        throw new UnsupportedOperationException(
                "E2eEnvSetup manages three independent compose stacks; see AuxStackSetup/SsStackSetup composeProjectName()");
    }

    @Override
    public ContainerMapping getContainerMapping(String service, int originalPort) {
        throw new UnsupportedOperationException("Use getContainerMapping(env, service, port); this facade manages three stacks");
    }

    @Override
    public Container.ExecResult execInContainer(String container, String... command) {
        throw new UnsupportedOperationException("Use execInEnvContainer(env, container, command); this facade manages three stacks");
    }

    @Override
    public void restartService(String containerName) {
        throw new UnsupportedOperationException("Not supported on the multi-stack facade");
    }

    @Override
    public void copyFilesToContainer(String containerName, String classpathResource, String targetPath) {
        throw new UnsupportedOperationException("Not supported on the multi-stack facade");
    }

    @Override
    public void copyFileFromContainer(String containerName, String containerPath, String localPath) {
        throw new UnsupportedOperationException("Use copyFileFromContainer(env, container, containerPath, localPath); "
                + "this facade manages three stacks");
    }

    private record HealthProbe(String container, List<String> command) {
    }
}
