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
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

/**
 * A single full-sidecar container standing in for one of the per-instance {@link SsStackSetup}
 * stacks (embedded PostgreSQL, embedded OpenBao, every service under supervisord). Selected for ss0
 * by {@code test-framework.ss0-stack=sidecar}; joins the shared {@code xroad-network} carrying every
 * alias the multi-container stack exports for that instance (UI, proxy, {@code xrd-<name>}), so hurl
 * and the test suite run unmodified against either shape.
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
public class SidecarSsStackSetup extends AbstractSsStack {

    private static final String SIDECAR = "sidecar";

    private static final String COMPOSE_SIDECAR_FILE = "compose.ss0-sidecar.e2e.yaml";
    private static final String XROAD_NETWORK = "xroad-network";

    private static final String POSTGRES_USER = "postgres";
    private static final String XROAD_USER = "xroad";
    private static final String MESSAGELOG_DB = "messagelog";
    private static final String MESSAGELOG_SEARCH_PATH = "messagelog,public";
    private static final String ARCHIVE_DIR = "/var/lib/xroad";
    private static final String ARCHIVER_LOG = "/var/log/xroad/message-log-archiver.log";
    private static final String ARCHIVER_CLI = "/usr/share/xroad/bin/xroad-message-log-archiver";
    private static final String ARCHIVE_SUCCESS_MARKER = "Archival operation completed successfully";
    private static final String CLEANUP_SUCCESS_MARKER = "Cleanup operation completed successfully";
    private static final String MESSAGELOG_ARCHIVES_FILE = "messagelog-archives.tar.gz";

    /**
     * The sidecar's measured cold start is around three minutes (embedded PostgreSQL and OpenBao
     * bootstrap plus every packaged service starting under supervisord, the dataspace control plane
     * alone taking over two), and a single early control-plane restart under load has been observed;
     * this leaves generous headroom above that instead of reusing the multi-container stack's
     * five-minute budget.
     */
    private static final Duration READINESS_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration READINESS_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration PORT_WAIT_TIMEOUT = Duration.ofMinutes(8);

    private final String name;

    public SidecarSsStackSetup(ApiTestCoreProperties coreProperties, String name) {
        super(coreProperties);
        this.name = name;
    }

    @Override
    protected String composeProjectName() {
        return name + "-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(), composeFile(COMPOSE_SIDECAR_FILE))
                .withEnv("XROAD_DSP_PARTICIPANT_CONTEXT_ID", "xrd-" + name)
                .withExposedService(SIDECAR, SsStackSetup.Port.PROXY,
                        forListeningPort().withStartupTimeout(PORT_WAIT_TIMEOUT))
                .withExposedService(SIDECAR, SsStackSetup.Port.PROXY_HEALTHCHECK,
                        forListeningPort().withStartupTimeout(PORT_WAIT_TIMEOUT))
                .withExposedService(SIDECAR, SsStackSetup.Port.UI,
                        forListeningPort().withStartupTimeout(PORT_WAIT_TIMEOUT))
                .withLogConsumer(SIDECAR, createLogConsumer(name, SIDECAR));
    }

    @Override
    protected void onPostStart() {
        connectToExternalNetwork();
    }

    /**
     * One sidecar container answers to every alias the multi-container stack spreads across its ui,
     * proxy, signer, configuration-client, ds-control-plane and ds-identity-hub services, plus the
     * {@code xrd-<name>} messaging alias the proxy alone carries there. The ds-control-plane and
     * ds-identity-hub aliases matter even though compose-mode DSP assertions self-skip: dataspace
     * asset-access acquisition is the actual wire mechanism message exchange and management requests
     * negotiate over, so a counterparty resolving those hostnames is required for any cross-SS call to
     * reach the sidecar, not only for DSP-specific test scenarios.
     */
    private void connectToExternalNetwork() {
        var containerState = env.getContainerByServiceName(SIDECAR).orElseThrow();
        var dockerClient = containerState.getDockerClient();

        String networkId = dockerClient.listNetworksCmd().exec().stream()
                .filter(n -> XROAD_NETWORK.equals(n.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find external network '%s'".formatted(XROAD_NETWORK)))
                .getId();

        var aliases = List.of(
                "%s-%s".formatted(name, SsStackSetup.UI),
                "%s-%s".formatted(name, SsStackSetup.PROXY),
                "%s-%s".formatted(name, SsStackSetup.SIGNER),
                "%s-%s".formatted(name, SsStackSetup.CONFIGURATION_CLIENT),
                "%s-%s".formatted(name, SsStackSetup.DS_CONTROL_PLANE),
                "%s-%s".formatted(name, SsStackSetup.DS_IDENTITY_HUB),
                "xrd-" + name);

        dockerClient.connectToNetworkCmd()
                .withContainerId(containerState.getContainerId())
                .withNetworkId(networkId)
                .withContainerNetwork(new ContainerNetwork().withAliases(aliases.toArray(String[]::new)))
                .exec();
    }

    /**
     * Blocks until the sidecar's proxy reports readiness, including OCSP status for the auth key.
     * Sized for the sidecar's slower cold start (see {@link #READINESS_TIMEOUT}) rather than the
     * multi-container stack's per-service boot time.
     */
    @Override
    public void awaitProxyReadiness() {
        var mapping = getContainerMapping(SsStackSetup.PROXY, SsStackSetup.Port.PROXY_HEALTHCHECK);
        var readinessUrl = "http://%s:%d/q/health/ready".formatted(mapping.host(), mapping.port());
        log.info("Waiting for {} sidecar proxy readiness at {}", name, readinessUrl);

        await()
                .atMost(READINESS_TIMEOUT)
                .pollInterval(READINESS_POLL_INTERVAL)
                .ignoreExceptions()
                .until(() -> {
                    var json = given().get(readinessUrl).jsonPath();
                    var overall = json.getString("status");
                    var authKeyOcsp = json.getString(
                            "checks.find { it.name == 'PROXY_AUTH_KEY_OCSP_READINESS_CHECK' }.data.status");
                    log.info("{} sidecar proxy readiness: status={}, authKeyOcsp={}", name, overall, authKeyOcsp);
                    return "UP".equals(overall) && "OK".equals(authKeyOcsp);
                });
    }

    /**
     * The one sidecar container answers to every service name the multi-container stack spreads
     * across ui/proxy/signer/configuration-client, so callers may pass any of those names; only the
     * port distinguishes which of the container's listeners they mean.
     */
    @Override
    public ContainerMapping getContainerMapping(String service, int originalPort) {
        return super.getContainerMapping(SIDECAR, originalPort);
    }

    /**
     * Runs a query against the embedded PostgreSQL's {@code messagelog} database as the {@code postgres}
     * user, mirroring {@link org.niis.xroad.e2e.LxdEnvSetup}'s single-host {@code psql} pattern in place
     * of a dedicated {@code db-messagelog} container. The search path is overridden because a native
     * package local install schemas the database after its own user, {@code messagelog}, not
     * {@code public}.
     */
    public String execMessagelogSql(String sql) {
        var result = execAsUserChecked(POSTGRES_USER, Map.of("PGOPTIONS", "--search_path=" + MESSAGELOG_SEARCH_PATH),
                "psql", "-d", MESSAGELOG_DB, "-tAX", "-c", sql);
        return result.getStdout().trim();
    }

    /**
     * Runs the message log archiver CLI as the {@code xroad} user, blocking until it completes. The CLI
     * always exits 0 regardless of outcome, so success is verified from its log file, exactly as
     * {@link org.niis.xroad.e2e.LxdEnvSetup} does against a native install.
     */
    public void triggerMessageLogCommand(String command) {
        var commandArgs = new ArrayList<>(List.of(ARCHIVER_CLI));
        commandArgs.addAll(List.of(command.trim().split("\\s+")));
        var result = execAsUser(XROAD_USER, commandArgs.toArray(String[]::new));

        var successMarker = command.startsWith("archive") ? ARCHIVE_SUCCESS_MARKER : CLEANUP_SUCCESS_MARKER;
        var logTail = tailArchiverLog();
        if (result.getExitCode() != 0 || !logTail.contains(successMarker)) {
            throw new IllegalStateException(
                    "message log %s on sidecar %s did not report success (exit %d); stderr: %s; stdout: %s; log tail:%n%s"
                            .formatted(command, name, result.getExitCode(), result.getStderr(), result.getStdout(), logTail));
        }
    }

    private String tailArchiverLog() {
        return exec("tail", "-n", "50", ARCHIVER_LOG).getStdout();
    }

    /**
     * Packages every produced {@code mlog-*} archive file into a tarball and downloads it, mirroring
     * {@link org.niis.xroad.e2e.LxdEnvSetup}.
     */
    public void downloadMessageLogArchives(String localDir) {
        downloadTarball("cd %s && find . -maxdepth 1 -type f -name 'mlog-*' | tar czf %s -T -", ARCHIVE_DIR, localDir);
    }

    /**
     * Decrypts every archive file under the sidecar's message log archive directory whose name starts
     * with {@code filePrefix}, following the same steps as {@link org.niis.xroad.e2e.LxdEnvSetup}. Message
     * log encryption is an ss1-only feature, so on ss0 no file ever matches {@code filePrefix*.gpg} and
     * this returns 0 with an empty tarball — the unencrypted path the interface still has to satisfy.
     */
    @SneakyThrows
    public int decryptArchives(String filePrefix, String keyId, String passphrase, String outputDir) {
        var keyFile = Path.of(coreProperties.resourceDir() + "gpg_keys/" + keyId + ".asc");
        var workDir = "/tmp/decrypt-" + UUID.randomUUID();
        var gnupgHome = workDir + "/gnupg";
        var decryptedDir = workDir + "/out";
        var keyFileInContainer = workDir + "/key.asc";

        try {
            var listResult = execChecked("find", ARCHIVE_DIR, "-maxdepth", "1", "-type", "f", "-name", filePrefix + "*.gpg");
            var remoteFiles = listResult.getStdout().lines().filter(line -> !line.isBlank()).toList();

            execChecked("mkdir", "-p", "-m", "700", gnupgHome);
            execChecked("mkdir", "-p", decryptedDir);
            containerState().copyFileToContainer(MountableFile.forHostPath(keyFile), keyFileInContainer);
            execChecked("gpg", "--homedir", gnupgHome, "--batch", "--yes", "--import", keyFileInContainer);

            for (var remoteFile : remoteFiles) {
                decryptOne(gnupgHome, remoteFile, passphrase, decryptedDir);
            }

            downloadTarball("cd %s && tar czf %s .", decryptedDir, outputDir);
            return remoteFiles.size();
        } finally {
            exec("rm", "-rf", workDir);
        }
    }

    /**
     * Decrypts a single archive in place; gpg's exit code cannot be trusted (these fixtures are signed by
     * a key the recipient keyring doesn't hold), so success is judged by the output file existing and
     * being non-empty, matching {@link org.niis.xroad.e2e.LxdEnvSetup}.
     */
    private void decryptOne(String gnupgHome, String remoteFile, String passphrase, String decryptedDir) {
        var outFileName = remoteFile.substring(remoteFile.lastIndexOf('/') + 1).replaceFirst("\\.gpg$", "");
        var outPath = decryptedDir + "/" + outFileName;

        var decryptResult = exec("gpg", "--homedir", gnupgHome, "--batch", "--no-tty", "--pinentry-mode", "loopback",
                "--passphrase", passphrase, "--output", outPath, "--decrypt", remoteFile);
        var sizeCheck = exec("test", "-s", outPath);
        if (sizeCheck.getExitCode() != 0) {
            log.warn("Decryption of {} in sidecar {} did not produce output (gpg exit {}): {}",
                    remoteFile, name, decryptResult.getExitCode(), decryptResult.getStderr());
            exec("rm", "-f", outPath);
        }
    }

    @SneakyThrows
    private void downloadTarball(String tarCommandFormat, String remoteDir, String localDir) {
        Files.createDirectories(Path.of(localDir));
        var remoteTarball = "/tmp/" + MESSAGELOG_ARCHIVES_FILE.replace(".tar.gz", "-" + UUID.randomUUID() + ".tar.gz");
        execChecked("sh", "-c", tarCommandFormat.formatted(remoteDir, remoteTarball));
        execChecked("chmod", "0644", remoteTarball);
        copyFileFromContainer(SIDECAR, remoteTarball, localDir + "/" + MESSAGELOG_ARCHIVES_FILE);
        execChecked("rm", "-f", remoteTarball);
    }

    /**
     * Execs in the container's default (root) context. The single-container replacement for
     * {@link org.niis.xroad.e2e.LxdEnvSetup}'s {@code lxc exec}.
     */
    @SneakyThrows
    private Container.ExecResult exec(String... command) {
        return containerState().execInContainer(command);
    }

    private Container.ExecResult execChecked(String... command) {
        var result = exec(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("%s in sidecar %s failed (exit %d): %s"
                    .formatted(List.of(command), name, result.getExitCode(), result.getStderr()));
        }
        return result;
    }

    /**
     * Execs as the given user via docker {@code exec}'s native user-switching support, in place of
     * {@link org.niis.xroad.e2e.LxdEnvSetup}'s {@code sudo -u} — the single-container replacement for
     * dedicated per-role containers ({@code db-messagelog} runs as {@code postgres},
     * {@code message-log-cli} as its packaged user).
     */
    private Container.ExecResult execAsUser(String user, String... command) {
        return execAsUser(user, Map.of(), command);
    }

    @SneakyThrows
    private Container.ExecResult execAsUser(String user, Map<String, String> envVars, String... command) {
        var config = ExecConfig.builder().command(command).envVars(envVars).user(user).build();
        return containerState().execInContainer(config);
    }

    private Container.ExecResult execAsUserChecked(String user, Map<String, String> envVars, String... command) {
        var result = execAsUser(user, envVars, command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("%s as %s in sidecar %s failed (exit %d): %s"
                    .formatted(List.of(command), user, name, result.getExitCode(), result.getStderr()));
        }
        return result;
    }

    private ContainerState containerState() {
        return env.getContainerByServiceName(SIDECAR).orElseThrow();
    }

    private Slf4jLogConsumer createLogConsumer(String envName, String containerName) {
        return createLogConsumer("%s-%s".formatted(envName, containerName));
    }

    private File composeFile(String fileName) {
        return new File(coreProperties.resourceDir() + fileName);
    }
}
