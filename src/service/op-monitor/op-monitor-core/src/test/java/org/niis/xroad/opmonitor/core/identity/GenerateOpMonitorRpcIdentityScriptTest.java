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
package org.niis.xroad.opmonitor.core.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.file.FileKeyProvider;
import org.niis.xroad.common.rpc.test.PemFiles;
import org.niis.xroad.common.rpc.test.TestCa;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GenerateOpMonitorRpcIdentityScriptTest {
    private static final Path SCRIPT = Path.of("../../../../deployment/native-packages/src/xroad/common/op-monitor/"
            + "usr/share/xroad/scripts/generate_opmonitor_rpc_identity.sh").toAbsolutePath().normalize();
    private static final String ROOT_TAG = "root";
    private static final String DIR_VARIABLE = "XROAD_OPMONITOR_IDENTITY_DIR";
    private static final String SERVER_AUTH = "1.3.6.1.5.5.7.3.1";
    private static final String CLIENT_AUTH = "1.3.6.1.5.5.7.3.2";
    private static final int SAN_DNS = 2;
    private static final int SAN_IP = 7;
    private static final int KEY_USAGE_DIGITAL_SIGNATURE = 0;
    private static final int KEY_USAGE_KEY_ENCIPHERMENT = 2;
    private static final int KEY_USAGE_KEY_CERT_SIGN = 5;
    private static final int KEY_USAGE_CRL_SIGN = 6;
    private static final String KEY_USAGE_OID = "2.5.29.15";
    private static final String SUBJECT_KEY_IDENTIFIER_OID = "2.5.29.14";
    private static final String AUTHORITY_KEY_IDENTIFIER_OID = "2.5.29.35";
    private static final int KEY_IDENTIFIER_LENGTH = 20;
    private static final String DAYS_MESSAGE = " must be a positive whole number of days";
    private static final String DNS_NAME = "op-monitor.example.test";
    private static final String IPV4 = "10.1.2.3";
    private static final String IPV6 = "fd00::5";
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration KILL_WAIT = Duration.ofSeconds(5);
    private static final int DEFAULT_SERVER_DAYS = 3650;
    private static final int DEFAULT_CA_DAYS = 7300;
    private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final List<String> OUTPUT_FILES = List.of("ca.crt", "ca.key", "server.crt", "server.key");
    private static final String DEFAULT_PARENT_LINE = "DEFAULT_PARENT=/etc/xroad-opmonitor";
    private static final String DEFAULT_DIR_LINE = "DEFAULT_DIR=$DEFAULT_PARENT/ssl";

    @TempDir(factory = RootControlledTempDirFactory.class)
    Path workDir;

    private Path dir;

    @BeforeEach
    void setUp() {
        assumeTrue(opensslAvailable(), "openssl is not installed");
        assumeTrue(!isRoot() || serverUserExists(), "running as root needs the xroad user");
        dir = workDir.resolve("ssl");
    }

    @Test
    void firstRunCreatesTheFourFilesAndTheServerCertificateCarriesTheInputs() throws Exception {
        assertThat(run(DNS_NAME, IPV4, IPV6).exitCode()).isZero();

        assertThat(listing()).containsExactlyInAnyOrderElementsOf(OUTPUT_FILES);
        var ca = certificate("ca.crt");
        var server = certificate("server.crt");
        assertThat(ca.getBasicConstraints()).isNotNegative();
        assertThat(server.getBasicConstraints()).isEqualTo(-1);
        assertThat(server.getExtendedKeyUsage()).contains(SERVER_AUTH, CLIENT_AUTH);
        assertThat(sanValues(server, SAN_DNS)).containsExactly(DNS_NAME);
        assertThat(ipSans(server)).containsExactlyInAnyOrder(InetAddress.getByName(IPV4), InetAddress.getByName(IPV6));
        assertThatCode(() -> server.verify(ca.getPublicKey())).doesNotThrowAnyException();
    }

    @Test
    void runWithoutSanFailsAndWritesNothing() {
        assertRefused("at least one DNS name or IP address is required");
    }

    @Test
    void runWithoutSanLeavesAnExistingDirectoryEmpty() throws Exception {
        Files.createDirectory(dir);

        var result = run();

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("at least one DNS name or IP address is required");
        assertThat(listing()).isEmpty();
    }

    @Test
    void nonRootRunWithoutTheTestHookIsRefusedAndWritesNothing() throws IOException {
        assumeFalse(isRoot(), "the refusal applies to non-root users");
        var sandbox = Files.createDirectory(workDir.resolve("default-parent"));
        var sandboxDir = Files.createDirectory(sandbox.resolve("ssl"));
        for (var name : OUTPUT_FILES) {
            var file = Files.writeString(sandboxDir.resolve(name), "placeholder " + name);
            Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(Duration.ofDays(1))));
        }
        var script = scriptWithDefaultParent(sandbox);
        var before = sandboxState(sandbox);

        var result = execute(List.of("bash", script.toString(), DNS_NAME), Map.of());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("must be run as root");
        assertThat(sandboxState(sandbox)).isEqualTo(before);
    }

    @Test
    void bothKeysAreRsa2048AndDefaultValidityIsTenAndTwentyYears() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();

        assertThat(privateKey("ca.key").getModulus().bitLength()).isEqualTo(2048);
        assertThat(privateKey("server.key").getModulus().bitLength()).isEqualTo(2048);
        assertThat(validityDays(certificate("server.crt"))).isBetween(DEFAULT_SERVER_DAYS - 1L, DEFAULT_SERVER_DAYS + 1L);
        assertThat(validityDays(certificate("ca.crt"))).isBetween(DEFAULT_CA_DAYS - 1L, DEFAULT_CA_DAYS + 1L);
    }

    @Test
    void daysAndCaDaysSetTheValidity() throws Exception {
        assertThat(run("--days", "100", "--ca-days", "200", DNS_NAME).exitCode()).isZero();

        assertThat(validityDays(certificate("server.crt"))).isBetween(99L, 101L);
        assertThat(validityDays(certificate("ca.crt"))).isBetween(199L, 201L);
    }

    @Test
    void thereIsNoKeyAlgorithmOption() {
        assertRefused("unknown option --key-algorithm", "--key-algorithm", "ec", DNS_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"--help", "-h"})
    void helpPrintsTheUsageAndWritesNothing(String option) {
        var result = run(option);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("usage:", "--days <n>", "--ca-days <n>", "--force-new-ca", "--help");
        assertThat(dir).doesNotExist();
    }

    @ParameterizedTest
    @MethodSource("invalidDayCounts")
    void invalidDayCountsAreRefusedAndNothingIsWritten(String option, String value) {
        assertRefused(option + DAYS_MESSAGE, option, value, DNS_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"--days", "--ca-days"})
    void dayOptionWithoutAValueIsRefusedAndNothingIsWritten(String option) {
        assertRefused(option + " requires a value", DNS_NAME, option);
    }

    @Test
    void dayCountsOfSixDigitsAreAccepted() throws Exception {
        assertThat(run("--days", "100000", "--ca-days", "100001", DNS_NAME).exitCode()).isZero();

        assertThat(listing()).containsExactlyInAnyOrderElementsOf(OUTPUT_FILES);
    }

    @Test
    void certificatesCarryTheKeyUsageAndLinkTheServerCertificateToTheCaKeyIdentifier() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();

        var ca = certificate("ca.crt");
        var server = certificate("server.crt");
        assertThat(keyUsages(ca)).containsExactlyInAnyOrder(KEY_USAGE_KEY_CERT_SIGN, KEY_USAGE_CRL_SIGN);
        assertThat(keyUsages(server)).containsExactlyInAnyOrder(KEY_USAGE_DIGITAL_SIGNATURE, KEY_USAGE_KEY_ENCIPHERMENT);
        assertThat(ca.getCriticalExtensionOIDs()).contains(KEY_USAGE_OID);
        assertThat(server.getCriticalExtensionOIDs()).contains(KEY_USAGE_OID);
        var caSubjectKeyId = keyIdentifier(ca, SUBJECT_KEY_IDENTIFIER_OID);
        assertThat(keyIdentifier(server, AUTHORITY_KEY_IDENTIFIER_OID)).isEqualTo(caSubjectKeyId);
        assertThat(keyIdentifier(server, SUBJECT_KEY_IDENTIFIER_OID)).isNotEqualTo(caSubjectKeyId);
    }

    @Test
    void serverCertificateLongerThanTheNewCaIsRefusedAndWritesNothing() {
        assertThat(run("--days", "400", "--ca-days", "300", DNS_NAME).exitCode()).isNotZero();

        assertThat(dir).doesNotExist();
    }

    @Test
    void serverCertificateLongerThanTheExistingCaIsRefusedAndLeavesTheFilesUnchanged() throws Exception {
        assertThat(run("--days", "10", "--ca-days", "30", DNS_NAME).exitCode()).isZero();
        var before = snapshot();

        assertThat(run("--days", "365", DNS_NAME).exitCode()).isNotZero();

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void keyAndCertificateModesAreRestrictive() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();

        assertThat(mode("ca.key")).isEqualTo("r--------");
        assertThat(mode("server.key")).isEqualTo("r--------");
        assertThat(mode("ca.crt")).isEqualTo("rw-r--r--");
        assertThat(mode("server.crt")).isEqualTo("rw-r--r--");
    }

    @Tag(ROOT_TAG)
    @Test
    void ownersAreRootForTheCaAndXroadForTheServerKeyWhenRunAsRoot() throws Exception {
        assumeTrue(isRoot(), "ownership can be checked only as root");
        assertThat(run(DNS_NAME).exitCode()).isZero();

        assertThat(Files.getOwner(dir.resolve("ca.key")).getName()).isEqualTo("root");
        assertThat(Files.getOwner(dir.resolve("ca.crt")).getName()).isEqualTo("root");
        assertThat(Files.getOwner(dir.resolve("server.key")).getName()).isEqualTo("xroad");
    }

    @Test
    void rerunKeepingTheCaRestoresTheCaModesAndLeavesTheCaBytesUnchanged() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var before = snapshot();
        Files.setPosixFilePermissions(dir.resolve("ca.key"), PosixFilePermissions.fromString("rw-r-----"));
        Files.setPosixFilePermissions(dir.resolve("ca.crt"), PosixFilePermissions.fromString("rw-------"));

        assertThat(run(DNS_NAME).exitCode()).isZero();

        assertThat(mode("ca.key")).isEqualTo("r--------");
        assertThat(mode("ca.crt")).isEqualTo("rw-r--r--");
        assertThat(read("ca.key")).isEqualTo(before.get("ca.key"));
        assertThat(read("ca.crt")).isEqualTo(before.get("ca.crt"));
    }

    @Tag(ROOT_TAG)
    @Test
    void rerunKeepingTheCaRestoresRootOwnershipOfTheCaFilesAndTheDirectoryModeWhenRunAsRoot() throws Exception {
        assumeTrue(isRoot(), "ownership can be checked only as root");
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var xroad = dir.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("xroad");
        Files.setOwner(dir.resolve("ca.key"), xroad);
        Files.setOwner(dir.resolve("ca.crt"), xroad);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));

        assertThat(run(DNS_NAME).exitCode()).isZero();

        assertThat(Files.getOwner(dir.resolve("ca.key")).getName()).isEqualTo("root");
        assertThat(Files.getOwner(dir.resolve("ca.crt")).getName()).isEqualTo("root");
        assertThat(Files.getOwner(dir).getName()).isEqualTo("root");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir))).isEqualTo("rwxr-xr-x");
        assertThat(Files.getOwner(dir.resolve("server.key")).getName()).isEqualTo("xroad");
    }

    @Tag(ROOT_TAG)
    @Test
    void outputDirectoryOwnedByXroadIsRefusedAndNothingIsChangedWhenRunAsRoot() throws Exception {
        assumeTrue(isRoot(), "ownership can be checked only as root");
        Files.createDirectory(dir);
        handOverToXroadWhenRoot(dir);

        var result = run(DNS_NAME);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("not controlled by root");
        assertThat(Files.getOwner(dir).getName()).isEqualTo("xroad");
        assertThat(listing()).isEmpty();
    }

    @Tag(ROOT_TAG)
    @ParameterizedTest
    @ValueSource(strings = {"rwxrwxr-x", "rwxr-xrwx"})
    void outputDirectoryWritableByGroupOrOthersIsRefusedAndNothingIsChangedWhenRunAsRoot(String permissions) throws Exception {
        assumeTrue(isRoot(), "ownership can be checked only as root");
        Files.createDirectory(dir);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString(permissions));

        var result = run(DNS_NAME);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("not controlled by root");
        assertThat(listing()).isEmpty();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir))).isEqualTo(permissions);
    }

    @Tag(ROOT_TAG)
    @ParameterizedTest
    @CsvSource({"rwxrwxr-x,0", "rwxrwxr-x,1", "rwxr-xrwx,0", "rwxr-xrwx,1", "rwxrwxrwx,0", "rwxrwxrwx,1"})
    void outputUnderAnAncestorWritableByGroupOrOthersIsRefusedAndNothingIsWrittenWhenRunAsRoot(String permissions, int level)
            throws Exception {
        assumeTrue(isRoot(), "ownership can be checked only as root");
        var ancestors = ancestorChain();
        Files.setPosixFilePermissions(ancestors.get(level), PosixFilePermissions.fromString(permissions));

        assertRefusedUnderAncestors(ancestors);
    }

    @Tag(ROOT_TAG)
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void outputUnderAnAncestorOwnedByXroadIsRefusedAndNothingIsWrittenWhenRunAsRoot(int level) throws Exception {
        assumeTrue(isRoot(), "ownership can be checked only as root");
        var ancestors = ancestorChain();
        handOverToXroadWhenRoot(ancestors.get(level));

        assertRefusedUnderAncestors(ancestors);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "--force-new-ca"})
    void rerunFailingAfterKeyGenerationLeavesTheFilesUnchangedAndNoStagingFiles(String option) throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var before = snapshot();
        var listingBefore = listing().stream().sorted().toList();
        var calls = workDir.resolve("openssl.calls");
        var path = fakeOpensslFailingOn(calls);

        var result = run(dir, Map.of("PATH", path), option.isEmpty() ? new String[] {DNS_NAME} : new String[] {option, DNS_NAME});

        assertThat(result.exitCode()).isNotZero();
        assertThat(Files.readAllLines(calls)).contains("genpkey", "req", "x509");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(listing().stream().sorted().toList()).isEqualTo(listingBefore);
    }

    @Test
    void rerunWithoutCaKeyIsRefusedAndChangesNothingUntilTheKeyIsRestored() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var before = snapshot();
        Files.delete(dir.resolve("ca.key"));
        var listingBefore = listing().stream().sorted().toList();

        var refused = run(DNS_NAME);

        assertThat(refused.exitCode()).isNotZero();
        assertThat(refused.output()).contains("only one of ca.crt and ca.key");
        assertThat(listing().stream().sorted().toList()).isEqualTo(listingBefore);
        assertThat(read("ca.crt")).isEqualTo(before.get("ca.crt"));
        assertThat(read("server.key")).isEqualTo(before.get("server.key"));
        assertThat(read("server.crt")).isEqualTo(before.get("server.crt"));

        Files.writeString(dir.resolve("ca.key"), before.get("ca.key"));
        Files.setPosixFilePermissions(dir.resolve("ca.key"), PosixFilePermissions.fromString("rw-r-----"));

        assertThat(run(DNS_NAME).exitCode()).isZero();

        assertThat(read("ca.key")).isEqualTo(before.get("ca.key"));
        assertThat(read("ca.crt")).isEqualTo(before.get("ca.crt"));
        assertThat(read("server.key")).isNotEqualTo(before.get("server.key"));
        assertThat(mode("ca.key")).isEqualTo("r--------");
    }

    @Test
    void forceNewCaAfterTheCaKeyIsLostCreatesANewCaAndAServerIdentityThatOnlyTheNewCaVerifies() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var before = snapshot();
        Files.delete(dir.resolve("ca.key"));

        assertThat(run("--force-new-ca", DNS_NAME).exitCode()).isZero();

        assertThat(listing()).containsExactlyInAnyOrderElementsOf(OUTPUT_FILES);
        var after = snapshot();
        assertThat(after).doesNotContainEntry("ca.crt", before.get("ca.crt"));
        assertThat(after).doesNotContainEntry("ca.key", before.get("ca.key"));
        assertThat(after).doesNotContainEntry("server.key", before.get("server.key"));
        assertThat(after).doesNotContainEntry("server.crt", before.get("server.crt"));
        var server = certificate("server.crt");
        assertThatCode(() -> server.verify(certificate("ca.crt").getPublicKey())).doesNotThrowAnyException();
        assertThatThrownBy(() -> server.verify(parseCertificate(before.get("ca.crt")).getPublicKey()))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void missingParentDirectoriesAreCreatedAndRemovedAgainWhenTheRunFails() throws Exception {
        var nested = workDir.resolve("created").resolve("ssl");
        assertThat(run(nested, Map.of(), DNS_NAME).exitCode()).isZero();
        assertThat(nested.resolve("server.key")).exists();

        var failing = workDir.resolve("failed").resolve("ssl");
        var bin = Files.createDirectory(workDir.resolve("bin"));
        Files.writeString(bin.resolve("openssl"), "#!/bin/sh\nexit 1\n");
        Files.setPosixFilePermissions(bin.resolve("openssl"), PosixFilePermissions.fromString("rwx------"));
        var path = bin + File.pathSeparator + System.getenv("PATH");

        assertThat(run(failing, Map.of("PATH", path), DNS_NAME).exitCode()).isNotZero();

        assertThat(workDir.resolve("failed")).doesNotExist();
    }

    @Tag(ROOT_TAG)
    @ParameterizedTest
    @ValueSource(strings = {"", "--force-new-ca"})
    void symlinkedOutputDirectoryIsRefusedAndTheTargetIsLeftUntouched(String option) throws Exception {
        var target = Files.createDirectory(workDir.resolve("elsewhere"));
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(target.resolve("keep.txt"), "keep");
        handOverToXroadWhenRoot(target);
        Files.createSymbolicLink(dir, target);
        var before = fileState(target);

        var result = runWithOption(option);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("symbolic link");
        assertThat(Files.isSymbolicLink(dir)).isTrue();
        assertThat(fileState(target)).isEqualTo(before);
    }

    @Tag(ROOT_TAG)
    @ParameterizedTest
    @ValueSource(strings = {"ca.crt", "ca.key"})
    void symlinkedCaFileIsRefusedAndTheTargetAndTheOtherFilesAreLeftUntouched(String caFile) throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var target = workDir.resolve("outside-" + caFile);
        Files.move(dir.resolve(caFile), target);
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r-----"));
        handOverToXroadWhenRoot(target);
        Files.createSymbolicLink(dir.resolve(caFile), target);
        var targetBefore = fileState(target);
        var snapshotBefore = snapshot();
        var listingBefore = listing().stream().sorted().toList();

        for (var option : List.of("", "--force-new-ca")) {
            var result = runWithOption(option);

            assertThat(result.exitCode()).isNotZero();
            assertThat(result.output()).contains("symbolic link");
        }

        assertThat(Files.isSymbolicLink(dir.resolve(caFile))).isTrue();
        assertThat(fileState(target)).isEqualTo(targetBefore);
        assertThat(snapshot()).isEqualTo(snapshotBefore);
        assertThat(listing().stream().sorted().toList()).isEqualTo(listingBefore);
    }

    @Test
    void serverKeyIsUnencryptedPkcs8AndTheCaFileHoldsOneCertificateAndNoPrivateKey() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();

        var serverKey = read("server.key");
        assertThat(serverKey.lines().findFirst()).contains(PKCS8_HEADER);
        assertThat(serverKey).doesNotContain("ENCRYPTED");
        var caFile = read("ca.crt");
        assertThat(caFile.split("-----BEGIN CERTIFICATE-----", -1)).hasSize(2);
        assertThat(caFile).doesNotContain("PRIVATE KEY");
    }

    @Test
    void rerunKeepsTheCaAndReplacesTheServerIdentity() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var before = snapshot();

        assertThat(run(DNS_NAME).exitCode()).isZero();

        var after = snapshot();
        assertThat(after).containsEntry("ca.crt", before.get("ca.crt"));
        assertThat(after).containsEntry("ca.key", before.get("ca.key"));
        assertThat(after).doesNotContainEntry("server.key", before.get("server.key"));
        assertThat(after).doesNotContainEntry("server.crt", before.get("server.crt"));
        var server = certificate("server.crt");
        var newKey = privateKey("server.key");
        var certificateKey = (RSAPublicKey) server.getPublicKey();
        assertThat(certificateKey.getModulus()).isEqualTo(newKey.getModulus());
        assertThat(certificateKey.getPublicExponent()).isEqualTo(newKey.getPublicExponent());
        assertThatCode(() -> server.verify(certificate("ca.crt").getPublicKey())).doesNotThrowAnyException();
    }

    @Test
    void rerunsInSequenceKeepThenReplaceTheCaThenRefuseALongerServerCertificate() throws Exception {
        assertThat(run("--ca-days", "100", "--days", "50", DNS_NAME).exitCode()).isZero();
        var first = snapshot();

        assertThat(run("--days", "50", DNS_NAME).exitCode()).isZero();
        var second = snapshot();
        assertThat(second).containsEntry("ca.crt", first.get("ca.crt"));
        assertThat(second).containsEntry("ca.key", first.get("ca.key"));
        assertThat(second).doesNotContainEntry("server.crt", first.get("server.crt"));

        assertThat(run("--force-new-ca", "--ca-days", "100", "--days", "50", DNS_NAME).exitCode()).isZero();
        var third = snapshot();
        assertThat(third).doesNotContainEntry("ca.crt", second.get("ca.crt"));
        assertThat(third).doesNotContainEntry("ca.key", second.get("ca.key"));
        var newCa = certificate("ca.crt");
        var server = certificate("server.crt");
        assertThatCode(() -> server.verify(newCa.getPublicKey())).doesNotThrowAnyException();
        assertThatThrownBy(() -> server.verify(parseCertificate(second.get("ca.crt")).getPublicKey()))
                .isInstanceOf(GeneralSecurityException.class);

        assertThat(run("--days", "200", DNS_NAME).exitCode()).isNotZero();
        assertThat(snapshot()).isEqualTo(third);
    }

    @Test
    void otherFilesInTheDirectoryAndTheLegacyFilesAreLeftUntouched() throws Exception {
        Files.createDirectory(dir);
        Files.writeString(dir.resolve("notes.txt"), "keep");
        Files.writeString(dir.resolve("ca.srl"), "keep too");
        Files.writeString(workDir.resolve("opmonitor.crt"), "legacy certificate");
        Files.writeString(workDir.resolve("opmonitor.key"), "legacy key");

        assertThat(run(DNS_NAME).exitCode()).isZero();
        assertThat(run(DNS_NAME).exitCode()).isZero();
        assertThat(run("--force-new-ca", DNS_NAME).exitCode()).isZero();

        assertThat(read("notes.txt")).isEqualTo("keep");
        assertThat(read("ca.srl")).isEqualTo("keep too");
        assertThat(Files.readString(workDir.resolve("opmonitor.crt"))).isEqualTo("legacy certificate");
        assertThat(Files.readString(workDir.resolve("opmonitor.key"))).isEqualTo("legacy key");
        assertThat(listing()).containsExactlyInAnyOrder("notes.txt", "ca.srl", "ca.crt", "ca.key", "server.crt", "server.key");
    }

    @Test
    void generatedFilesLoadInTheFileKeyProviderAndTheCaFileIsAcceptedAsTrustedCa() throws Exception {
        assertThat(run(DNS_NAME).exitCode()).isZero();
        var securityServerRpcCa = PemFiles.writeCertificates(workDir.resolve("trusted-ca.crt"),
                TestCa.selfSigned("Security Server RPC CA").certificate());

        assertThatCode(() -> provider(securityServerRpcCa).init()).doesNotThrowAnyException();
        assertThatCode(() -> provider(dir.resolve("ca.crt")).init()).doesNotThrowAnyException();
    }

    @Test
    void validDnsNamesAndAddressesInAllNotationsAreAccepted() throws Exception {
        var longLabel = "a".repeat(63);
        var dnsNames = List.of("localhost", "xn--a-b.example", "1.2.3.example", longLabel + ".example");
        var addresses = List.of("0.0.0.0", "255.255.255.255", "::1", "::", "2001:db8:0:0:0:0:0:1", "1:2:3:4:5:6:7::",
                "::ffff:192.0.2.1", "1:2:3:4:5:6:1.2.3.4", "FD00::AB");
        var args = Stream.concat(dnsNames.stream(), addresses.stream()).toArray(String[]::new);

        assertThat(run(args).exitCode()).isZero();

        var server = certificate("server.crt");
        assertThat(sanValues(server, SAN_DNS)).containsExactlyInAnyOrderElementsOf(dnsNames);
        var expected = new HashSet<InetAddress>();
        for (var address : addresses) {
            expected.add(InetAddress.getByName(address));
        }
        assertThat(ipSans(server)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a..b", ".a", "a.", "a.-b", "a-.b", "a_b", "*.example.com", "a b", "a,b", "", "a\nb"})
    void malformedDnsNamesAreRefusedAndNothingIsWritten(String name) {
        assertRefused("not a valid DNS name", name);
    }

    @Test
    void dnsLabelOfMoreThan63CharactersIsRefusedAndNothingIsWritten() {
        assertRefused("not a valid DNS name", "a".repeat(64) + ".example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.3", "1.2.3.4.5", "999.999.999.999", "256.1.1.1", "1.2.3.256", "01.2.3.4", "1.2.3.", "123",
            "1..2.3"})
    void malformedIpv4AddressesAreRefusedAndNothingIsWritten(String address) {
        assertRefused("not a valid IPv4 address", address);
    }

    @ParameterizedTest
    @ValueSource(strings = {":::::", ":::", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "1::2::3", "12345::1", "g::1", "1:2:3:4:5:6:7::8",
            "::1.2.3", "::1.2.3.999", ":1::2", "1::2:", "1:2:3:4:5:6:7:", "[::1]", "fe80::1%eth0", "fd00::5,DNS:evil",
            "fd00::5\nDNS:evil"})
    void malformedIpv6AddressesAreRefusedAndNothingIsWritten(String address) {
        assertRefused("not a valid IPv6 address", address);
    }

    @Test
    void oneMalformedNameAmongValidOnesRefusesTheWholeRun() {
        assertRefused("not a valid DNS name", DNS_NAME, IPV4, "a..b");
    }

    private static Stream<Arguments> invalidDayCounts() {
        return Stream.of("--days", "--ca-days").flatMap(option ->
                Stream.of("abc", "0", "-5", "1.5", "07", "1000000", "").map(value -> Arguments.of(option, value)));
    }

    private List<Path> ancestorChain() throws IOException {
        var outer = Files.createDirectory(workDir.resolve("outer"));
        var inner = Files.createDirectory(outer.resolve("inner"));
        return List.of(outer, inner);
    }

    private void assertRefusedUnderAncestors(List<Path> ancestors) throws IOException {
        var inner = ancestors.getLast();
        var output = inner.resolve("ssl");

        var result = run(output, Map.of(), DNS_NAME);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("not controlled by root");
        assertThat(output).doesNotExist();
        try (var files = Files.list(inner)) {
            assertThat(files).isEmpty();
        }
    }

    private String fakeOpensslFailingOn(Path calls) throws Exception {
        var bin = Files.createDirectory(workDir.resolve("bin"));
        var fake = bin.resolve("openssl");
        Files.writeString(fake, """
                #!/bin/sh
                echo "$1" >> '%s'
                for argument in "$@"; do
                  [ "$argument" = '%s' ] && exit 1
                done
                exec '%s' "$@"
                """.formatted(calls, "-req", realOpenssl()));
        Files.setPosixFilePermissions(fake, PosixFilePermissions.fromString("rwx------"));
        return bin + File.pathSeparator + System.getenv("PATH");
    }

    private FileKeyProvider provider(Path trustedCa) throws Exception {
        return new FileKeyProvider(new RpcProperties.FileIdentity(dir.resolve("server.key"), dir.resolve("server.crt"), trustedCa));
    }

    private void assertRefused(String message, String... args) {
        var result = run(args);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(message);
        assertThat(dir).doesNotExist();
    }

    private Result run(String... args) {
        return run(dir, Map.of(), args);
    }

    private Result run(Path outputDir, Map<String, String> extraEnvironment, String... args) {
        var command = Stream.concat(Stream.of("bash", SCRIPT.toString()), Stream.of(args)).toList();
        var environment = new HashMap<>(extraEnvironment);
        environment.put(DIR_VARIABLE, outputDir.toString());
        return execute(command, environment);
    }

    private Result runWithOption(String option) {
        return option.isEmpty() ? run(DNS_NAME) : run(option, DNS_NAME);
    }

    private void handOverToXroadWhenRoot(Path path) throws IOException {
        if (isRoot()) {
            var xroad = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("xroad");
            Files.setOwner(path, xroad);
        }
    }

    private static List<String> fileState(Path path) throws IOException {
        var state = new ArrayList<String>();
        state.add(Files.getOwner(path).getName());
        state.add(PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                files.map(p -> p.getFileName().toString()).sorted().forEach(state::add);
            }
        } else {
            state.add(Files.readString(path));
        }
        return state;
    }

    private Result execute(List<String> command, Map<String, String> environment) {
        try {
            var outputFile = Files.createTempFile(workDir, "process", ".out");
            var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile());
            builder.environment().remove(DIR_VARIABLE);
            builder.environment().putAll(environment);
            var process = builder.start();
            if (!process.waitFor(GenerateOpMonitorRpcIdentityScriptTest.PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                return fail("process timed out after %s, output so far: %s", GenerateOpMonitorRpcIdentityScriptTest.PROCESS_TIMEOUT,
                        Files.readString(outputFile));
            }
            return new Result(process.exitValue(), Files.readString(outputFile));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void terminate(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Map<String, String> snapshot() throws IOException {
        var contents = new TreeMap<String, String>();
        for (var name : OUTPUT_FILES) {
            contents.put(name, read(name));
        }
        return contents;
    }

    private Path scriptWithDefaultParent(Path sandbox) throws IOException {
        var lines = Arrays.asList(Files.readString(SCRIPT).split("\n", -1));
        assertThat(Collections.frequency(lines, DEFAULT_PARENT_LINE)).as(DEFAULT_PARENT_LINE).isOne();
        assertThat(Collections.frequency(lines, DEFAULT_DIR_LINE)).as(DEFAULT_DIR_LINE).isOne();
        var sandboxed = lines.stream()
                .map(line -> line.equals(DEFAULT_PARENT_LINE) ? "DEFAULT_PARENT='" + sandbox + "'" : line)
                .collect(Collectors.joining("\n"));
        return Files.writeString(workDir.resolve("sandboxed-script.sh"), sandboxed);
    }

    private static Map<String, List<String>> sandboxState(Path sandbox) throws IOException {
        var sandboxDir = sandbox.resolve("ssl");
        var state = new TreeMap<String, List<String>>();
        state.put(sandbox.toString(), fileState(sandbox));
        state.put(sandboxDir.toString(), fileState(sandboxDir));
        for (var name : OUTPUT_FILES) {
            var file = sandboxDir.resolve(name);
            var fileState = new ArrayList<>(fileState(file));
            fileState.add(Files.size(file) + " bytes, modified " + Files.getLastModifiedTime(file));
            state.put(file.toString(), fileState);
        }
        return state;
    }

    private List<String> listing() throws IOException {
        try (var files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).toList();
        }
    }

    private String read(String name) throws IOException {
        return Files.readString(dir.resolve(name));
    }

    private String mode(String name) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(dir.resolve(name));
        return PosixFilePermissions.toString(permissions);
    }

    private X509Certificate certificate(String name) throws Exception {
        return parseCertificate(read(name));
    }

    private static X509Certificate parseCertificate(String pem) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }

    private static Set<String> sanValues(X509Certificate certificate, int type) throws Exception {
        var values = new HashSet<String>();
        for (var entry : certificate.getSubjectAlternativeNames()) {
            if (entry.getFirst().equals(type)) {
                values.add((String) entry.get(1));
            }
        }
        return values;
    }

    private static Set<InetAddress> ipSans(X509Certificate certificate) throws Exception {
        var addresses = new HashSet<InetAddress>();
        for (var value : sanValues(certificate, SAN_IP)) {
            addresses.add(InetAddress.getByName(value));
        }
        return addresses;
    }

    private RSAPrivateCrtKey privateKey(String name) throws Exception {
        var base64 = read(name).lines().filter(line -> !line.startsWith("-----")).reduce("", String::concat);
        var der = Base64.getDecoder().decode(base64);
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static Set<Integer> keyUsages(X509Certificate certificate) {
        var usages = new HashSet<Integer>();
        var flags = certificate.getKeyUsage();
        for (var i = 0; i < flags.length; i++) {
            if (flags[i]) {
                usages.add(i);
            }
        }
        return usages;
    }

    private static byte[] keyIdentifier(X509Certificate certificate, String extensionOid) {
        var value = certificate.getExtensionValue(extensionOid);
        assertThat(value).isNotNull();
        return Arrays.copyOfRange(value, value.length - KEY_IDENTIFIER_LENGTH, value.length);
    }

    private static String realOpenssl() throws IOException, InterruptedException {
        var process = new ProcessBuilder("sh", "-c", "command -v openssl").redirectErrorStream(true).start();
        var path = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertThat(process.waitFor()).isZero();
        return path;
    }

    private static long validityDays(X509Certificate certificate) {
        return Duration.between(certificate.getNotBefore().toInstant(), certificate.getNotAfter().toInstant()).toDays();
    }

    private static boolean opensslAvailable() {
        try {
            var process = new ProcessBuilder("openssl", "version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException _) {
            return false;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isRoot() {
        try {
            var process = new ProcessBuilder("id", "-u").redirectErrorStream(true).start();
            var uid = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("id -u failed: " + uid);
            }
            return Integer.parseInt(uid) == 0;
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("cannot determine the effective user", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static boolean serverUserExists() {
        try {
            return new ProcessBuilder("id", "xroad").redirectErrorStream(true).start().waitFor() == 0;
        } catch (IOException _) {
            return false;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record Result(int exitCode, String output) {
    }

    static class RootControlledTempDirFactory implements TempDirFactory {
        private static final Path ROOT_PARENT = Path.of("/var/lib/xroad-opmonitor-script-test");

        @Override
        public Path createTempDirectory(AnnotatedElementContext elementContext, ExtensionContext extensionContext)
                throws IOException {
            if (!isRoot()) {
                return Files.createTempDirectory("opmonitor-script-");
            }
            Files.createDirectories(ROOT_PARENT);
            Files.setPosixFilePermissions(ROOT_PARENT, PosixFilePermissions.fromString("rwxr-xr-x"));
            return Files.createTempDirectory(ROOT_PARENT, "opmonitor-script-");
        }
    }
}
