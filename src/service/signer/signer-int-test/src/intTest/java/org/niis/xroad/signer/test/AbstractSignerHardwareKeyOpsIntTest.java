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
package org.niis.xroad.signer.test;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import lombok.SneakyThrows;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties.NodeType;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA256;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.PRIMARY;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.SECONDARY;

/**
 * Shared scenario bodies for the hardware-token RSA (0210) and EC (0220) key-operations classes. SoftHSM
 * emulates the hardware token in-container; unlike the software-token classes, an authentication CSR
 * cannot be created at all (hardware tokens only support signing certs) and there is no OCSP coverage.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:MagicNumber")
abstract class AbstractSignerHardwareKeyOpsIntTest extends AbstractSignerIntTest {

    private static final String MEMBER_1 = "DEV:test:member-1";
    private static final String MEMBER_2 = "DEV:test:member-2";
    private static final String MANAGEMENT_CLIENT = "DEV:COM:1234:MANAGEMENT";

    private String scenarioKeyId;
    private String scenarioCsrId;
    private String certHash;
    private CertificateInfo certInfo;
    private byte[] scenarioCert;
    private byte[] lastCsrBytes;
    private byte[] lastCertBytes;

    protected abstract String tokenFriendlyName();

    protected abstract KeyAlgorithm keyAlgorithm();

    @BeforeEach
    void listTokensBeforeEachScenario() {
        listTokens();
    }

    @Test
    @Order(10)
    @DisplayName("HSM is operational")
    void hsmIsOperational() {
        Step.given("HSM is operational", () -> assertThat(client().isHSMOperational()).isTrue());
    }

    @Test
    @Order(20)
    @DisplayName("Keys are generated")
    void keysAreGenerated() {
        Step.when("new key 'key-1' generated for token", () -> generateKey("First key"));
        Step.when("new key 'key-2' generated for token", () -> generateKey("Second key"));
        Step.when("new key 'key-3' generated for token", () -> generateKey("Third key"));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token has exact keys 'First key,Second key,Third key'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "Third key")));
        Step.and("token has exact keys 'First key,Second key,Third key' on secondary node",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "Third key"), SECONDARY));
        Step.and("sign mechanism for key 'Second key' is not null",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "Second key").getSignMechanismName()).isNotBlank());
        Step.and("sign mechanism for key 'Second key' is not null on secondary node",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "Second key", SECONDARY).getSignMechanismName()).isNotBlank());
    }

    @Test
    @Order(30)
    @DisplayName("Key is deleted")
    void keyIsDeleted() {
        Step.given("new key 'key-X' generated for token", () -> generateKey("KeyX"));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token info can be retrieved by key id", () -> assertTokenInfoRetrievableByKeyId(PRIMARY));
        Step.and("token info can be retrieved by key id on secondary node", () -> assertTokenInfoRetrievableByKeyId(SECONDARY));
        Step.when("key 'Third key' is deleted from token", () -> deleteKey("Third key"));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token has exact keys 'First key,Second key,KeyX'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX")));
        Step.and("token has exact keys 'First key,Second key,KeyX' on secondary node",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX"), SECONDARY));
    }

    @Test
    @Order(40)
    @DisplayName("Cert request is (re)generated")
    void certRequestIsRegenerated() {
        Step.when("the SIGNING cert request is generated for key 'Second key' for client member-2",
                () -> generateCertRequest("SIGNING", "Second key", MEMBER_2));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.and("token and key can be retrieved by cert request", () -> assertTokenAndKeyRetrievableByCertRequest(PRIMARY));
        Step.and("token and key can be retrieved by cert request on secondary node",
                () -> assertTokenAndKeyRetrievableByCertRequest(SECONDARY));
        Step.then("cert request can be deleted", () -> client().deleteCertRequest(scenarioCsrId));
        Step.when("the SIGNING cert request is generated for key 'Second key' for client member-2 (again)",
                () -> generateCertRequest("SIGNING", "Second key", MEMBER_2));
        Step.and("cert request is regenerated", () -> client().regenerateCertRequest(scenarioCsrId, CertificateRequestFormat.DER));
    }

    @Test
    @Order(50)
    @DisplayName("A key with Sign certificate is created")
    void keyWithSignCertificateIsCreated() {
        Step.given("new key 'key-100' generated for token", () -> generateKey("SignKey from CA"));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.and("token has exact keys 'First key,Second key,KeyX,SignKey from CA'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX", "SignKey from CA")));
        Step.and("token has exact keys '...' on secondary node",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX", "SignKey from CA"), SECONDARY));
        Step.and("sign mechanism for key 'SignKey from CA' is not null",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "SignKey from CA").getSignMechanismName()).isNotBlank());
        Step.and("sign mechanism for key 'SignKey from CA' is not null on secondary node",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "SignKey from CA", SECONDARY).getSignMechanismName()).isNotBlank());
        Step.when("the SIGNING cert request is generated for key 'SignKey from CA' for client MANAGEMENT",
                () -> generateCertRequest("SIGNING", "SignKey from CA", MANAGEMENT_CLIENT));
        Step.and("SIGN CSR is processed by test CA", () -> processCsr(AbstractSignerIntTest.CsrType.SIGN));
        Step.and("Generated certificate with initial status 'registered' is imported for MANAGEMENT",
                () -> importCertFromFile(CertificateInfo.STATUS_REGISTERED, MANAGEMENT_CLIENT));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token info can be retrieved by key id", () -> assertTokenInfoRetrievableByKeyId(PRIMARY));
        Step.then("token info can be retrieved by key id on secondary node", () -> assertTokenInfoRetrievableByKeyId(SECONDARY));
    }

    @Test
    @Order(60)
    @DisplayName("A key with Auth certificate is not created in hardware token")
    void keyWithAuthCertificateIsNotCreatedInHardwareToken() {
        Step.given("new key 'key-200' generated for token", () -> generateKey("BadAuthKey from CA"));
        Step.when("token has exact keys '...,BadAuthKey from CA'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX", "SignKey from CA", "BadAuthKey from CA")));
        Step.then("the AUTHENTICATION cert request is generated for key 'BadAuthKey from CA' for client MANAGEMENT throws exception",
                this::assertAuthCertRequestOnHardwareTokenThrowsException);
    }

    @Test
    @Order(70)
    @DisplayName("Self signed certificate is generated")
    void selfSignedCertificateIsGenerated() {
        Step.given("token key 'First key' has 0 certificates", () -> assertKeyHasCertificateCount("First key", 0));
        Step.when("self signed cert generated for key 'First key', client member-1",
                () -> generateSelfSignedCert("First key", MEMBER_1));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token key 'First key' has 1 certificates", () -> assertKeyHasCertificateCount("First key", 1));
        Step.and("keyId can be retrieved by cert hash", () -> assertKeyIdRetrievableByCertHash(PRIMARY));
        Step.and("token and keyId can be retrieved by cert hash", () -> assertTokenAndKeyIdRetrievableByCertHash(PRIMARY));
        Step.then("token key 'First key' has 1 certificates on secondary node",
                () -> assertKeyHasCertificateCount("First key", 1, SECONDARY));
        Step.and("keyId can be retrieved by cert hash on secondary node", () -> assertKeyIdRetrievableByCertHash(SECONDARY));
        Step.and("token and keyId can be retrieved by cert hash on secondary node",
                () -> assertTokenAndKeyIdRetrievableByCertHash(SECONDARY));
        Step.and("certificate can be signed using key 'First key'", this::assertCertificateCanBeSignedUsingFirstKey);
    }

    @Test
    @Order(80)
    @DisplayName("Self Signed Certificate can be (re)imported")
    void selfSignedCertificateCanBeReimported() {
        Step.given("tokens list contains token", () -> assertThat(tokenIdByFriendlyName(tokenFriendlyName())).isNotNull());
        Step.when("Wrong Certificate is not imported for client member-2", this::assertWrongCertificateImportFails);
        Step.and("self signed cert generated for key 'Second key', client member-2",
                () -> generateSelfSignedCert("Second key", MEMBER_2));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.and("certificate info can be retrieved by cert hash", () -> assertCertificateRetrievableByHash(PRIMARY));
        Step.and("certificate info can be retrieved by cert hash on secondary node",
                () -> assertCertificateRetrievableByHash(SECONDARY));
        Step.when("certificate can be deleted", () -> client().deleteCert(certInfo.getId()));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token key 'Second key' has 0 certificates", () -> assertKeyHasCertificateCount("Second key", 0));
        Step.then("token key 'Second key' has 0 certificates on secondary node",
                () -> assertKeyHasCertificateCount("Second key", 0, SECONDARY));
        Step.when("Certificate is imported for client member-2",
                () -> scenarioKeyId = client().importCert(scenarioCert, CertificateInfo.STATUS_REGISTERED, clientId(MEMBER_2)));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token key 'Second key' has 1 certificates", () -> assertKeyHasCertificateCount("Second key", 1));
        Step.then("token key 'Second key' has 1 certificates on secondary node",
                () -> assertKeyHasCertificateCount("Second key", 1, SECONDARY));
    }

    @Test
    @Order(90)
    @DisplayName("Sign fails with an unknown algorithm error")
    void signFailsWithUnknownAlgorithmError() {
        Step.given("digest can be signed using key 'KeyX'", () -> assertCanSign("KeyX"));
        Step.and("Signing with unknown algorithm fails using key 'KeyX'", this::assertSignWithUnknownAlgorithmFails);
    }

    @Test
    @Order(100)
    @DisplayName("Sign data is successful")
    void signDataIsSuccessful() {
        Step.given("digest can be signed using key 'SignKey from CA'", () -> assertCanSign("SignKey from CA"));
        Step.and("digest can be signed using key 'KeyX'", () -> assertCanSign("KeyX"));
    }

    @Test
    @Order(110)
    @DisplayName("Member signing info can be retrieved")
    void memberSigningInfoCanBeRetrieved() {
        Step.given("tokens list contains token", () -> assertThat(tokenIdByFriendlyName(tokenFriendlyName())).isNotNull());
        Step.and("Member signing info for client MANAGEMENT is retrieved",
                () -> client().getMemberSigningInfo(clientId(MANAGEMENT_CLIENT)));
    }

    @Test
    @Order(120)
    @DisplayName("Member certs are retrieved")
    void memberCertsAreRetrieved() {
        Step.then("member '%s' has 2 certificate".formatted(MEMBER_1), () -> assertMemberHasCertificateCount(MEMBER_1, 2, PRIMARY));
        Step.then("member '%s' has 2 certificate on secondary node".formatted(MEMBER_1),
                () -> assertMemberHasCertificateCount(MEMBER_1, 2, SECONDARY));
    }

    @Test
    @Order(130)
    @DisplayName("Cert status can be updated")
    void certStatusCanBeUpdated() {
        Step.given("self signed cert generated for key 'KeyX', client member-2", () -> generateSelfSignedCert("KeyX", MEMBER_2));
        Step.and("certificate info can be retrieved by cert hash", () -> assertCertificateRetrievableByHash(PRIMARY));
        Step.then("certificate can be deactivated", () -> client().deactivateCert(certInfo.getId()));
        Step.and("certificate can be activated", () -> client().activateCert(certInfo.getId()));
        Step.and("certificate status can be changed to 'deletion in progress'",
                () -> client().setCertStatus(certInfo.getId(), "deletion in progress"));
        Step.and("certificate can be deleted", () -> client().deleteCert(certInfo.getId()));
    }

    @Test
    @Order(140)
    @DisplayName("Miscellaneous checks")
    void miscellaneousChecks() {
        Step.given("check key 'First key' batch signing enabled", () ->
                assertThat(client().isTokenBatchSigningEnabled(findKeyInToken(tokenFriendlyName(), "First key").getId())).isNotNull());
    }

    protected void generateKey(String friendlyName) {
        var tokenId = tokenIdByFriendlyName(tokenFriendlyName());
        var keyInfo = client().generateKey(tokenId, friendlyName, keyAlgorithm());
        scenarioKeyId = keyInfo.getId();
        client().setKeyFriendlyName(scenarioKeyId, friendlyName);
    }

    protected void deleteKey(String keyName) {
        client().deleteKey(findKeyInToken(tokenFriendlyName(), keyName).getId(), true);
    }

    protected void assertTokenHasExactKeys(List<String> keyNames) {
        assertTokenHasExactKeys(keyNames, PRIMARY);
    }

    protected void assertTokenHasExactKeys(List<String> keyNames, NodeType nodeType) {
        var token = tokenInfoByFriendlyName(tokenFriendlyName(), nodeType);
        assertThat(token.getKeyInfo()).hasSize(keyNames.size());
        assertThat(token.getKeyInfo().stream().map(KeyInfo::getFriendlyName).toList())
                .containsExactlyInAnyOrderElementsOf(keyNames);
    }

    private void assertTokenInfoRetrievableByKeyId(NodeType nodeType) {
        assertThat(client(nodeType).getTokenForKeyId(scenarioKeyId)).isNotNull();
    }

    private void generateCertRequest(String keyUsage, String keyName, String forClient) {
        var key = findKeyInToken(tokenFriendlyName(), keyName);
        SignerRpcClient.GeneratedCertRequestInfo csrInfo = client().generateCertRequest(key.getId(), clientId(forClient),
                KeyUsageInfo.valueOf(keyUsage), "CN=key-" + keyName, CertificateRequestFormat.DER);
        scenarioCsrId = csrInfo.certReqId();
        lastCsrBytes = csrInfo.certRequest();
    }

    private void assertAuthCertRequestOnHardwareTokenThrowsException() {
        try {
            generateCertRequest("AUTHENTICATION", "BadAuthKey from CA", MANAGEMENT_CLIENT);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.wrong_cert_usage",
                    "\\[.*?\\] signer\\.wrong_cert_usage: Authentication certificate requests can only be created under "
                            + "software tokens", e);
        }
    }

    private void processCsr(AbstractSignerIntTest.CsrType type) {
        File csrFile = createTempFile(csrFile(type));
        writeBytes(csrFile, lastCsrBytes);
        File cert = signCsr(csrFile, type);
        lastCertBytes = readBytes(cert);
    }

    private String csrFile(AbstractSignerIntTest.CsrType type) {
        return type.name().toLowerCase() + "_csr" + System.currentTimeMillis();
    }

    @SneakyThrows
    private void importCertFromFile(String initialStatus, String forClient) {
        scenarioKeyId = client().importCert(lastCertBytes, initialStatus, clientId(forClient));
        X509Certificate x509Certificate = readCertificate(lastCertBytes);
        scenarioCert = x509Certificate.getEncoded();
        certInfo = client().getCertForHash(calculateCertHexHash(scenarioCert));
    }

    private void assertTokenAndKeyRetrievableByCertRequest(NodeType nodeType) {
        assertThat(client(nodeType).getTokenAndKeyIdForCertRequestId(scenarioCsrId)).isNotNull();
    }

    private void assertWrongCertificateImportFails() {
        byte[] certBytes = readClasspathBytes("cert-01.pem");
        try {
            client().importCert(certBytes, CertificateInfo.STATUS_REGISTERED, clientId(MEMBER_2));
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.key_not_found",
                    "\\[.*?\\] signer\\.key_not_found: Could not find key that has public key that matches "
                            + "the public key of certificate", e);
        }
    }

    @SneakyThrows
    private void generateSelfSignedCert(String keyName, String forClient) {
        var key = findKeyInToken(tokenFriendlyName(), keyName);
        scenarioCert = client().generateSelfSignedCert(key.getId(), clientId(forClient), KeyUsageInfo.SIGNING,
                forClient, Date.from(now().minus(5, DAYS)), Date.from(now().plus(5, DAYS)));
        certHash = calculateCertHexHash(scenarioCert);
    }

    private void assertCertificateRetrievableByHash(NodeType nodeType) {
        certInfo = client(nodeType).getCertForHash(certHash);
        assertThat(certInfo).isNotNull();
    }

    private void assertKeyHasCertificateCount(String keyName, int count) {
        assertKeyHasCertificateCount(keyName, count, PRIMARY);
    }

    private void assertKeyHasCertificateCount(String keyName, int count, NodeType nodeType) {
        assertThat(findKeyInToken(tokenFriendlyName(), keyName, nodeType).getCerts()).hasSize(count);
    }

    private void assertKeyIdRetrievableByCertHash(NodeType nodeType) {
        assertThat(client(nodeType).getKeyIdForCertHash(certHash)).isNotNull();
    }

    private void assertTokenAndKeyIdRetrievableByCertHash(NodeType nodeType) {
        assertThat(client(nodeType).getTokenAndKeyIdForCertHash(certHash)).isNotNull();
    }

    @SneakyThrows
    private void assertCertificateCanBeSignedUsingFirstKey() {
        var key = findKeyInToken(tokenFriendlyName(), "First key");
        byte[] keyBytes = Base64.decode(key.getPublicKey().getBytes());
        var x509publicKey = new X509EncodedKeySpec(keyBytes);
        var algorithm = SignMechanism.valueOf(key.getSignMechanismName()).keyAlgorithm();
        var kf = KeyFactory.getInstance(algorithm.name());
        var publicKey = kf.generatePublic(x509publicKey);

        final byte[] bytes = signClient(PRIMARY).signCertificate(key.getId(), signAlgorithmFor(key), "CN=CS", publicKey);
        assertThat(bytes).isNotEmpty();
    }

    private void assertMemberHasCertificateCount(String memberId, int count, NodeType nodeType) {
        assertThat(client(nodeType).getMemberCerts(clientId(memberId))).hasSize(count);
    }

    @SneakyThrows
    private void assertCanSign(String keyName) {
        var key = findKeyInToken(tokenFriendlyName(), keyName);
        var digest = "%s-%d".formatted(randomUUID(), System.currentTimeMillis());
        byte[] bytes = signClient(PRIMARY).sign(key.getId(), signAlgorithmFor(key), calculateDigest(SHA256, digest.getBytes(UTF_8)));
        assertThat(bytes).isNotEmpty();
    }

    @SneakyThrows
    private void assertSignWithUnknownAlgorithmFails() {
        try {
            var key = findKeyInToken(tokenFriendlyName(), "KeyX");
            signClient(PRIMARY).sign(key.getId(), SignAlgorithm.ofName("NOT-ALGORITHM-ID"),
                    calculateDigest(SHA256, "digest".getBytes(UTF_8)));
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.cannot_sign.internal_error",
                    "\\[.*?\\] signer\\.cannot_sign\\.internal_error: Unknown sign mechanism of signature algorithm:"
                            + " uSA\\[name=NOT-ALGORITHM-ID, uri=null\\]", e);
        }
    }

    private byte[] readClasspathBytes(String path) {
        try (var in = requireClasspathStream(path)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InputStream requireClasspathStream(String path) {
        var in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Classpath resource not found: " + path);
        }
        return in;
    }

    private File createTempFile(String suffix) {
        try {
            return File.createTempFile("tmp", suffix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeBytes(File file, byte[] bytes) {
        try {
            Files.write(file.toPath(), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] readBytes(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
