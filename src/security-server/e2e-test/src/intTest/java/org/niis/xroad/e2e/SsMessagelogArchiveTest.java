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

import ee.ria.xroad.common.asic.AsicContainerVerifier;

import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.e2e.container.E2eEnvSetup;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.test.apitest.core.config.ApiTestConfigSource;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;
import org.niis.xroad.test.globalconf.TestGlobalConfFactory;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Runs after {@link SsProxyMessageFlowTest}: the messagelog counts asserted here are the records its
 * traffic produced on ss1. Method order matters too: the first test downloads the verificationconf that
 * verifies every archive, and the second produces the {@code mlog-*} files the decryption tests read.
 */
@DisplayName("SS message log - archive, cleanup and encryption-key-scoped decryption")
@Order(200)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class SsMessagelogArchiveTest extends E2eTest {

    private static final String MESSAGELOG_ARCHIVES_FILE = "messagelog-archives.tar.gz";

    @Test
    @Order(1)
    @DisplayName("SS0 Messagelogs are successfully archived and removed from database")
    void ss0MessagelogsAreArchivedAndRemoved(E2eEnvSetup env) {
        given("the environment is initialized", () -> assertThat(env.isAuxHurlRunning()).isFalse());

        and("message log 'archive DEV' is triggered on ss0", () -> triggerMessageLogCommand(env, "ss0", "archive DEV"));
        and("global configuration is fetched from ss0's proxy for messagelog verification",
                () -> fetchGlobalConfForMessagelogVerification(env, "ss0"));
        and("messagelog archives are downloaded from ss0 message-log-cli", () ->
                downloadMessageLogArchives(env, "ss0", SsStackSetup.MESSAGE_LOG_CLI, "/var/lib/xroad", "ss0"));

        then("ss0 has 20 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss0", 20));

        when("message log 'cleanup' is triggered on ss0", () -> triggerMessageLogCommand(env, "ss0", "cleanup"));
        then("ss0 contains 0 messagelog entries", () -> assertMessagelogEntryCount(env, "ss0", 0));
    }

    @Test
    @Order(2)
    @DisplayName("SS1 messagelog is successfully archived and removed from database")
    void ss1MessagelogIsArchivedAndRemoved(E2eEnvSetup env) {
        when("message log 'archive DEV' is triggered on ss1", () -> triggerMessageLogCommand(env, "ss1", "archive DEV"));
        and("message log 'cleanup' is triggered on ss1", () -> triggerMessageLogCommand(env, "ss1", "cleanup"));
        and("messagelog archives are downloaded from ss1 message-log-cli", () ->
                downloadMessageLogArchives(env, "ss1", SsStackSetup.MESSAGE_LOG_CLI, "/var/lib/xroad", "ss1"));

        then("ss1 contains 0 messagelog entries", () -> assertMessagelogEntryCount(env, "ss1", 0));
    }

    @Test
    @Order(3)
    @DisplayName("DEV/COM/4321 messagelogs can be decrypted with key 8A4BB80EEE081BDE")
    void dev4321MessagelogsCanBeDecryptedWithKey1(E2eEnvSetup env) {
        then("ss1 messagelog archives 'mlog-DEV_COM_4321' can be decrypted using key '8A4BB80EEE081BDE'", () ->
                assertArchivesCanBeDecrypted(env, "ss1", "mlog-DEV_COM_4321", "8A4BB80EEE081BDE"));
        and("ss1/8A4BB80EEE081BDE has 10 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss1/8A4BB80EEE081BDE", 10));
    }

    @Test
    @Order(4)
    @DisplayName("DEV/COM/4321 messagelogs can be decrypted with key E93952B01C2D2EA5")
    void dev4321MessagelogsCanBeDecryptedWithKey2(E2eEnvSetup env) {
        then("ss1 messagelog archives 'mlog-DEV_COM_4321' can be decrypted using key 'E93952B01C2D2EA5'", () ->
                assertArchivesCanBeDecrypted(env, "ss1", "mlog-DEV_COM_4321", "E93952B01C2D2EA5"));
        and("ss1/E93952B01C2D2EA5 has 10 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss1/E93952B01C2D2EA5", 10));
    }

    @Test
    @Order(5)
    @DisplayName("DEV/COM/1234 messagelogs can be decrypted with key 3BD9C292C63580F8")
    void dev1234MessagelogsCanBeDecryptedWithKey3(E2eEnvSetup env) {
        when("ss1 messagelog archives 'mlog-DEV_COM_1234_test-consumer' can be decrypted using key '3BD9C292C63580F8'", () ->
                assertArchivesCanBeDecrypted(env, "ss1", "mlog-DEV_COM_1234_test-consumer", "3BD9C292C63580F8"));
        and("ss1/3BD9C292C63580F8 has 2 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss1/3BD9C292C63580F8", 2));
    }

    @Test
    @Order(6)
    @DisplayName("messagelogs decryption with other keys fails")
    void messagelogDecryptionWithOtherKeysFails(E2eEnvSetup env) {
        given("ss1 messagelog archives 'mlog-DEV_COM_4321' can not be decrypted using key '3BD9C292C63580F8'", () ->
                assertArchivesCannotBeDecrypted(env, "ss1", "mlog-DEV_COM_4321", "3BD9C292C63580F8"));
        and("ss1 messagelog archives 'mlog-DEV_COM_1234_test-consumer' can not be decrypted using key 'E93952B01C2D2EA5'", () ->
                assertArchivesCannotBeDecrypted(env, "ss1", "mlog-DEV_COM_1234_test-consumer", "E93952B01C2D2EA5"));
        and("ss1 messagelog archives 'mlog-DEV_COM_1234_test-consumer' can not be decrypted using key '8A4BB80EEE081BDE'", () ->
                assertArchivesCannotBeDecrypted(env, "ss1", "mlog-DEV_COM_1234_test-consumer", "8A4BB80EEE081BDE"));
    }

    private void triggerMessageLogCommand(E2eEnvSetup env, String envName, String command) {
        var javaCmd = "java -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
                + " -Dquarkus.profile=containerized"
                + " -jar /opt/app/quarkus-run.jar " + command
                + " 2>&1 | tee /proc/1/fd/1";
        var result = env.execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI, "sh", "-c", javaCmd);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Message log %s failed on %s (exit code %d): %s"
                    .formatted(command, envName, result.getExitCode(), result.getStderr()));
        }
    }

    @SneakyThrows
    private void fetchGlobalConfForMessagelogVerification(E2eEnvSetup env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);

        try (var zis = new ZipInputStream(RestAssuredFactory.givenSilent()
                .get("http://%s:%s/verificationconf".formatted(mapping.host(), mapping.port()))
                .asInputStream())) {
            for (var entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                var path = Path.of(resourceDir()).resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(path.getParent());
                    Files.copy(zis, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    @SneakyThrows
    private void downloadMessageLogArchives(E2eEnvSetup env, String envName, String service, String containerPath, String localEnvDir) {
        var localDir = resourceDir() + localEnvDir;
        Files.createDirectories(Paths.get(localDir));
        var localCompressedArchivesPath = localDir + "/" + MESSAGELOG_ARCHIVES_FILE;

        env.execInEnvContainer(envName, service, "tar", "czf", "/tmp/" + MESSAGELOG_ARCHIVES_FILE, "-C", containerPath, ".");
        env.copyFileFromContainer(envName, service, "/tmp/" + MESSAGELOG_ARCHIVES_FILE, localCompressedArchivesPath);
        env.execInEnvContainer(envName, service, "rm", "/tmp/" + MESSAGELOG_ARCHIVES_FILE);
    }

    @SneakyThrows
    private void assertMessagelogArchivePresent(String localEnvDir, int expectedMessagelogCount) {
        var localCompressedArchivesPath = resourceDir() + localEnvDir + "/" + MESSAGELOG_ARCHIVES_FILE;

        try (var tis = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(localCompressedArchivesPath)))) {
            var messagelogCount = 0;
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (entry.getName().equals("./")) {
                    continue;
                }
                assertThat(entry.getName()).matches("\\./mlog-.*\\.zip");
                try (var bais = new ByteArrayInputStream(tis.readAllBytes()); var zis = new ZipInputStream(bais)) {
                    ZipEntry archiveEntry;
                    while ((archiveEntry = zis.getNextEntry()) != null) {
                        if (archiveEntry.getName().equals("linkinginfo")) {
                            continue;
                        }
                        assertThat(archiveEntry.getName()).endsWith(".asice");
                        var tmpAsiceContainer = Files.write(
                                Path.of(resourceDir(), localEnvDir, archiveEntry.getName()), zis.readAllBytes());
                        verifyMessagelog(tmpAsiceContainer);
                        Files.delete(tmpAsiceContainer);
                        messagelogCount++;
                    }
                }
            }
            assertThat(messagelogCount).isEqualTo(expectedMessagelogCount);
        }
    }

    @SneakyThrows
    private void verifyMessagelog(Path asiceContainer) {
        new AsicContainerVerifier(
                TestGlobalConfFactory.create(resourceDir() + "verificationconf"),
                new OcspVerifierFactory(),
                asiceContainer.toString()
        ).verify();
    }

    private void assertMessagelogEntryCount(E2eEnvSetup env, String envName, int expectedCount) {
        var recordsCount = Integer.parseInt(env.execMessagelogSql(envName, "SELECT COUNT(id) FROM logrecord"));
        assertThat(recordsCount).isEqualTo(expectedCount);
    }

    private void assertArchivesCanBeDecrypted(E2eEnvSetup env, String envName, String filePrefix, String keyId) {
        var keyfile = "/gpg-keys/%s.asc".formatted(keyId);
        var outputDir = "/tmp/" + UUID.randomUUID();

        var execResult = env.execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI,
                "/gpg-keys/scripts/decrypt-archives.sh", filePrefix, keyfile, "secret", outputDir);

        var localEnvDir = envName + "/" + keyId;
        downloadMessageLogArchives(env, envName, SsStackSetup.MESSAGE_LOG_CLI, outputDir, localEnvDir);

        var processedFilesCount = filesProcessedCount(execResult.getStdout());
        var decryptedFilesCount = env.execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI,
                "/gpg-keys/scripts/count_files.sh", outputDir).getStdout().trim();

        assertThat(Integer.parseInt(decryptedFilesCount)).isEqualTo(processedFilesCount);
    }

    private void assertArchivesCannotBeDecrypted(E2eEnvSetup env, String envName, String filePrefix, String keyId) {
        var keyfile = "/gpg-keys/%s.asc".formatted(keyId);
        var outputDir = "/tmp/" + UUID.randomUUID();

        env.execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI,
                "/gpg-keys/scripts/decrypt-archives.sh", filePrefix, keyfile, "secret", outputDir);
        var outputFilesCount = env.execInEnvContainer(envName, SsStackSetup.MESSAGE_LOG_CLI,
                "/gpg-keys/scripts/count_files.sh", outputDir).getStdout().trim();

        assertThat(outputFilesCount).isEqualTo("0");
    }

    private int filesProcessedCount(String output) {
        var matcher = Pattern.compile("Processed (\\d+) files\\.").matcher(output);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new IllegalStateException("Could not parse processed file count from decrypt-archives.sh output: " + output);
    }

    private String resourceDir() {
        return ApiTestConfigSource.getInstance().getCoreProperties().resourceDir();
    }
}
