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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.Container;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Kubernetes-based implementation of the e2e environment: two Security Server chart releases
 * (ss0/ss1) in separate namespaces. Assumes the environment is already provisioned by
 * {@code core/scripts/env-k8s} and reachable via its {@code kubectl port-forward} listeners, so
 * bring-up and teardown are no-ops. Extends {@link BaseComposeSetup} only to satisfy the
 * api-test-core session-listener/extension lifecycle; the inherited compose accessors are
 * overridden to fail loudly — test code must go through {@link E2eEnvironment} and the ops
 * interfaces. The messagelog transports are the k8s analogue of {@link LxdEnvSetup}'s
 * {@code lxc exec}: they shell out to {@code kubectl exec}/{@code kubectl logs} against the
 * per-SS namespace instead.
 */
@Slf4j
public class K8sEnvSetup extends BaseComposeSetup
        implements E2eEnvironment, MessagelogDbOps, MessagelogArchiveOps, DsControlPlaneDbOps {

    private static final int PROBE_TIMEOUT_MS = 5000;

    private static final String MESSAGELOG_CLUSTER = "db-messagelog";
    private static final String MESSAGELOG_PRIMARY_SELECTOR = "cnpg.io/cluster=" + MESSAGELOG_CLUSTER
            + ",cnpg.io/instanceRole=primary";
    private static final String DS_CONTROL_PLANE_DB = "ds-control-plane";
    private static final String DS_CONTROL_PLANE_CLUSTER = "db-ds-control-plane";
    private static final String DS_CONTROL_PLANE_PRIMARY_SELECTOR = "cnpg.io/cluster=" + DS_CONTROL_PLANE_CLUSTER
            + ",cnpg.io/instanceRole=primary";
    private static final String AUXILIARY_SERVICE_WORKLOAD = "deploy/auxiliary-service";
    private static final String ARCHIVE_DIR = "/var/lib/xroad/messagelog-archives";
    private static final String ARCHIVER_SCRIPT = "/usr/share/xroad/scripts/containerised/message_log_archiver.sh";
    private static final String ARCHIVE_SUCCESS_MARKER = "Archival operation completed successfully";
    private static final String CLEANUP_SUCCESS_MARKER = "Cleanup operation completed successfully";
    private static final Pattern JOB_NAME_PATTERN = Pattern.compile("Creating Kubernetes Job '([^']+)'");

    private final K8sEnvProperties k8sProperties;

    public K8sEnvSetup(K8sEnvProperties k8sProperties, ApiTestCoreProperties coreProperties) {
        super(coreProperties);
        this.k8sProperties = k8sProperties;
    }

    @Override
    public void start() {
        log.info("Using pre-provisioned k8s environment (ss0={}:{}, ss1={}:{}); bring-up is externally managed by "
                        + "core/scripts/env-k8s",
                k8sProperties.ss0Host(), k8sProperties.ss0ProxyPort(),
                k8sProperties.ss1Host(), k8sProperties.ss1ProxyPort());
    }

    @Override
    public void stop() {
        log.info("Leaving k8s environment running; teardown is externally managed by core/scripts/env-k8s");
    }

    @Override
    public ContainerMapping getContainerMapping(String env, String service, int port) {
        return new ContainerMapping(resolveHost(env), resolvePort(env, service, port));
    }

    @Override
    public boolean isInitialized() {
        return probePort(k8sProperties.ss0Host(), k8sProperties.ss0ProxyPort())
                && probePort(k8sProperties.ss1Host(), k8sProperties.ss1ProxyPort());
    }

    /**
     * The address the Security Server identifies itself by in global configuration and monitoring
     * data, which in this topology is its namespace-qualified in-cluster proxy Service. This is
     * deliberately not {@link #resolveHost(String)} — that returns the port-forward host the test
     * client dials, which coincides with the registered address in compose and LXD but not here.
     */
    @Override
    public String securityServerAddress(String env) {
        return "proxy." + resolveNamespace(env);
    }

    @Override
    @SneakyThrows
    public String execMessagelogSql(String env, String sql) {
        var namespace = resolveNamespace(env);
        var pod = resolvePrimaryPod(namespace, MESSAGELOG_PRIMARY_SELECTOR, MESSAGELOG_CLUSTER);
        return execPsql(namespace, pod, "messagelog", sql);
    }

    @Override
    @SneakyThrows
    public String execDsControlPlaneSql(String env, String sql) {
        var namespace = resolveNamespace(env);
        var pod = resolvePrimaryPod(namespace, DS_CONTROL_PLANE_PRIMARY_SELECTOR, DS_CONTROL_PLANE_CLUSTER);
        return execPsql(namespace, pod, DS_CONTROL_PLANE_DB, sql);
    }

    /**
     * Runs a query against a CNPG-managed database's primary pod via {@code kubectl exec}, mirroring
     * {@link LxdEnvSetup}'s {@code lxc exec} equivalent.
     */
    @SneakyThrows
    private String execPsql(String namespace, String pod, String database, String sql) {
        var process = new ProcessBuilder(
                k8sProperties.kubectlCommand(), "-n", namespace, "exec", pod, "-c", "postgres", "--",
                "psql", "-U", "postgres", "-d", database, "-tAX", "-c", sql)
                .start();

        // Stdout and stderr are drained concurrently to avoid deadlocking on a full pipe buffer,
        // mirroring LxdEnvSetup.execMessagelogSql.
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("psql query on %s failed (exit %d): %s".formatted(database, exitCode, stderr));
        }
        return stdout.trim();
    }

    @Override
    @SneakyThrows
    public void triggerMessageLogCommand(String env, String command) {
        var namespace = resolveNamespace(env);
        var commandArgs = new ArrayList<String>(List.of(
                k8sProperties.kubectlCommand(), "-n", namespace, "exec", AUXILIARY_SERVICE_WORKLOAD, "--",
                ARCHIVER_SCRIPT));
        commandArgs.addAll(List.of(command.trim().split("\\s+")));

        var process = new ProcessBuilder(commandArgs).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();

        var jobNameMatcher = JOB_NAME_PATTERN.matcher(stdout);
        if (exitCode != 0 || !jobNameMatcher.find()) {
            throw new IllegalStateException(
                    "message log %s on %s did not create a Job (exit %d); stderr: %s; stdout: %s"
                            .formatted(command, env, exitCode, stderr, stdout));
        }
        var jobName = jobNameMatcher.group(1);

        // The script already exits non-zero when the Job fails; the success marker in the Job
        // pod's log is an independent check that the requested operation actually completed
        // (the containerized Quarkus profile logs to stdout, unlike %native which redirects
        // to a file).
        var successMarker = command.startsWith("archive") ? ARCHIVE_SUCCESS_MARKER : CLEANUP_SUCCESS_MARKER;
        var jobLog = tailJobLog(namespace, jobName);
        if (!jobLog.contains(successMarker)) {
            throw new IllegalStateException(
                    "message log %s on %s did not report success; Job '%s' log tail:\n%s"
                            .formatted(command, env, jobName, jobLog));
        }
    }

    @SneakyThrows
    private String tailJobLog(String namespace, String jobName) {
        var process = new ProcessBuilder(
                k8sProperties.kubectlCommand(), "-n", namespace, "logs", "job/" + jobName, "--tail=50")
                .start();
        var stdout = readAll(process.getInputStream());
        process.waitFor();
        return stdout;
    }

    @Override
    @SneakyThrows
    public void downloadMessageLogArchives(String env, String localDir) {
        downloadArchivesTarball(resolveNamespace(env), ARCHIVE_DIR, localDir);
    }

    @Override
    @SneakyThrows
    public int decryptArchives(String env, String filePrefix, String keyId, String passphrase, String outputDir) {
        var namespace = resolveNamespace(env);
        var keyFile = Path.of(coreProperties.resourceDir() + "gpg_keys/" + keyId + ".asc");
        var workDir = "/tmp/decrypt-" + UUID.randomUUID();
        var gnupgHome = workDir + "/gnupg";
        var decryptedDir = workDir + "/out";

        try {
            // Decryption happens inside the auxiliary-service pod, using the image's own gpg
            // (2.4.8), rather than on the test host: the fixture keys carry no encryption
            // capability, which gpg 2.4.x tolerates and 2.5+ refuses, so a host-side decrypt makes
            // the outcome depend on the host's gpg version. Only the recipient private key ever
            // leaves the test host, piped in over `kubectl exec -i` stdin; nothing is written to
            // an image, chart, ConfigMap or Secret.
            var listResult = execInAuxiliaryServiceChecked(namespace,
                    "find", ARCHIVE_DIR, "-maxdepth", "1", "-type", "f", "-name", filePrefix + "*.gpg");
            var remoteFiles = listResult.stdout().lines().filter(line -> !line.isBlank()).toList();

            execInAuxiliaryServiceChecked(namespace, "mkdir", "-p", "-m", "700", gnupgHome);
            execInAuxiliaryServiceChecked(namespace, "mkdir", "-p", decryptedDir);
            importKey(namespace, gnupgHome, keyFile);

            for (var remoteFile : remoteFiles) {
                decryptOneInPod(namespace, gnupgHome, remoteFile, passphrase, decryptedDir);
            }

            downloadDecryptedTarball(namespace, decryptedDir, outputDir);
            return remoteFiles.size();
        } finally {
            execInAuxiliaryService(namespace, "rm", "-rf", workDir);
        }
    }

    @SneakyThrows
    private void downloadArchivesTarball(String namespace, String remoteDir, String localDir) {
        Files.createDirectories(Path.of(localDir));
        var remoteTarball = "/tmp/messagelog-archives-" + UUID.randomUUID() + ".tar.gz";

        // Native SS hosts have other content under /var/lib/xroad (a "backup" directory, dotfiles)
        // that the k8s archive-only mount never has; only mlog-* archive files belong in the
        // tarball the archive assertions later read entry-by-entry.
        run(k8sProperties.kubectlCommand(), "-n", namespace, "exec", AUXILIARY_SERVICE_WORKLOAD, "--",
                "sh", "-c", "cd %s && find . -maxdepth 1 -type f -name 'mlog-*' | tar czf %s -T -"
                        .formatted(remoteDir, remoteTarball));
        downloadFile(namespace, remoteTarball, Path.of(localDir, "messagelog-archives.tar.gz"));
        run(k8sProperties.kubectlCommand(), "-n", namespace, "exec", AUXILIARY_SERVICE_WORKLOAD, "--",
                "rm", "-f", remoteTarball);
    }

    /**
     * Streams a single remote file out of the auxiliary-service container via {@code kubectl exec ... cat},
     * rather than {@code kubectl cp} — that avoids resolving the Deployment's backing pod name (which
     * {@code cp}, unlike {@code exec}/{@code logs}, cannot do on its own) at the cost of requiring only
     * {@code cat} in the target container.
     */
    @SneakyThrows
    private void downloadFile(String namespace, String remotePath, Path localPath) {
        var process = new ProcessBuilder(
                k8sProperties.kubectlCommand(), "-n", namespace, "exec", AUXILIARY_SERVICE_WORKLOAD, "--",
                "cat", remotePath)
                .redirectOutput(localPath.toFile())
                .start();
        var stderr = readAll(process.getErrorStream());
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Downloading %s from %s failed (exit %d): %s"
                    .formatted(remotePath, namespace, exitCode, stderr));
        }
    }

    /**
     * Tars up the pod's decrypted-files directory and downloads it, mirroring
     * {@link #downloadArchivesTarball}. An empty {@code remoteDecryptedDir} still yields a
     * readable tarball containing only its own directory entry, so a wrong key/passphrase
     * produces zero file entries rather than a missing or broken tarball.
     */
    @SneakyThrows
    private void downloadDecryptedTarball(String namespace, String remoteDecryptedDir, String localDir) {
        Files.createDirectories(Path.of(localDir));
        var remoteTarball = "/tmp/messagelog-archives-" + UUID.randomUUID() + ".tar.gz";
        execInAuxiliaryServiceChecked(namespace, "sh", "-c",
                "cd %s && tar czf %s .".formatted(remoteDecryptedDir, remoteTarball));
        downloadFile(namespace, remoteTarball, Path.of(localDir, "messagelog-archives.tar.gz"));
        execInAuxiliaryService(namespace, "rm", "-f", remoteTarball);
    }

    /**
     * Imports the recipient private key into a gpg homedir inside the auxiliary-service pod,
     * piping the key material in over {@code kubectl exec -i} stdin so it never touches an
     * intermediate file, image, chart, ConfigMap or Secret in the cluster.
     */
    @SneakyThrows
    private void importKey(String namespace, String gnupgHome, Path keyFile) {
        var commandArgs = new ArrayList<>(List.of(
                k8sProperties.kubectlCommand(), "-n", namespace, "exec", "-i", AUXILIARY_SERVICE_WORKLOAD, "--",
                "gpg", "--homedir", gnupgHome, "--batch", "--yes", "--import", "-"));
        var process = new ProcessBuilder(commandArgs).redirectInput(keyFile.toFile()).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Importing key %s into %s failed (exit %d): %s%s"
                    .formatted(keyFile, namespace, exitCode, stdout, stderr));
        }
    }

    /**
     * Decrypts a single archive in place inside the pod. gpg's exit code cannot be trusted here:
     * these archives are signed by a key the fixture keyring doesn't hold, so gpg reports
     * "Can't check signature: No public key" and exits non-zero even after writing a complete,
     * correctly decrypted output file. A wrong key/passphrase, by contrast, never writes the
     * output file at all. So success is judged by the output file existing and being non-empty
     * ({@code test -s}), not by gpg's exit status; a failed decrypt is logged at WARN with gpg's
     * stderr and leaves no (or an empty) file behind, keeping it out of the resulting tarball.
     */
    @SneakyThrows
    private void decryptOneInPod(String namespace, String gnupgHome, String remoteFile, String passphrase, String decryptedDir) {
        var outFileName = remoteFile.substring(remoteFile.lastIndexOf('/') + 1).replaceFirst("\\.gpg$", "");
        var outPath = decryptedDir + "/" + outFileName;

        var decryptResult = execInAuxiliaryService(namespace,
                "gpg", "--homedir", gnupgHome, "--batch", "--no-tty", "--pinentry-mode", "loopback",
                "--passphrase", passphrase, "--output", outPath, "--decrypt", remoteFile);
        var sizeCheck = execInAuxiliaryService(namespace, "test", "-s", outPath);
        if (sizeCheck.exitCode() != 0) {
            log.warn("Decryption of {} in {} did not produce output (gpg exit {}): {}",
                    remoteFile, namespace, decryptResult.exitCode(), decryptResult.stderr());
            execInAuxiliaryService(namespace, "rm", "-f", outPath);
        }
    }

    private record KubectlExecResult(String stdout, String stderr, int exitCode) {
    }

    @SneakyThrows
    private KubectlExecResult execInAuxiliaryService(String namespace, String... command) {
        var commandArgs = new ArrayList<>(List.of(
                k8sProperties.kubectlCommand(), "-n", namespace, "exec", AUXILIARY_SERVICE_WORKLOAD, "--"));
        commandArgs.addAll(List.of(command));
        var process = new ProcessBuilder(commandArgs).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();
        return new KubectlExecResult(stdout, stderr, exitCode);
    }

    private KubectlExecResult execInAuxiliaryServiceChecked(String namespace, String... command) {
        var result = execInAuxiliaryService(namespace, command);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("%s in %s failed (exit %d): %s"
                    .formatted(List.of(command), namespace, result.exitCode(), result.stderr()));
        }
        return result;
    }

    @SneakyThrows
    private void run(String... command) {
        var process = new ProcessBuilder(command).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("%s failed (exit %d): %s%s"
                    .formatted(List.of(command), exitCode, stdout, stderr));
        }
    }

    @SneakyThrows
    private static String readAll(InputStream inputStream) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    @SneakyThrows
    private String resolvePrimaryPod(String namespace, String labelSelector, String clusterName) {
        var process = new ProcessBuilder(
                k8sProperties.kubectlCommand(), "-n", namespace, "get", "pod",
                "-l", labelSelector,
                "-o", "jsonpath={.items[0].metadata.name}")
                .start();
        var stdout = readAll(process.getInputStream());
        var stderr = readAll(process.getErrorStream());
        var exitCode = process.waitFor();
        if (exitCode != 0 || stdout.isBlank()) {
            throw new IllegalStateException(
                    "Could not resolve the primary %s pod in namespace %s (exit %d): %s"
                            .formatted(clusterName, namespace, exitCode, stderr));
        }
        return stdout;
    }

    private String resolveNamespace(String env) {
        return switch (env) {
            case "ss0" -> k8sProperties.ss0Namespace();
            case "ss1" -> k8sProperties.ss1Namespace();
            default -> throw new IllegalArgumentException("Unknown k8s environment: " + env);
        };
    }

    private String resolveHost(String env) {
        return switch (env) {
            case "ss0" -> k8sProperties.ss0Host();
            case "ss1" -> k8sProperties.ss1Host();
            default -> throw new IllegalArgumentException("Unknown k8s environment: " + env);
        };
    }

    /**
     * Both SS's are reached via kubectl port-forward on {@code localhost}, so — unlike LXD's
     * distinct hostnames — ss0/ss1 need distinct local ports per service; the {@code port}
     * argument (the service's canonical in-cluster port, e.g. {@link SsStackSetup.Port#PROXY})
     * is only used as a fallback for services this adapter has no dedicated forwarded port for.
     */
    private int resolvePort(String env, String service, int port) {
        return switch (env) {
            case "ss0" -> switch (service) {
                case SsStackSetup.PROXY -> k8sProperties.ss0ProxyPort();
                case SsStackSetup.UI -> k8sProperties.ss0UiPort();
                default -> port;
            };
            case "ss1" -> switch (service) {
                case SsStackSetup.PROXY -> k8sProperties.ss1ProxyPort();
                case SsStackSetup.UI -> k8sProperties.ss1UiPort();
                default -> port;
            };
            default -> throw new IllegalArgumentException("Unknown k8s environment: " + env);
        };
    }

    private boolean probePort(String host, int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            log.debug("Probe {}:{} reachable", host, port);
            return true;
        } catch (IOException e) {
            log.warn("Probe {}:{} unreachable: {}", host, port, e.getMessage());
            return false;
        }
    }

    @Override
    protected String composeProjectName() {
        throw new UnsupportedOperationException("K8sEnvSetup does not manage a compose stack");
    }

    @Override
    public ContainerMapping getContainerMapping(String service, int originalPort) {
        throw new UnsupportedOperationException("Use getContainerMapping(env, service, port); there is no compose stack");
    }

    @Override
    public Container.ExecResult execInContainer(String container, String... command) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }

    @Override
    public void restartService(String containerName) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }

    @Override
    public void copyFilesToContainer(String containerName, String classpathResource, String targetPath) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }

    @Override
    public void copyFileFromContainer(String containerName, String containerPath, String localPath) {
        throw new UnsupportedOperationException("Not supported on the k8s adapter");
    }
}
