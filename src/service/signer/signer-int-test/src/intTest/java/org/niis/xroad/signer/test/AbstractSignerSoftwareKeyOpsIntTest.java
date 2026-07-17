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

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import lombok.SneakyThrows;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
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
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
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
 * Shared scenario bodies for the software-token RSA (0110) and EC (0120) key-operations classes: both
 * generate, use and retire keys/certs on the same token shape, differing only in key algorithm and
 * (for EC) an extra scenario that deletes the previous class's leftover keys first.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:MagicNumber")
abstract class AbstractSignerSoftwareKeyOpsIntTest extends AbstractSignerIntTest {

    private static final String MEMBER_1 = "DEV:test:member-1";
    private static final String MEMBER_2 = "DEV:test:member-2";
    private static final String MANAGEMENT_CLIENT = "DEV:COM:1234:MANAGEMENT";
    private static final String SS100 = "DEV:COM:1234:SS100";

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
    @Order(1)
    @DisplayName("Keys are generated")
    void keysAreGenerated() {
        Step.when("new RSA key 'key-1' generated for token 'soft-token-000'", () -> generateKey("First key"));
        Step.when("new RSA key 'key-2' generated for token 'soft-token-000'", () -> generateKey("Second key"));
        Step.when("new RSA key 'key-3' generated for token 'soft-token-000'", () -> generateKey("Third key"));
        Step.then("token 'soft-token-000' has exact keys 'First key,Second key,Third key'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "Third key")));
        Step.and("sign mechanism for token 'soft-token-000' key 'Second key' is not null",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "Second key").getSignMechanismName()).isNotBlank());
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token 'soft-token-000' has exact keys 'First key,Second key,Third key' on secondary node",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "Third key"), SECONDARY));
        Step.and("sign mechanism for token 'soft-token-000' key 'Second key' is not null on secondary node",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "Second key", SECONDARY).getSignMechanismName()).isNotBlank());
        Step.and("set name for key not allowed on secondary node",
                () -> assertAccessDenied(() -> client(SECONDARY).setKeyFriendlyName(scenarioKeyId, "keyFriendlyName")));
    }

    @Test
    @Order(2)
    @DisplayName("Key is deleted")
    void keyIsDeleted() {
        Step.given("new RSA key 'key-X' generated for token 'soft-token-000'", () -> generateKey("KeyX"));
        Step.then("token info can be retrieved by key id", () -> assertTokenInfoRetrievableByKeyId());
        Step.when("key 'Third key' is deleted from token 'soft-token-000'", () -> deleteKey("Third key"));
        Step.then("token 'soft-token-000' has exact keys 'First key,Second key,KeyX'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX")));
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token 'soft-token-000' has exact keys 'First key,Second key,KeyX' on secondary node",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX"), SECONDARY));
    }

    @Test
    @Order(3)
    @DisplayName("A key with Sign certificate is created")
    void keyWithSignCertificateIsCreated() {
        Step.given("new RSA key 'key-10' generated for token 'soft-token-000'", () -> generateKey("SignKey from CA"));
        Step.and("token 'soft-token-000' has exact keys 'First key,Second key,KeyX,SignKey from CA'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX", "SignKey from CA")));
        Step.and("sign mechanism for token 'soft-token-000' key 'SignKey from CA' is not null",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "SignKey from CA").getSignMechanismName()).isNotBlank());
        Step.when("the SIGNING cert request is generated for key 'SignKey from CA' for client MANAGEMENT",
                () -> generateCertRequest("SIGNING", "SignKey from CA", MANAGEMENT_CLIENT));
        Step.and("SIGN CSR is processed by test CA", () -> processCsr(AbstractSignerIntTest.CsrType.SIGN));
        Step.and("Generated certificate with initial status 'registered' is imported for MANAGEMENT",
                () -> importCertFromFile(CertificateInfo.STATUS_REGISTERED, MANAGEMENT_CLIENT));
        Step.then("token info can be retrieved by key id", () -> assertTokenInfoRetrievableByKeyId());
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token info can be retrieved by key id on secondary node", () -> assertTokenInfoRetrievableByKeyId(SECONDARY));
    }

    @Test
    @Order(4)
    @DisplayName("A key with Auth certificate is created")
    void keyWithAuthCertificateIsCreated() {
        Step.given("new RSA key 'key-20' generated for token 'soft-token-000'", () -> generateKey("AuthKey from CA"));
        Step.and("token 'soft-token-000' has exact keys '...,AuthKey from CA'",
                () -> assertTokenHasExactKeys(List.of("First key", "Second key", "KeyX", "SignKey from CA", "AuthKey from CA")));
        Step.and("sign mechanism for token 'soft-token-000' key 'AuthKey from CA' is not null",
                () -> assertThat(findKeyInToken(tokenFriendlyName(), "AuthKey from CA").getSignMechanismName()).isNotBlank());
        Step.when("the AUTHENTICATION cert request is generated for key 'AuthKey from CA' for client MANAGEMENT",
                () -> generateCertRequest("AUTHENTICATION", "AuthKey from CA", MANAGEMENT_CLIENT));
        Step.and("CSR is processed by test CA", () -> processCsr(AbstractSignerIntTest.CsrType.AUTO));
        Step.and("Generated certificate with initial status 'registered' is imported for MANAGEMENT",
                () -> importCertFromFile(CertificateInfo.STATUS_REGISTERED, MANAGEMENT_CLIENT));
        Step.then("token info can be retrieved by key id", () -> assertTokenInfoRetrievableByKeyId());
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token info can be retrieved by key id on secondary node", () -> assertTokenInfoRetrievableByKeyId(SECONDARY));
    }

    @Test
    @Order(5)
    @DisplayName("Sign fails with an unknown algorithm error")
    void signFailsWithUnknownAlgorithmError() {
        Step.given("digest can be signed using key 'KeyX' from token 'soft-token-000'", () -> assertCanSign("KeyX"));
        Step.and("Signing with unknown algorithm fails using key 'KeyX' from token 'soft-token-000'",
                this::assertSignWithUnknownAlgorithmFails);
    }

    @Test
    @Order(6)
    @DisplayName("Generate/Regenerate cert request")
    void generateRegenerateCertRequest() {
        Step.when("the SIGNING cert request is generated for key 'Second key' for client member-2",
                () -> generateCertRequest("SIGNING", "Second key", MEMBER_2));
        Step.and("token and key can be retrieved by cert request", () -> assertTokenAndKeyRetrievableByCertRequest());
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token and key can be retrieved by cert request on secondary node",
                () -> assertTokenAndKeyRetrievableByCertRequest(SECONDARY));
        Step.then("cert request can be deleted", () -> client().deleteCertRequest(scenarioCsrId));
        Step.when("the SIGNING cert request is generated for key 'Second key' for client member-2 (again)",
                () -> generateCertRequest("SIGNING", "Second key", MEMBER_2));
        Step.and("cert request is regenerated", () -> client().regenerateCertRequest(scenarioCsrId, CertificateRequestFormat.DER));
    }

    @Test
    @Order(7)
    @DisplayName("Self Signed certificate can be (re)imported")
    void selfSignedCertificateCanBeReimported() {
        Step.given("tokens list contains token 'soft-token-000'",
                () -> assertThat(tokenIdByFriendlyName(tokenFriendlyName())).isNotNull());
        Step.when("Wrong Certificate is not imported for client member-2", this::assertWrongCertificateImportFails);
        Step.and("self signed cert generated for key 'Second key', client member-2",
                () -> generateSelfSignedCert("Second key", MEMBER_2));
        Step.and("certificate info can be retrieved by cert hash", () -> assertCertificateRetrievableByHash());
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("certificate info can be retrieved by cert hash on secondary node",
                () -> assertCertificateRetrievableByHash(SECONDARY));
        Step.when("certificate can be deleted", () -> client().deleteCert(certInfo.getId()));
        Step.then("token 'soft-token-000' key 'Second key' has 0 certificates",
                () -> assertKeyHasCertificateCount("Second key", 0));
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.and("token 'soft-token-000' key 'Second key' has 0 certificates on secondary node",
                () -> assertKeyHasCertificateCount("Second key", 0, SECONDARY));
        Step.when("Certificate is imported for client member-2",
                () -> scenarioKeyId = client().importCert(scenarioCert, CertificateInfo.STATUS_REGISTERED, clientId(MEMBER_2)));
        Step.then("token 'soft-token-000' key 'Second key' has 1 certificates", () -> assertKeyHasCertificateCount("Second key", 1));
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.and("token 'soft-token-000' key 'Second key' has 1 certificates on secondary node",
                () -> assertKeyHasCertificateCount("Second key", 1, SECONDARY));
    }

    @Test
    @Order(8)
    @DisplayName("Self signed certificate")
    void selfSignedCertificate() {
        Step.given("token 'soft-token-000' key 'First key' has 0 certificates", () -> assertKeyHasCertificateCount("First key", 0));
        Step.when("self signed cert generated for key 'First key', client member-1",
                () -> generateSelfSignedCert("First key", MEMBER_1));
        Step.then("token 'soft-token-000' key 'First key' has 1 certificates", () -> assertKeyHasCertificateCount("First key", 1));
        Step.and("keyId can be retrieved by cert hash", () -> assertKeyIdRetrievableByCertHash());
        Step.and("token and keyId can be retrieved by cert hash", () -> assertTokenAndKeyIdRetrievableByCertHash());
        Step.and("certificate can be signed using key 'First key' from token 'soft-token-000'",
                this::assertCertificateCanBeSignedUsingFirstKey);
        Step.when("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("keyId can be retrieved by cert hash on secondary node", () -> assertKeyIdRetrievableByCertHash(SECONDARY));
        Step.and("token and keyId can be retrieved by cert hash on secondary node",
                () -> assertTokenAndKeyIdRetrievableByCertHash(SECONDARY));
    }

    @Test
    @Order(9)
    @DisplayName("Member signing info can be retrieved")
    void memberSigningInfoCanBeRetrieved() {
        Step.given("tokens list contains token 'soft-token-000'",
                () -> assertThat(tokenIdByFriendlyName(tokenFriendlyName())).isNotNull());
        Step.and("Member signing info for client MANAGEMENT is retrieved",
                () -> client().getMemberSigningInfo(clientId(MANAGEMENT_CLIENT)));
    }

    @Test
    @Order(10)
    @DisplayName("Member certs are retrieved")
    void memberCertsAreRetrieved() {
        Step.then("member '%s' has 1 certificate".formatted(MEMBER_1), () -> assertMemberHasCertificateCount(MEMBER_1, 1));
        Step.then("member '%s' has 1 certificate on secondary node".formatted(MEMBER_1),
                () -> assertMemberHasCertificateCount(MEMBER_1, 1, SECONDARY));
    }

    @Test
    @Order(11)
    @DisplayName("Cert status can be updated")
    void certStatusCanBeUpdated() {
        Step.given("self signed cert generated for key 'KeyX', client member-2", () -> generateSelfSignedCert("KeyX", MEMBER_2));
        Step.and("certificate info can be retrieved by cert hash", () -> assertCertificateRetrievableByHash());
        Step.then("certificate can be deactivated", () -> client().deactivateCert(certInfo.getId()));
        Step.and("certificate can be activated", () -> client().activateCert(certInfo.getId()));
        Step.and("certificate status can be changed to 'deletion in progress'",
                () -> client().setCertStatus(certInfo.getId(), "deletion in progress"));
        Step.and("certificate can be deleted", () -> client().deleteCert(certInfo.getId()));
    }

    @Test
    @Order(12)
    @DisplayName("Miscellaneous checks")
    void miscellaneousChecks() {
        Step.given("check token 'soft-token-000' key 'First key' batch signing enabled", () ->
                assertThat(client().isTokenBatchSigningEnabled(findKeyInToken(tokenFriendlyName(), "First key").getId())).isNotNull());
    }

    @Test
    @Order(13)
    @DisplayName("Exceptions are being handled")
    void exceptionsAreBeingHandled() {
        Step.given("Set token name fails with TokenNotFound exception when token does not exist", this::assertSetTokenNameFails);
        Step.and("Deleting not existing certificate from token fails", this::assertDeleteNonExistingCertFails);
        Step.and("Retrieving token info by not existing key fails", this::assertRetrievingTokenInfoByUnknownKeyFails);
        Step.and("Deleting not existing certRequest fails", this::assertDeletingNonExistingCertRequestFails);
        Step.and("Signing with unknown key fails", this::assertSigningWithUnknownKeyFails);
        Step.and("Getting key by not existing cert hash fails", this::assertGetKeyIdByUnknownHashFails);
        Step.and("Not existing certificate can not be activated", this::assertActivatingUnknownCertFails);
        Step.and("Member signing info for client member-1 fails if not suitable certificates are found",
                this::assertMemberSigningInfoFailsForUnsuitableMember);
        Step.and("auth key retrieval for Security Server SS100 fails when no active token is found",
                this::assertAuthKeyRetrievalFailsForSs100);
    }

    @Test
    @Order(14)
    @DisplayName("Ocsp responses")
    void ocspResponses() {
        Step.when("ocsp responses are set", this::setGoodOcspResponse);
        Step.then("ocsp responses can be retrieved", this::assertOcspResponsesRetrievable);
        Step.and("null ocsp response is returned for unknown certificate on primary node",
                () -> assertNullOcspResponseForUnknownCertificate());
        Step.and("null ocsp response is returned for unknown certificate on secondary node",
                () -> assertNullOcspResponseForUnknownCertificate(SECONDARY));
    }

    @Test
    @Order(15)
    @DisplayName("Ocsp responses verified on certificate activation")
    void ocspResponsesVerifiedOnCertificateActivation() {
        Step.when("the SIGNING cert request is generated for key 'SignKey from CA' for client MANAGEMENT",
                () -> generateCertRequest("SIGNING", "SignKey from CA", MANAGEMENT_CLIENT));
        Step.and("SIGN CSR is processed by test CA", () -> processCsr(AbstractSignerIntTest.CsrType.SIGN));
        Step.and("Generated certificate with initial status 'registered' is imported for MANAGEMENT",
                () -> importCertFromFile(CertificateInfo.STATUS_REGISTERED, MANAGEMENT_CLIENT));
        Step.then("certificate can be activated", () -> client().activateCert(certInfo.getId()));
        Step.when("ocsp responses are set to REVOKED", this::setRevokedOcspResponse);
        Step.then("certificate activation fails with ocsp verification", this::assertCertificateActivationFailsWithOcspVerification);
    }

    private void generateKey(String friendlyName) {
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

    private void assertTokenInfoRetrievableByKeyId() {
        assertTokenInfoRetrievableByKeyId(PRIMARY);
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

    @SneakyThrows
    private void processCsr(AbstractSignerIntTest.CsrType type) {
        File csrFile = File.createTempFile("tmp", type.name().toLowerCase() + "_csr" + System.currentTimeMillis());
        Files.write(csrFile.toPath(), lastCsrBytes);
        File cert = signCsr(csrFile, type);
        lastCertBytes = Files.readAllBytes(cert.toPath());
    }

    @SneakyThrows
    private void importCertFromFile(String initialStatus, String forClient) {
        scenarioKeyId = client().importCert(lastCertBytes, initialStatus, clientId(forClient));
        X509Certificate x509Certificate = readCertificate(lastCertBytes);
        scenarioCert = x509Certificate.getEncoded();
        certInfo = client().getCertForHash(calculateCertHexHash(scenarioCert));
    }

    private void assertTokenAndKeyRetrievableByCertRequest() {
        assertTokenAndKeyRetrievableByCertRequest(PRIMARY);
    }

    private void assertTokenAndKeyRetrievableByCertRequest(NodeType nodeType) {
        TokenInfoAndKeyId result = client(nodeType).getTokenAndKeyIdForCertRequestId(scenarioCsrId);
        assertThat(result).isNotNull();
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

    private void assertCertificateRetrievableByHash() {
        assertCertificateRetrievableByHash(PRIMARY);
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

    private void assertKeyIdRetrievableByCertHash() {
        assertKeyIdRetrievableByCertHash(PRIMARY);
    }

    private void assertKeyIdRetrievableByCertHash(NodeType nodeType) {
        assertThat(client(nodeType).getKeyIdForCertHash(certHash)).isNotNull();
    }

    private void assertTokenAndKeyIdRetrievableByCertHash() {
        assertTokenAndKeyIdRetrievableByCertHash(PRIMARY);
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
        KeyFactory kf = KeyFactory.getInstance(algorithm.name());
        PublicKey publicKey = kf.generatePublic(x509publicKey);

        final byte[] bytes = signClient(PRIMARY).signCertificate(key.getId(), signAlgorithmFor(key), "CN=CS", publicKey);
        assertThat(bytes).isNotEmpty();
    }

    private void assertMemberHasCertificateCount(String memberId, int count) {
        assertMemberHasCertificateCount(memberId, count, PRIMARY);
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

    private void assertSetTokenNameFails() {
        String tokenId = randomUUID().toString();
        try {
            client().setTokenFriendlyName(tokenId, randomUUID().toString());
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.token_not_found", "\\[.*?\\] signer\\.token_not_found: Token '" + tokenId + "' not found", e);
        }
    }

    private void assertDeleteNonExistingCertFails() {
        String certId = randomUUID().toString();
        try {
            client().deleteCert(certId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.cert_not_found",
                    "\\[.*?\\] signer\\.cert_not_found: Certificate with id '" + certId + "' not found", e);
        }
    }

    private void assertRetrievingTokenInfoByUnknownKeyFails() {
        String keyId = randomUUID().toString();
        try {
            client().getTokenForKeyId(keyId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.key_not_found", "\\[.*?\\] signer\\.key_not_found: Key '" + keyId + "' not found", e);
        }
    }

    private void assertDeletingNonExistingCertRequestFails() {
        String csrId = randomUUID().toString();
        try {
            client().deleteCertRequest(csrId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.csr_not_found",
                    "\\[.*?\\] signer\\.csr_not_found: Certificate request '" + csrId + "' not found", e);
        }
    }

    private void assertSigningWithUnknownKeyFails() {
        String keyId = randomUUID().toString();
        try {
            signClient(PRIMARY).sign(keyId, SignAlgorithm.ofName(randomUUID().toString()), new byte[0]);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.key_not_found", "\\[.*?\\] signer\\.key_not_found: Key '" + keyId + "' not found", e);
        }
    }

    private void assertGetKeyIdByUnknownHashFails() {
        String hash = randomUUID().toString();
        try {
            client().getKeyIdForCertHash(hash);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.cert_not_found",
                    "\\[.*?\\] signer\\.cert_not_found: Certificate with hash '" + hash + "' not found", e);
        }
    }

    private void assertActivatingUnknownCertFails() {
        String certId = randomUUID().toString();
        try {
            client().activateCert(certId);
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.cert_not_found",
                    "\\[.*?\\] signer\\.cert_not_found: Certificate with id '" + certId + "' not found", e);
        }
    }

    private void assertMemberSigningInfoFailsForUnsuitableMember() {
        try {
            client().getMemberSigningInfo(clientId(MEMBER_1));
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.internal_error",
                    "\\[.*?\\] signer\\.internal_error: Member 'MEMBER:DEV/test/member-1' has no suitable certificates", e);
        }
    }

    private void assertAuthKeyRetrievalFailsForSs100() {
        try {
            client().getAuthKey(securityServerId(SS100));
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            var errorServerId = SS100.replace(":", "/");
            assertXrdException("signer.key_not_found",
                    ("\\[.*?\\] signer\\.key_not_found: Could not find active authentication key for "
                            + "security server 'SERVER:%s'").formatted(errorServerId), e);
        }
    }

    @SneakyThrows
    private void setGoodOcspResponse() {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final OCSPResp ocspResponse = OcspTestUtils.createOCSPResponse(subject, TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0], TestCertUtil.getOcspSigner().key, CertificateStatus.GOOD);

        client().setOcspResponses(new String[]{calculateCertHexHash(subject)},
                new String[]{Base64.toBase64String(ocspResponse.getEncoded())});
    }

    @SneakyThrows
    private void assertOcspResponsesRetrievable() {
        X509Certificate subject = TestCertUtil.getConsumer().certChain[0];
        final String hash = calculateCertHexHash(subject);

        final String[] ocspResponses = client().getOcspResponses(new String[]{hash});
        assertThat(ocspResponses[0]).isNotNull();
    }

    private void assertNullOcspResponseForUnknownCertificate() {
        assertNullOcspResponseForUnknownCertificate(PRIMARY);
    }

    @SneakyThrows
    private void assertNullOcspResponseForUnknownCertificate(NodeType nodeType) {
        final String[] ocspResponses = client(nodeType).getOcspResponses(new String[]{calculateCertHexHash("not a cert".getBytes())});
        assertThat(ocspResponses).hasSize(1);
        assertThat(ocspResponses[0]).isNull();
    }

    @SneakyThrows
    private void setRevokedOcspResponse() {
        CertificateStatus certificateStatus = new RevokedStatus(Date.from(Instant.parse("2022-01-01T00:00:00Z")));
        X509Certificate subject = readCertificate(certInfo.getCertificateBytes());
        String caHomePath = "files/home/ca/CA";
        X509Certificate caCert = readCertificate(readClasspathBytes(caHomePath + "/certs/ca.cert.pem"));
        X509Certificate ocspCert = readCertificate(readClasspathBytes(caHomePath + "/certs/ocsp.cert.pem"));
        try (var reader = new InputStreamReader(requireClasspathStream(caHomePath + "/private/ocsp.key.pem"))) {
            PEMParser pemParser = new PEMParser(reader);
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pemParser.readObject());
            PrivateKey ocspPrivateKey = converter.getPrivateKey(privateKeyInfo);

            final OCSPResp ocspResponse = OcspTestUtils.createOCSPResponse(subject, caCert, ocspCert, ocspPrivateKey, certificateStatus);

            client().setOcspResponses(new String[]{calculateCertHexHash(subject)},
                    new String[]{Base64.toBase64String(ocspResponse.getEncoded())});
        }
    }

    private void assertCertificateActivationFailsWithOcspVerification() {
        try {
            client().activateCert(certInfo.getId());
            Assertions.fail("Exception expected");
        } catch (XrdRuntimeException e) {
            assertXrdException("signer.internal_error",
                    "\\[.*?\\] signer\\.internal_error: Failed to verify OCSP responses for certificate\\. "
                            + "Error: \\[.*?\\] invalid_cert_path\\.cert_validation: OCSP "
                            + "response indicates certificate status is REVOKED \\(date: 2022-01-01 00:00:00\\)", e);
        }
    }

    @SneakyThrows
    private byte[] readClasspathBytes(String path) {
        try (var in = requireClasspathStream(path)) {
            return in.readAllBytes();
        }
    }

    private InputStream requireClasspathStream(String path) {
        var in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Classpath resource not found: " + path);
        }
        return in;
    }
}
