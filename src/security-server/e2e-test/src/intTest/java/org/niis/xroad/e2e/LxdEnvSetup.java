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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;

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
import java.util.stream.Stream;

/**
 * LXD-based implementation of the e2e environment.
 * Assumes the environment is already provisioned and bootstrapped by Ansible.
 * Bring-up and teardown are no-ops.
 */
@Slf4j
@RequiredArgsConstructor
public class LxdEnvSetup implements E2eEnvironment, MessagelogDbOps, MessagelogArchiveOps {

    private static final int PROBE_TIMEOUT_MS = 5000;
    private static final String MESSAGELOG_SEARCH_PATH = "--search_path=messagelog,public";
    private static final String ARCHIVE_DIR = "/var/lib/xroad";
    private static final String ARCHIVER_LOG = "/var/log/xroad/message-log-archiver.log";
    private static final String ARCHIVER_CLI = "/usr/share/xroad/bin/xroad-message-log-archiver";
    private static final String ARCHIVE_SUCCESS_MARKER = "Archival operation completed successfully";
    private static final String CLEANUP_SUCCESS_MARKER = "Cleanup operation completed successfully";

    private final LxdEnvProperties lxdProperties;
    private final TestFrameworkCoreProperties coreProperties;

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
    public String peerControlPlaneHost(String env) {
        return "xrd-" + env + ".lxd";
    }

    @Override
    public String participantContextId(String env) {
        return resolveHost(env);
    }

    @Override
    public String participantDid(String env) {
        return "did:web:%s%%3A7183".formatted(resolveHost(env));
    }

    @Override
    public String securityServerAddress(String env) {
        return resolveHost(env);
    }

    @Override
    @SneakyThrows
    public String execMessagelogSql(String env, String sql) {
        var container = "xrd-" + env;
        var process = new ProcessBuilder(
                lxdProperties.lxcCommand(), "exec", container, "--",
                "sudo", "-u", "postgres", "PGOPTIONS=" + MESSAGELOG_SEARCH_PATH,
                "psql", "-d", "messagelog", "-tAX", "-c", sql)
                .start();

        // Stdout and stderr are drained concurrently to avoid deadlocking on a full pipe buffer:
        // lxc exec emits sudoers noise on stderr on every call, which must not be mistaken for failure.
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
        var container = "xrd-" + env;
        var commandArgs = new ArrayList<String>(List.of(
                lxdProperties.lxcCommand(), "exec", container, "--",
                "sudo", "-u", "xroad", ARCHIVER_CLI));
        commandArgs.addAll(List.of(command.split(" ")));

        var process = new ProcessBuilder(commandArgs).start();
        var stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        var stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        stdoutFuture.get();
        stderrFuture.get();
        var exitCode = process.waitFor();

        // The CLI always exits 0 (MessageLogArchiverService catches and logs internally), so the
        // real outcome is read from its own log file — the %native Quarkus profile disables
        // console logging in favor of /var/log/xroad/message-log-archiver.log.
        var successMarker = command.startsWith("archive") ? ARCHIVE_SUCCESS_MARKER : CLEANUP_SUCCESS_MARKER;
        var logTail = tailArchiverLog(env);
        if (exitCode != 0 || !logTail.contains(successMarker)) {
            throw new IllegalStateException(
                    "message log %s on %s did not report success (exit %d); log tail:\n%s"
                            .formatted(command, env, exitCode, logTail));
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
        var keyFile = coreProperties.resourceDir() + "gpg_keys/" + keyId + ".asc";
        var downloadedDir = Files.createTempDirectory("lxd-encrypted-");
        var decryptedDir = Files.createTempDirectory("lxd-decrypted-");

        try {
            // Mirrors compose's decrypt-archives.sh: operates directly on the archive directory on
            // the SS host, not on a prior local download — the 0200 decrypt scenarios never call
            // "messsagelog archives are downloaded from" first.
            var listProcess = new ProcessBuilder(
                    lxdProperties.lxcCommand(), "exec", container, "--",
                    "find", ARCHIVE_DIR, "-maxdepth", "1", "-type", "f", "-name", filePrefix + "*.gpg")
                    .start();
            var fileList = readAll(listProcess.getInputStream());
            listProcess.waitFor();
            var remoteFiles = fileList.lines().filter(line -> !line.isBlank()).toList();

            for (var remoteFile : remoteFiles) {
                run(lxdProperties.lxcCommand(), "file", "pull", "%s%s".formatted(container, remoteFile), downloadedDir.toString());
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
    private void downloadArchivesTarball(String env, String remoteDir, String localDir) {
        var container = "xrd-" + env;
        Files.createDirectories(Path.of(localDir));
        var remoteTarball = "/tmp/messagelog-archives-" + UUID.randomUUID() + ".tar.gz";

        // Native SS hosts have other content under /var/lib/xroad (a "backup" directory, dotfiles)
        // that compose's stripped-down message-log-cli image never has; only mlog-* archive files
        // belong in the tarball serviceHasMessagelogArchivePresent later reads entry-by-entry.
        run(lxdProperties.lxcCommand(), "exec", container, "--",
                "sh", "-c", "cd %s && find . -maxdepth 1 -type f -name 'mlog-*' | tar czf %s -T -"
                        .formatted(remoteDir, remoteTarball));
        run(lxdProperties.lxcCommand(), "exec", container, "--", "chmod", "0644", remoteTarball);
        run(lxdProperties.lxcCommand(), "file", "pull",
                "%s%s".formatted(container, remoteTarball), localDir + "/messagelog-archives.tar.gz");
        run(lxdProperties.lxcCommand(), "exec", container, "--", "rm", "-f", remoteTarball);
    }

    @SneakyThrows
    private void packageAsTarball(Path sourceDir, String outputDir) {
        Files.createDirectories(Path.of(outputDir));
        // COPYFILE_DISABLE suppresses BSD tar's AppleDouble ._* metadata entries when this runs on
        // macOS (harmless/absent on the ubuntu-24.04 CI runner's GNU tar), which would otherwise
        // inflate the entry count serviceHasMessagelogArchivePresent and the decrypt count checks
        // read back out of this tarball.
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
        var gnupgHome = Files.createTempDirectory("lxd-gpg-");
        try {
            Files.setPosixFilePermissions(gnupgHome, PosixFilePermissions.fromString("rwx------"));

            runGpg(gnupgHome, "--batch", "--yes", "--import", keyFile);

            var outFileName = encryptedFile.getFileName().toString().replaceFirst("\\.gpg$", "");
            var outPath = outputDir.resolve(outFileName);
            // Errors are ignored per file, mirroring decrypt-archives.sh — a wrong key/passphrase
            // fails individual files without aborting the batch.
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
}
