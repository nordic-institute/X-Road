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
package org.niis.xroad.softtoken.signer.test;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.niis.xroad.softtoken.signer.test.container.SoftTokenSignerContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_ECDSA;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_RSA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ;
import static org.niis.xroad.softtoken.signer.test.container.SoftTokenSignerContainerSetup.SIGNER_AVAILABILITY_LOCK;

/**
 * 0100 - Softtoken-Signer: Key Synchronization. Creates and removes keys via the signer gRPC client, then
 * verifies (or refutes) key availability by attempting sign operations against softtoken-signer's own gRPC
 * sign endpoint - the same mechanism the legacy Cucumber scenarios used to prove synchronization.
 *
 * <p>Runs single-threaded and holds a read lock on {@link SoftTokenSignerContainerSetup#SIGNER_AVAILABILITY_LOCK}:
 * scenarios share the same signer token and would otherwise race on concurrent key/token mutation, and must
 * never overlap {@link SoftTokenHealthChecksIntTest}, which stops and restarts the signer container.
 */
@Slf4j
@ExtendWith(ApiStackExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(value = SIGNER_AVAILABILITY_LOCK, mode = READ)
class SoftTokenKeySyncIntTest {

    private static final String TOKEN_SOFT_000 = "soft-token-000";
    private static final String PIN = "1234";
    private static final int KEYS_SYNC_RATE_SECONDS = 3;
    private static final int KEYS_SYNC_BUFFER_SECONDS = 1;
    private static final byte[] TEST_DATA = "test data for signature".getBytes(StandardCharsets.UTF_8);

    private final Map<String, KeyInfo> createdKeys = new HashMap<>();

    private SignerRpcClient signerClient;
    private SignerSignRpcClient softTokenSignerSignClient;
    private byte[] lastSignature;

    @BeforeEach
    void signerIsInitializedAndTokenLoggedIn(SoftTokenSignerContainerSetup setup) {
        signerClient = setup.getSignerClient();
        softTokenSignerSignClient = setup.getSoftTokenSignerSignClient();

        Step.given("signer is initialized with PIN '%s'".formatted(PIN),
                () -> signerClient.initSoftwareToken(PIN.toCharArray()));
        Step.and("token '%s' is logged in with PIN '%s'".formatted(TOKEN_SOFT_000, PIN), () -> {
            var token = tokenByFriendlyName(TOKEN_SOFT_000);
            signerClient.activateToken(token.getId(), PIN.toCharArray());
        });
    }

    @AfterEach
    void cleanupCreatedKeys() {
        createdKeys.values().forEach(key -> {
            try {
                signerClient.deleteKey(key.getId(), true);
            } catch (Exception e) {
                log.warn("Failed to delete key during cleanup: {}", key.getFriendlyName(), e);
            }
        });
        createdKeys.clear();
    }

    @Test
    @DisplayName("Keys are synchronized on startup")
    void keysAreSynchronizedOnStartup() {
        Step.given("signer has RSA key 'rsa-key' on token 'soft-token-000'", () -> generateKey("rsa-key", KeyAlgorithm.RSA));
        Step.and("signer has EC key 'ec-key' on token 'soft-token-000'", () -> generateKey("ec-key", KeyAlgorithm.EC));
        Step.when("key synchronization completes", this::waitForSynchronization);
        Step.then("softtoken-signer can sign with key 'rsa-key'", () -> assertCanSign("rsa-key"));
        Step.and("softtoken-signer can sign with key 'ec-key'", () -> assertCanSign("ec-key"));
    }

    @Test
    @DisplayName("New key is synchronized after creation")
    void newKeyIsSynchronizedAfterCreation() {
        Step.given("signer has RSA key 'existing-key' on token 'soft-token-000'",
                () -> generateKey("existing-key", KeyAlgorithm.RSA));
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.when("new RSA key 'new-key' generated for token 'soft-token-000' in signer",
                () -> generateKey("new-key", KeyAlgorithm.RSA));
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.then("softtoken-signer can sign with key 'new-key'", () -> assertCanSign("new-key"));
    }

    @Test
    @DisplayName("Key deletion is synchronized")
    void keyDeletionIsSynchronized() {
        Step.given("signer has RSA key 'key-to-delete' on token 'soft-token-000'",
                () -> generateKey("key-to-delete", KeyAlgorithm.RSA));
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.when("key 'key-to-delete' is deleted from signer", () -> deleteKey("key-to-delete"));
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.then("softtoken-signer cannot sign with key 'key-to-delete'", () -> assertCannotSign("key-to-delete"));
    }

    @Test
    @DisplayName("Signature created by softtoken-signer is valid")
    void signatureCreatedByServiceIsValid() {
        Step.given("signer has EC key 'sign-test-key' on token 'soft-token-000'",
                () -> generateKey("sign-test-key", KeyAlgorithm.EC));
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.when("signature is created with softtoken-signer using key 'sign-test-key'",
                () -> assertCanSign("sign-test-key"));
        Step.then("signature can be verified with key 'sign-test-key' public key",
                () -> assertSignatureValid("sign-test-key"));
    }

    @Test
    @DisplayName("Token deactivation and reactivation restores key availability")
    void tokenDeactivationAndReactivationRestoresKeyAvailability() {
        Step.given("signer has EC key 'test-key' on token 'soft-token-000'", () -> generateKey("test-key", KeyAlgorithm.EC));
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.and("softtoken-signer can sign with key 'test-key'", () -> assertCanSign("test-key"));
        Step.when("token 'soft-token-000' is deactivated", () -> {
            var token = tokenByFriendlyName(TOKEN_SOFT_000);
            signerClient.deactivateToken(token.getId());
        });
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.and("softtoken-signer cannot sign with key 'test-key'", () -> assertCannotSign("test-key"));
        Step.and("token 'soft-token-000' is reactivated with PIN '1234'", () -> {
            var token = tokenByFriendlyName(TOKEN_SOFT_000);
            signerClient.activateToken(token.getId(), PIN.toCharArray());
        });
        Step.and("key synchronization completes", this::waitForSynchronization);
        Step.then("softtoken-signer can sign with key 'test-key'", () -> assertCanSign("test-key"));
    }

    private void generateKey(String keyLabel, KeyAlgorithm algorithm) {
        var token = tokenByFriendlyName(TOKEN_SOFT_000);
        var keyInfo = signerClient.generateKey(token.getId(), keyLabel, algorithm);
        createdKeys.put(keyLabel, keyInfo);
    }

    private void deleteKey(String keyLabel) {
        var keyInfo = createdKeys.get(keyLabel);
        if (keyInfo == null) {
            keyInfo = findKeyInToken(keyLabel);
        }
        signerClient.deleteKey(keyInfo.getId(), true);
        createdKeys.remove(keyLabel);
    }

    @SneakyThrows
    private void assertCanSign(String keyLabel) {
        var keyInfo = resolveKey(keyLabel);

        byte[] digest = calculateDigest(DigestAlgorithm.SHA256, TEST_DATA);
        var signAlgorithm = signAlgorithmFor(keyInfo);

        byte[] signature = softTokenSignerSignClient.sign(keyInfo.getId(), signAlgorithm, digest);

        assertThat(signature).as("Signature should be created successfully").isNotNull().isNotEmpty();
        lastSignature = signature;
    }

    @SneakyThrows
    private void assertCannotSign(String keyLabel) {
        var keyInfo = createdKeys.get(keyLabel);
        if (keyInfo == null) {
            log.debug("Key '{}' was already deleted", keyLabel);
            return;
        }

        byte[] digest = calculateDigest(DigestAlgorithm.SHA256, TEST_DATA);
        assertThatThrownBy(() -> softTokenSignerSignClient.sign(keyInfo.getId(), SHA256_WITH_RSA, digest))
                .as("Signing with deleted key should fail")
                .isInstanceOf(Exception.class);
    }

    @SneakyThrows
    private void assertSignatureValid(String keyLabel) {
        assertThat(lastSignature).as("Signature should exist").isNotNull();

        var keyInfo = resolveKey(keyLabel);
        var keyAlgorithm = SignMechanism.valueOf(keyInfo.getSignMechanismName()).keyAlgorithm();
        var publicKey = decodePublicKey(keyInfo, keyAlgorithm);

        var verifier = Signature.getInstance(signAlgorithmFor(keyInfo).name());
        verifier.initVerify(publicKey);
        verifier.update(TEST_DATA);

        assertThat(verifier.verify(lastSignature)).as("Signature should be valid").isTrue();
    }

    private PublicKey decodePublicKey(KeyInfo keyInfo, KeyAlgorithm keyAlgorithm) throws Exception {
        var keyFactory = KeyFactory.getInstance(keyAlgorithm.name());
        return keyFactory.generatePublic(new X509EncodedKeySpec(Base64.decode(keyInfo.getPublicKey())));
    }

    private SignAlgorithm signAlgorithmFor(KeyInfo keyInfo) {
        var keyAlgorithm = SignMechanism.valueOf(keyInfo.getSignMechanismName()).keyAlgorithm();
        return keyAlgorithm == KeyAlgorithm.RSA ? SHA256_WITH_RSA : SHA256_WITH_ECDSA;
    }

    private KeyInfo resolveKey(String keyLabel) {
        var keyInfo = createdKeys.get(keyLabel);
        return keyInfo != null ? keyInfo : findKeyInToken(keyLabel);
    }

    private KeyInfo findKeyInToken(String keyLabel) {
        var token = tokenByFriendlyName(TOKEN_SOFT_000);
        Optional<KeyInfo> key = token.getKeyInfo().stream()
                .filter(k -> keyLabel.equals(k.getFriendlyName()))
                .findFirst();
        return key.orElseThrow(() -> new AssertionError("Key not found in token " + TOKEN_SOFT_000 + ": " + keyLabel));
    }

    private TokenInfo tokenByFriendlyName(String friendlyName) {
        var tokens = signerClient.getTokens();

        Optional<TokenInfo> token = tokens.stream()
                .filter(t -> friendlyName.equals(t.getFriendlyName()))
                .findFirst();

        if (token.isEmpty() && TOKEN_SOFT_000.equals(friendlyName)) {
            token = tokens.stream().filter(t -> "0".equals(t.getId())).findFirst();
        }

        return token.orElseThrow(() -> new AssertionError("Token not found: " + friendlyName));
    }

    private void waitForSynchronization() {
        try {
            Thread.sleep(Duration.ofSeconds(KEYS_SYNC_RATE_SECONDS + KEYS_SYNC_BUFFER_SECONDS).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Synchronization wait interrupted", e);
        }
    }
}
