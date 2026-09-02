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
import org.niis.xroad.e2e.E2eEnvironment;
import org.niis.xroad.e2e.MessagelogArchiveOps;
import org.niis.xroad.e2e.MessagelogDbOps;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.Container;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;

/**
 * Facade over the three compose stacks (aux, ss0, ss1). Extends {@link BaseComposeSetup} only to satisfy
 * the api-test-core session-listener/extension lifecycle; the inherited single-stack accessors are
 * overridden to fail loudly — test code must use the env-qualified overloads.
 */
@Slf4j
public class E2eEnvSetup extends BaseComposeSetup implements E2eEnvironment, MessagelogDbOps, MessagelogArchiveOps {

    private static final Pattern PROCESSED_FILES_PATTERN = Pattern.compile("Processed (\\d+) files\\.");
    private static final String MESSAGELOG_ARCHIVES_FILE = "messagelog-archives.tar.gz";

    private static final Duration GLOBALCONF_PROPAGATION_GRACE_PERIOD = Duration.ofSeconds(20);

    private AuxStackSetup aux;
    private SsStackSetup ss0;
    private SsStackSetup ss1;

    public E2eEnvSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    public void start() {
        aux = new AuxStackSetup(coreProperties);
        aux.start();

        ss0 = new SsStackSetup(coreProperties, "ss0",
                Set.of(SsStackSetup.Feature.BATCH_SIGNATURES, SsStackSetup.Feature.SOFTTOKEN_SIGNER, SsStackSetup.Feature.OP_MONITOR));
        ss0.start();

        ss1 = new SsStackSetup(coreProperties, "ss1",
                Set.of(SsStackSetup.Feature.HSM, SsStackSetup.Feature.MESSAGE_LOG_ENCRYPTION));
        ss1.start();

        aux.waitForHurlToFinish();
        // The ds-* containers fail fast until setup.hurl has uploaded each server's DS TLS
        // certificate through the admin API, so their readiness is only awaitable from here on.
        ss0.awaitDsReadiness();
        ss1.awaitDsReadiness();
        ss0.awaitProxyReadiness();
        ss1.awaitProxyReadiness();

        log.info("Waiting grace period of {} for global configuration to propagate..", GLOBALCONF_PROPAGATION_GRACE_PERIOD);
        await().pollDelay(GLOBALCONF_PROPAGATION_GRACE_PERIOD)
                .timeout(GLOBALCONF_PROPAGATION_GRACE_PERIOD.plusMinutes(1))
                .until(() -> true);
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
        return mapEnvironment(envName).getContainerMapping(service, originalPort);
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
        var result = execInEnvContainer(envName, SsStackSetup.DB_MESSAGELOG,
                "psql", "-U", "postgres", "-d", "messagelog", "-tAX", "-c", sql);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql query on %s failed: %s".formatted(envName, result.getStderr()));
        }
        return result.getStdout().trim();
    }

    @Override
    @SneakyThrows
    public void triggerMessageLogCommand(String envName, String command) {
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
        downloadArchivesTarball(envName, "/var/lib/xroad", localDir);
    }

    @Override
    @SneakyThrows
    public int decryptArchives(String envName, String filePrefix, String keyId, String passphrase, String outputDir) {
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

    private BaseComposeSetup mapEnvironment(String name) {
        return switch (name) {
            case "ss0" -> ss0;
            case "ss1" -> ss1;
            case "aux" -> aux;
            default -> throw new IllegalArgumentException("Unknown environment: " + name);
        };
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
}
