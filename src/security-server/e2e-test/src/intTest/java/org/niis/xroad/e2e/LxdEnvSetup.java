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

/**
 * LXD-based implementation of the e2e environment. Assumes the environment is already provisioned
 * and bootstrapped by Ansible, so bring-up and teardown are no-ops. Extends {@link BaseComposeSetup}
 * only to satisfy the api-test-core session-listener/extension lifecycle; the inherited compose
 * accessors are overridden to fail loudly — test code must go through {@link E2eEnvironment} and the
 * ops interfaces.
 */
@Slf4j
public class LxdEnvSetup extends BaseComposeSetup
        implements E2eEnvironment, MessagelogDbOps, MessagelogArchiveOps, DsControlPlaneDbOps {

    private static final int PROBE_TIMEOUT_MS = 5000;
    private static final String MESSAGELOG_SEARCH_PATH = "--search_path=messagelog,public";
    private static final String DS_CONTROL_PLANE_DB = "ds-control-plane";
    private static final String ARCHIVE_DIR = "/var/lib/xroad";
    private static final String ARCHIVER_LOG = "/var/log/xroad/message-log-archiver.log";
    private static final String ARCHIVER_CLI = "/usr/share/xroad/bin/xroad-message-log-archiver";
    private static final String ARCHIVE_SUCCESS_MARKER = "Archival operation completed successfully";
    private static final String CLEANUP_SUCCESS_MARKER = "Cleanup operation completed successfully";

    private final LxdEnvProperties lxdProperties;

    public LxdEnvSetup(LxdEnvProperties lxdProperties, ApiTestCoreProperties coreProperties) {
        super(coreProperties);
        this.lxdProperties = lxdProperties;
    }

    @Override
    public void start() {
        log.info("Using pre-provisioned LXD environment (ss0={}, ss1={}); bring-up is externally managed",
                lxdProperties.ss0Host(), lxdProperties.ss1Host());
    }

    @Override
    public void stop() {
        log.info("Leaving LXD environment running; teardown is externally managed");
    }

    @Override
    public ContainerMapping getContainerMapping(String env, String service, int port) {
        String host = resolveHost(env);
        return new ContainerMapping(host, port);
    }

    @Override
    public boolean isInitialized() {
        return probePort(lxdProperties.ss0Host(), lxdProperties.proxyPort())
                && probePort(lxdProperties.ss1Host(), lxdProperties.proxyPort());
    }

    @Override
    public String securityServerAddress(String env) {
        return resolveHost(env);
    }

    @Override
    @SneakyThrows
    public String execMessagelogSql(String env, String sql) {
        return execPsql(env, "messagelog", sql, "PGOPTIONS=" + MESSAGELOG_SEARCH_PATH);
    }

    @Override
    @SneakyThrows
    public String execDsControlPlaneSql(String env, String sql) {
        return execPsql(env, DS_CONTROL_PLANE_DB, sql);
    }

    /**
     * Runs a query against the given database's local postgres instance on the {@code env} container
     * via {@code lxc exec}, mirroring {@link K8sEnvSetup}'s {@code kubectl exec} equivalent.
     * {@code extraEnv} carries any {@code KEY=value} pairs the {@code sudo} invocation should forward
     * (e.g. messagelog's non-default search path); ds-control-plane needs none, since EDC's schema
     * lives in the default {@code public} schema.
     */
    @SneakyThrows
    private String execPsql(String env, String database, String sql, String... extraEnv) {
        var container = "xrd-" + env;
        var commandArgs = new ArrayList<String>(List.of(
                lxdProperties.lxcCommand(), "exec", container, "--",
                "sudo", "-u", "postgres"));
        commandArgs.addAll(List.of(extraEnv));
        commandArgs.addAll(List.of("psql", "-d", database, "-tAX", "-c", sql));

        var process = new ProcessBuilder(commandArgs).start();

        // Stdout and stderr are drained concurrently to avoid deadlocking on a full pipe buffer:
        // lxc exec emits sudoers noise on stderr on every call, which must not be mistaken for failure.
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
        var container = "xrd-" + env;
        var commandArgs = new ArrayList<String>(List.of(
                lxdProperties.lxcCommand(), "exec", container, "--",
                "sudo", "-u", "xroad", ARCHIVER_CLI));
        commandArgs.addAll(List.of(command.trim().split("\\s+")));

        var process = new ProcessBuilder(commandArgs).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();

        // The CLI exits non-zero on an operation failure, but its output goes to the log file —
        // the %native Quarkus profile disables console logging in favor of
        // /var/log/xroad/message-log-archiver.log — so the success marker is verified there and
        // the log tail carries an operation failure's cause. Only a startup-level failure
        // (JVM/config/connection) surfaces on the process streams.
        var successMarker = command.startsWith("archive") ? ARCHIVE_SUCCESS_MARKER : CLEANUP_SUCCESS_MARKER;
        var logTail = tailArchiverLog(env);
        if (exitCode != 0 || !logTail.contains(successMarker)) {
            throw new IllegalStateException(
                    "message log %s on %s did not report success (exit %d); stderr: %s; stdout: %s; log tail:\n%s"
                            .formatted(command, env, exitCode, stderr, stdout, logTail));
        }
    }

    @SneakyThrows
    private String tailArchiverLog(String env) {
        var container = "xrd-" + env;
        var process = new ProcessBuilder(
                lxdProperties.lxcCommand(), "exec", container, "--",
                "tail", "-n", "50", ARCHIVER_LOG)
                .start();
        var stdout = readAll(process.getInputStream());
        process.waitFor();
        return stdout;
    }

    @Override
    @SneakyThrows
    public void downloadMessageLogArchives(String env, String localDir) {
        downloadArchivesTarball(env, ARCHIVE_DIR, localDir);
    }

    @Override
    @SneakyThrows
    public int decryptArchives(String env, String filePrefix, String keyId, String passphrase, String outputDir) {
        var container = "xrd-" + env;
        var keyFile = Path.of(coreProperties.resourceDir() + "gpg_keys/" + keyId + ".asc");
        var workDir = "/tmp/decrypt-" + UUID.randomUUID();
        var gnupgHome = workDir + "/gnupg";
        var decryptedDir = workDir + "/out";

        try {
            // Decryption happens inside the container, using its own gpg, rather than on the test
            // host: the fixture keys carry no encryption capability, which gpg 2.4.x tolerates and
            // 2.5+ refuses, so a host-side decrypt makes the outcome depend on the host's gpg
            // version. Only the recipient private key ever leaves the test host, piped in over
            // `lxc exec` stdin; nothing is written to a container image or left behind on disk.
            var listResult = lxcExecChecked(container,
                    "find", ARCHIVE_DIR, "-maxdepth", "1", "-type", "f", "-name", filePrefix + "*.gpg");
            var remoteFiles = listResult.stdout().lines().filter(line -> !line.isBlank()).toList();

            lxcExecChecked(container, "mkdir", "-p", "-m", "700", gnupgHome);
            lxcExecChecked(container, "mkdir", "-p", decryptedDir);
            importKey(container, gnupgHome, keyFile);

            for (var remoteFile : remoteFiles) {
                decryptOneInContainer(container, gnupgHome, remoteFile, passphrase, decryptedDir);
            }

            downloadDecryptedTarball(container, decryptedDir, outputDir);
            return remoteFiles.size();
        } finally {
            lxcExec(container, "rm", "-rf", workDir);
        }
    }

    @SneakyThrows
    private void downloadArchivesTarball(String env, String remoteDir, String localDir) {
        var container = "xrd-" + env;
        Files.createDirectories(Path.of(localDir));
        var remoteTarball = "/tmp/messagelog-archives-" + UUID.randomUUID() + ".tar.gz";

        // Native SS hosts have other content under /var/lib/xroad (a "backup" directory, dotfiles)
        // that compose's stripped-down message-log-cli image never has; only mlog-* archive files
        // belong in the tarball the archive assertions later read entry-by-entry.
        run(lxdProperties.lxcCommand(), "exec", container, "--",
                "sh", "-c", "cd %s && find . -maxdepth 1 -type f -name 'mlog-*' | tar czf %s -T -"
                        .formatted(remoteDir, remoteTarball));
        run(lxdProperties.lxcCommand(), "exec", container, "--", "chmod", "0644", remoteTarball);
        run(lxdProperties.lxcCommand(), "file", "pull",
                "%s%s".formatted(container, remoteTarball), localDir + "/messagelog-archives.tar.gz");
        run(lxdProperties.lxcCommand(), "exec", container, "--", "rm", "-f", remoteTarball);
    }

    /**
     * Tars up the container's decrypted-files directory and downloads it, mirroring
     * {@link #downloadArchivesTarball}. An empty {@code remoteDecryptedDir} still yields a readable
     * tarball containing only its own directory entry, so a wrong key/passphrase produces zero file
     * entries rather than a missing or broken tarball.
     */
    @SneakyThrows
    private void downloadDecryptedTarball(String container, String remoteDecryptedDir, String localDir) {
        Files.createDirectories(Path.of(localDir));
        var remoteTarball = "/tmp/messagelog-archives-" + UUID.randomUUID() + ".tar.gz";
        run(lxdProperties.lxcCommand(), "exec", container, "--",
                "sh", "-c", "cd %s && tar czf %s .".formatted(remoteDecryptedDir, remoteTarball));
        run(lxdProperties.lxcCommand(), "file", "pull",
                "%s%s".formatted(container, remoteTarball), localDir + "/messagelog-archives.tar.gz");
        run(lxdProperties.lxcCommand(), "exec", container, "--", "rm", "-f", remoteTarball);
    }

    /**
     * Imports the recipient private key into a gpg homedir inside the container, piping the key
     * material in over {@code lxc exec} stdin so it never touches an intermediate file or the
     * container image.
     */
    @SneakyThrows
    private void importKey(String container, String gnupgHome, Path keyFile) {
        var commandArgs = new ArrayList<>(List.of(
                lxdProperties.lxcCommand(), "exec", container, "--",
                "gpg", "--homedir", gnupgHome, "--batch", "--yes", "--import", "-"));
        var process = new ProcessBuilder(commandArgs).redirectInput(keyFile.toFile()).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Importing key %s into %s failed (exit %d): %s%s"
                    .formatted(keyFile, container, exitCode, stdout, stderr));
        }
    }

    /**
     * Decrypts a single archive in place inside the container. gpg's exit code cannot be trusted
     * here: these archives are signed by a key the fixture keyring doesn't hold, so gpg reports
     * "Can't check signature: No public key" and exits non-zero even after writing a complete,
     * correctly decrypted output file. A wrong key/passphrase, by contrast, never writes the output
     * file at all. So success is judged by the output file existing and being non-empty
     * ({@code test -s}), not by gpg's exit status; a failed decrypt is logged at WARN with gpg's
     * stderr and leaves no (or an empty) file behind, keeping it out of the resulting tarball.
     */
    @SneakyThrows
    private void decryptOneInContainer(String container, String gnupgHome, String remoteFile, String passphrase, String decryptedDir) {
        var outFileName = remoteFile.substring(remoteFile.lastIndexOf('/') + 1).replaceFirst("\\.gpg$", "");
        var outPath = decryptedDir + "/" + outFileName;

        var decryptResult = lxcExec(container,
                "gpg", "--homedir", gnupgHome, "--batch", "--no-tty", "--pinentry-mode", "loopback",
                "--passphrase", passphrase, "--output", outPath, "--decrypt", remoteFile);
        var sizeCheck = lxcExec(container, "test", "-s", outPath);
        if (sizeCheck.exitCode() != 0) {
            log.warn("Decryption of {} in {} did not produce output (gpg exit {}): {}",
                    remoteFile, container, decryptResult.exitCode(), decryptResult.stderr());
            lxcExec(container, "rm", "-f", outPath);
        }
    }

    private record LxcExecResult(String stdout, String stderr, int exitCode) {
    }

    @SneakyThrows
    private LxcExecResult lxcExec(String container, String... command) {
        var commandArgs = new ArrayList<>(List.of(lxdProperties.lxcCommand(), "exec", container, "--"));
        commandArgs.addAll(List.of(command));
        var process = new ProcessBuilder(commandArgs).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        var stdout = stdoutFuture.get();
        var stderr = stderrFuture.get();
        var exitCode = process.waitFor();
        return new LxcExecResult(stdout, stderr, exitCode);
    }

    private LxcExecResult lxcExecChecked(String container, String... command) {
        var result = lxcExec(container, command);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("%s in %s failed (exit %d): %s"
                    .formatted(List.of(command), container, result.exitCode(), result.stderr()));
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

    private String resolveHost(String env) {
        return switch (env) {
            case "ss0" -> lxdProperties.ss0Host();
            case "ss1" -> lxdProperties.ss1Host();
            case "aux" -> lxdProperties.csHost();
            default -> throw new IllegalArgumentException("Unknown LXD environment: " + env);
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
        throw new UnsupportedOperationException("LxdEnvSetup does not manage a compose stack");
    }

    @Override
    public ContainerMapping getContainerMapping(String service, int originalPort) {
        throw new UnsupportedOperationException("Use getContainerMapping(env, service, port); there is no compose stack");
    }

    @Override
    public Container.ExecResult execInContainer(String container, String... command) {
        throw new UnsupportedOperationException("Not supported on the LXD adapter; use the ops interfaces");
    }

    @Override
    public void restartService(String containerName) {
        throw new UnsupportedOperationException("Not supported on the LXD adapter");
    }

    @Override
    public void copyFilesToContainer(String containerName, String classpathResource, String targetPath) {
        throw new UnsupportedOperationException("Not supported on the LXD adapter");
    }

    @Override
    public void copyFileFromContainer(String containerName, String containerPath, String localPath) {
        throw new UnsupportedOperationException("Not supported on the LXD adapter");
    }
}
