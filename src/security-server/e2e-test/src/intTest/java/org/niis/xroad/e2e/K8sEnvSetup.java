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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Kubernetes-based implementation of the e2e environment: two Security Server chart releases
 * (ss0/ss1) in separate namespaces (PRD .workbench/20260730-k8s-e2e-variant/PRD.md, slice 06:
 * two-ss-message-flow). Assumes the environment is already provisioned by
 * {@code core/scripts/env-k8s} and reachable via its {@code kubectl port-forward} listeners, so
 * bring-up and teardown are no-ops. Extends {@link BaseComposeSetup} only to satisfy the
 * api-test-core session-listener/extension lifecycle; the inherited compose accessors are
 * overridden to fail loudly — test code must go through {@link E2eEnvironment} and the ops
 * interfaces. The messagelog transports are the k8s analogue of {@link LxdEnvSetup}'s
 * {@code lxc exec}: they shell out to {@code kubectl exec}/{@code kubectl logs} against the
 * per-SS namespace instead.
 */
@Slf4j
public class K8sEnvSetup extends BaseComposeSetup implements E2eEnvironment, MessagelogDbOps, MessagelogArchiveOps {

    private static final int PROBE_TIMEOUT_MS = 5000;

    private static final String MESSAGELOG_CLUSTER = "db-messagelog";
    private static final String MESSAGELOG_PRIMARY_SELECTOR = "cnpg.io/cluster=" + MESSAGELOG_CLUSTER
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

    @Override
    public String securityServerAddress(String env) {
        return resolveHost(env);
    }

    @Override
    @SneakyThrows
    public String execMessagelogSql(String env, String sql) {
        var namespace = resolveNamespace(env);
        var pod = resolvePrimaryMessagelogPod(namespace);
        var process = new ProcessBuilder(
                k8sProperties.kubectlCommand(), "-n", namespace, "exec", pod, "-c", "postgres", "--",
                "psql", "-U", "postgres", "-d", "messagelog", "-tAX", "-c", sql)
                .start();

        // Stdout and stderr are drained concurrently to avoid deadlocking on a full pipe buffer,
        // mirroring LxdEnvSetup.execMessagelogSql.
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("psql query on %s failed (exit %d): %s".formatted(env, exitCode, stderr));
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

        // As with the LXD CLI, the archiver Job's container always exits 0 regardless of outcome
        // (MessageLogArchiverService catches and logs internally) — message_log_archiver.sh's own
        // "Job completed successfully" only reflects Job-controller completion, so the real outcome
        // is read from the Job pod's own log output (the containerized Quarkus profile logs to
        // stdout, unlike %native which redirects to a file).
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
        var keyFile = coreProperties.resourceDir() + "gpg_keys/" + keyId + ".asc";
        var downloadedDir = Files.createTempDirectory("k8s-encrypted-");
        var decryptedDir = Files.createTempDirectory("k8s-decrypted-");

        try {
            // Mirrors LxdEnvSetup.decryptArchives: operates directly on the archive directory in
            // the cluster, not on a prior local download — the decrypt scenarios never download
            // archives first.
            var listProcess = new ProcessBuilder(
                    k8sProperties.kubectlCommand(), "-n", namespace, "exec", AUXILIARY_SERVICE_WORKLOAD, "--",
                    "find", ARCHIVE_DIR, "-maxdepth", "1", "-type", "f", "-name", filePrefix + "*.gpg")
                    .start();
            var fileList = readAll(listProcess.getInputStream());
            var listError = readAll(listProcess.getErrorStream());
            var listExit = listProcess.waitFor();
            if (listExit != 0) {
                throw new IllegalStateException("Listing %s*.gpg archives in %s failed (exit %d): %s"
                        .formatted(filePrefix, namespace, listExit, listError));
            }
            var remoteFiles = fileList.lines().filter(line -> !line.isBlank()).toList();

            for (var remoteFile : remoteFiles) {
                var fileName = remoteFile.substring(remoteFile.lastIndexOf('/') + 1);
                downloadFile(namespace, remoteFile, downloadedDir.resolve(fileName));
            }

            List<Path> downloadedFiles;
            try (Stream<Path> paths = Files.list(downloadedDir)) {
                downloadedFiles = paths.toList();
            }
            for (var file : downloadedFiles) {
                decryptOne(file, keyFile, passphrase, decryptedDir);
            }
            packageAsTarball(decryptedDir, outputDir);
            return remoteFiles.size();
        } finally {
            deleteRecursively(downloadedDir);
            deleteRecursively(decryptedDir);
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

    @SneakyThrows
    private void packageAsTarball(Path sourceDir, String outputDir) {
        Files.createDirectories(Path.of(outputDir));
        // COPYFILE_DISABLE suppresses BSD tar's AppleDouble ._* metadata entries when this runs on
        // macOS (harmless/absent on the ubuntu-24.04 CI runner's GNU tar), which would otherwise
        // inflate the entry count the archive assertions and the decrypt count checks read back
        // out of this tarball.
        var processBuilder = new ProcessBuilder("tar", "czf", outputDir + "/messagelog-archives.tar.gz", "-C", sourceDir.toString(), ".");
        processBuilder.environment().put("COPYFILE_DISABLE", "1");
        var process = processBuilder.start();
        var stdout = readAll(process.getInputStream());
        var stderr = readAll(process.getErrorStream());
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("tar failed (exit %d): %s%s".formatted(exitCode, stdout, stderr));
        }
    }

    @SneakyThrows
    private void decryptOne(Path encryptedFile, String keyFile, String passphrase, Path outputDir) {
        var gnupgHome = Files.createTempDirectory("k8s-gpg-");
        try {
            Files.setPosixFilePermissions(gnupgHome, PosixFilePermissions.fromString("rwx------"));

            runGpg(gnupgHome, "--batch", "--yes", "--import", keyFile);

            var outFileName = encryptedFile.getFileName().toString().replaceFirst("\\.gpg$", "");
            var outPath = outputDir.resolve(outFileName);
            // Errors are ignored per file, mirroring LxdEnvSetup/decrypt-archives.sh — a wrong
            // key/passphrase fails individual files without aborting the batch.
            try {
                runGpg(gnupgHome, "--batch", "--no-tty", "--pinentry-mode", "loopback",
                        "--passphrase", passphrase, "--output", outPath.toString(),
                        "--decrypt", encryptedFile.toString());
            } catch (IllegalStateException e) {
                log.debug("Decryption of {} with the given key failed (expected for wrong-key scenarios): {}",
                        encryptedFile, e.getMessage());
            }
        } finally {
            deleteRecursively(gnupgHome);
        }
    }

    @SneakyThrows
    private void runGpg(Path gnupgHome, String... args) {
        var commandArgs = new ArrayList<>(List.of("gpg", "--homedir", gnupgHome.toString()));
        commandArgs.addAll(List.of(args));
        run(commandArgs.toArray(String[]::new));
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
    private void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete {}: {}", p, e.getMessage());
                }
            });
        }
    }

    @SneakyThrows
    private static String readAll(InputStream inputStream) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    @SneakyThrows
    private String resolvePrimaryMessagelogPod(String namespace) {
        var process = new ProcessBuilder(
                k8sProperties.kubectlCommand(), "-n", namespace, "get", "pod",
                "-l", MESSAGELOG_PRIMARY_SELECTOR,
                "-o", "jsonpath={.items[0].metadata.name}")
                .start();
        var stdout = readAll(process.getInputStream());
        var stderr = readAll(process.getErrorStream());
        var exitCode = process.waitFor();
        if (exitCode != 0 || stdout.isBlank()) {
            throw new IllegalStateException(
                    "Could not resolve the primary %s pod in namespace %s (exit %d): %s"
                            .formatted(MESSAGELOG_CLUSTER, namespace, exitCode, stderr));
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
