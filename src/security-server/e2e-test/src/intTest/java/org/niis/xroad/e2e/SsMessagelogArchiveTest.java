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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.test.apitest.core.config.ApiTestConfigSource;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;
import org.niis.xroad.test.globalconf.TestGlobalConfFactory;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class SsMessagelogArchiveTest extends E2eTest {

    private static final String MESSAGELOG_ARCHIVES_FILE = "messagelog-archives.tar.gz";
    private static final String DEFAULT_PASSPHRASE = "secret";

    private static final Map<String, String> PRE_ARCHIVE_MESSAGELOG = new ConcurrentHashMap<>();

    @Test
    @Order(1)
    @DisplayName("SS0 Messagelogs are successfully archived and removed from database")
    void ss0MessagelogsAreArchivedAndRemoved(E2eEnvironment env, MessagelogArchiveOps archiveOps, MessagelogDbOps dbOps) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        and("ss0 messagelog contents are captured for diagnostics before archiving",
                () -> captureMessagelogSnapshot(dbOps, "ss0"));
        and("message log 'archive DEV' is triggered on ss0", () -> archiveOps.triggerMessageLogCommand("ss0", "archive DEV"));
        and("global configuration is fetched from ss0's proxy for messagelog verification",
                () -> fetchGlobalConfForMessagelogVerification(env, "ss0"));
        and("messagelog archives are downloaded from ss0", () ->
                archiveOps.downloadMessageLogArchives("ss0", resourceDir() + "ss0"));

        then("ss0 has 20 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss0", 20));

        when("message log 'cleanup' is triggered on ss0", () -> archiveOps.triggerMessageLogCommand("ss0", "cleanup"));
        then("ss0 contains 0 messagelog entries", () -> assertMessagelogEntryCount(dbOps, "ss0", 0));
    }

    @Test
    @Order(2)
    @DisplayName("SS1 messagelog is successfully archived and removed from database")
    void ss1MessagelogIsArchivedAndRemoved(MessagelogArchiveOps archiveOps, MessagelogDbOps dbOps) {
        given("ss1 messagelog contents are captured for diagnostics before archiving",
                () -> captureMessagelogSnapshot(dbOps, "ss1"));
        when("message log 'archive DEV' is triggered on ss1", () -> archiveOps.triggerMessageLogCommand("ss1", "archive DEV"));
        and("message log 'cleanup' is triggered on ss1", () -> archiveOps.triggerMessageLogCommand("ss1", "cleanup"));
        and("messagelog archives are downloaded from ss1", () ->
                archiveOps.downloadMessageLogArchives("ss1", resourceDir() + "ss1"));

        then("ss1 contains 0 messagelog entries", () -> assertMessagelogEntryCount(dbOps, "ss1", 0));
    }

    @Test
    @Order(3)
    @DisplayName("DEV/COM/4321 messagelogs can be decrypted with key 8A4BB80EEE081BDE")
    void dev4321MessagelogsCanBeDecryptedWithKey1(MessagelogArchiveOps archiveOps) {
        then("ss1 messagelog archives 'mlog-DEV_COM_4321' can be decrypted using key '8A4BB80EEE081BDE'", () ->
                assertArchivesCanBeDecrypted(archiveOps, "ss1", "mlog-DEV_COM_4321", "8A4BB80EEE081BDE"));
        and("ss1/8A4BB80EEE081BDE has 10 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss1/8A4BB80EEE081BDE", 10));
    }

    @Test
    @Order(4)
    @DisplayName("DEV/COM/4321 messagelogs can be decrypted with key E93952B01C2D2EA5")
    void dev4321MessagelogsCanBeDecryptedWithKey2(MessagelogArchiveOps archiveOps) {
        then("ss1 messagelog archives 'mlog-DEV_COM_4321' can be decrypted using key 'E93952B01C2D2EA5'", () ->
                assertArchivesCanBeDecrypted(archiveOps, "ss1", "mlog-DEV_COM_4321", "E93952B01C2D2EA5"));
        and("ss1/E93952B01C2D2EA5 has 10 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss1/E93952B01C2D2EA5", 10));
    }

    @Test
    @Order(5)
    @DisplayName("DEV/COM/1234 messagelogs can be decrypted with key 3BD9C292C63580F8")
    void dev1234MessagelogsCanBeDecryptedWithKey3(MessagelogArchiveOps archiveOps) {
        when("ss1 messagelog archives 'mlog-DEV_COM_1234_test-consumer' can be decrypted using key '3BD9C292C63580F8'", () ->
                assertArchivesCanBeDecrypted(archiveOps, "ss1", "mlog-DEV_COM_1234_test-consumer", "3BD9C292C63580F8"));
        and("ss1/3BD9C292C63580F8 has 2 messagelogs present in the archives and all are cryptographically valid", () ->
                assertMessagelogArchivePresent("ss1/3BD9C292C63580F8", 2));
    }

    @Test
    @Order(6)
    @DisplayName("messagelogs decryption with other keys fails")
    void messagelogDecryptionWithOtherKeysFails(MessagelogArchiveOps archiveOps) {
        given("ss1 messagelog archives 'mlog-DEV_COM_4321' can not be decrypted using key '3BD9C292C63580F8'", () ->
                assertArchivesCannotBeDecrypted(archiveOps, "ss1", "mlog-DEV_COM_4321", "3BD9C292C63580F8"));
        and("ss1 messagelog archives 'mlog-DEV_COM_1234_test-consumer' can not be decrypted using key 'E93952B01C2D2EA5'", () ->
                assertArchivesCannotBeDecrypted(archiveOps, "ss1", "mlog-DEV_COM_1234_test-consumer", "E93952B01C2D2EA5"));
        and("ss1 messagelog archives 'mlog-DEV_COM_1234_test-consumer' can not be decrypted using key '8A4BB80EEE081BDE'", () ->
                assertArchivesCannotBeDecrypted(archiveOps, "ss1", "mlog-DEV_COM_1234_test-consumer", "8A4BB80EEE081BDE"));
    }

    @SneakyThrows
    private void fetchGlobalConfForMessagelogVerification(E2eEnvironment env, String envName) {
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
    private void assertMessagelogArchivePresent(String localEnvDir, int expectedMessagelogCount) {
        var localCompressedArchivesPath = resourceDir() + localEnvDir + "/" + MESSAGELOG_ARCHIVES_FILE;
        var archiveEntries = new ArrayList<String>();

        try (var tis = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(localCompressedArchivesPath)))) {
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
                        archiveEntries.add(entry.getName() + " -> " + archiveEntry.getName());
                    }
                }
            }
            assertThat(archiveEntries.size())
                    .withFailMessage(() -> describeArchiveCountMismatch(localEnvDir, expectedMessagelogCount, archiveEntries))
                    .isEqualTo(expectedMessagelogCount);
        }
    }

    private void captureMessagelogSnapshot(MessagelogDbOps dbOps, String env) {
        var countsBySender = dbOps.execMessagelogSql(env,
                "SELECT memberclass, membercode, subsystemcode, response, count(*) FROM logrecord "
                        + "GROUP BY memberclass, membercode, subsystemcode, response "
                        + "ORDER BY memberclass, membercode, subsystemcode, response");
        var records = dbOps.execMessagelogSql(env,
                "SELECT id, time, discriminator, response, memberclass, membercode, subsystemcode, queryid, "
                        + "xrequestid, keyid, archived FROM logrecord ORDER BY time, id");
        var snapshot = String.format(
                "counts (memberclass|membercode|subsystemcode|response|count):%n%s%n%n"
                        + "records (id|time|discriminator|response|memberclass|membercode|subsystemcode|queryid|"
                        + "xrequestid|keyid|archived):%n%s",
                countsBySender, records);
        PRE_ARCHIVE_MESSAGELOG.put(env, snapshot);
        log.info("Pre-archive messagelog snapshot for {}:\n{}", env, snapshot);
    }

    private String describeArchiveCountMismatch(String localEnvDir, int expectedCount, List<String> archiveEntries) {
        var envKey = localEnvDir.startsWith("ss0") ? "ss0" : "ss1";
        return String.format(
                "Expected %d messagelog record(s) in archive '%s' but found %d.%n"
                        + "Archive .asice entries found (%d):%n  %s%n%n"
                        + "Messagelog DB snapshot for '%s' captured before 'archive DEV' "
                        + "(identifies the surplus/missing record):%n%s",
                expectedCount, localEnvDir, archiveEntries.size(), archiveEntries.size(),
                String.join(String.format("%n  "), archiveEntries),
                envKey, PRE_ARCHIVE_MESSAGELOG.getOrDefault(envKey, "(snapshot not captured)"));
    }

    @SneakyThrows
    private void verifyMessagelog(Path asiceContainer) {
        new AsicContainerVerifier(
                TestGlobalConfFactory.create(resourceDir() + "verificationconf"),
                new OcspVerifierFactory(),
                asiceContainer.toString()
        ).verify();
    }

    private void assertMessagelogEntryCount(MessagelogDbOps dbOps, String envName, int expectedCount) {
        var recordsCount = Integer.parseInt(dbOps.execMessagelogSql(envName, "SELECT COUNT(id) FROM logrecord"));
        assertThat(recordsCount).isEqualTo(expectedCount);
    }

    private void assertArchivesCanBeDecrypted(MessagelogArchiveOps archiveOps, String envName, String filePrefix, String keyId) {
        var outputDir = resourceDir() + envName + "/" + keyId;
        var processedFilesCount = archiveOps.decryptArchives(envName, filePrefix, keyId, DEFAULT_PASSPHRASE, outputDir);
        var decryptedFilesCount = countTarballEntries(outputDir + "/" + MESSAGELOG_ARCHIVES_FILE);

        assertThat(decryptedFilesCount).isEqualTo(processedFilesCount);
    }

    private void assertArchivesCannotBeDecrypted(MessagelogArchiveOps archiveOps, String envName, String filePrefix, String keyId) {
        var outputDir = resourceDir() + envName + "/" + keyId;
        archiveOps.decryptArchives(envName, filePrefix, keyId, DEFAULT_PASSPHRASE, outputDir);
        var decryptedFilesCount = countTarballEntries(outputDir + "/" + MESSAGELOG_ARCHIVES_FILE);

        assertThat(decryptedFilesCount).isZero();
    }

    @SneakyThrows
    private int countTarballEntries(String tarballPath) {
        try (var tis = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(tarballPath)))) {
            var count = 0;
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    count++;
                }
            }
            return count;
        }
    }

    private String resourceDir() {
        return ApiTestConfigSource.getInstance().getCoreProperties().resourceDir();
    }
}
