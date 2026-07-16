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

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.common.properties.NodeProperties.NodeType;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.PRIMARY;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.SECONDARY;
import static org.niis.xroad.signer.test.container.SignerIntTestContainerSetup.SIGNER;

/**
 * 0400 - Signer: Secondary node tests. Verifies the secondary node's read-only access-control contract
 * and cross-node key propagation via {@code refreshModules}, closing with a scenario that inserts a key
 * directly on the HSM device (bypassing the signer's own generate-key RPC) to prove the module scanner
 * still picks it up and syncs it to the secondary node.
 */
@Order(400)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("java:S1192")
class SignerSecondaryNodeIntTest extends AbstractSignerIntTest {

    private static final String TOKEN_SOFT_000 = "soft-token-000";
    private static final String TOKEN_HSM_0 = "xrd-softhsm-0";
    private static final String AUTOLOGIN_PIN = "4321";
    private static final String HSM_PIN = "1234";

    @BeforeEach
    void backgroundSetup() {
        Step.given("tokens are listed", this::listTokens);
        Step.and("tokens are listed on secondary node", () -> client(SECONDARY).getTokens());
    }

    @Test
    @Order(1)
    @DisplayName("Write operations are not allowed on secondary node")
    void writeOperationsAreNotAllowedOnSecondaryNode() {
        Step.given("Init software token on secondary node not allowed",
                () -> assertAccessDenied(() -> client(SECONDARY).initSoftwareToken(HSM_PIN.toCharArray())));
        Step.and("Set token friendly name on secondary node not allowed",
                () -> assertAccessDenied(() -> client(SECONDARY).setTokenFriendlyName("0", "name")));
        Step.and("Delete token on secondary node not allowed",
                () -> assertAccessDenied(() -> client(SECONDARY).deleteToken("0")));
    }

    @Test
    @Order(2)
    @DisplayName("Activate token on secondary node")
    void activateTokenOnSecondaryNode() {
        Step.when("token '%s' is logged in with pin '%s' on secondary node".formatted(TOKEN_SOFT_000, AUTOLOGIN_PIN),
                () -> client(SECONDARY).activateToken(tokenIdByFriendlyName(TOKEN_SOFT_000), AUTOLOGIN_PIN.toCharArray()));
        Step.then("token '%s' is active on secondary node".formatted(TOKEN_SOFT_000),
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_SOFT_000, SECONDARY).isActive()).isTrue());
        Step.and("Update token pin on secondary node not allowed", () -> assertAccessDenied(
                () -> client(SECONDARY).updateTokenPin("0", AUTOLOGIN_PIN.toCharArray(), "pin".toCharArray())));
        Step.and("Generate new key on secondary node not allowed",
                () -> assertAccessDenied(() -> client(SECONDARY).generateKey("0", "key-label", KeyAlgorithm.RSA)));
    }

    @Test
    @Order(3)
    @DisplayName("Signing data on secondary node")
    void signingDataOnSecondaryNode() {
        Step.given("new RSA key 'key-test-secondary-1' generated for token '%s'".formatted(TOKEN_SOFT_000),
                () -> generateKey(TOKEN_SOFT_000, "key-test-secondary-1", KeyAlgorithm.RSA));
        Step.and("new EC key 'key-test-secondary-2' generated for token '%s'".formatted(TOKEN_SOFT_000),
                () -> generateKey(TOKEN_SOFT_000, "key-test-secondary-2", KeyAlgorithm.EC));
        Step.and("new RSA key 'key-test-secondary-3' generated for token '%s'".formatted(TOKEN_HSM_0),
                () -> generateKey(TOKEN_HSM_0, "key-test-secondary-3", KeyAlgorithm.RSA));
        Step.and("new EC key 'key-test-secondary-4' generated for token '%s'".formatted(TOKEN_HSM_0),
                () -> generateKey(TOKEN_HSM_0, "key-test-secondary-4", KeyAlgorithm.EC));
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.when("token '%s' is logged in with pin '%s' on secondary node".formatted(TOKEN_HSM_0, HSM_PIN),
                () -> client(SECONDARY).activateToken(tokenIdByFriendlyName(TOKEN_HSM_0), HSM_PIN.toCharArray()));
        Step.and("token '%s' is logged in with pin '%s' on secondary node".formatted(TOKEN_SOFT_000, AUTOLOGIN_PIN),
                () -> client(SECONDARY).activateToken(tokenIdByFriendlyName(TOKEN_SOFT_000), AUTOLOGIN_PIN.toCharArray()));
        Step.then("digest can be signed using key 'key-test-secondary-1' from token '%s' on secondary node".formatted(TOKEN_SOFT_000),
                () -> assertCanSignOnSecondary(TOKEN_SOFT_000, "key-test-secondary-1"));
        Step.and("digest can be signed using key 'key-test-secondary-2' from token '%s' on secondary node".formatted(TOKEN_SOFT_000),
                () -> assertCanSignOnSecondary(TOKEN_SOFT_000, "key-test-secondary-2"));
        Step.and("digest can be signed using key 'key-test-secondary-3' from token '%s' on secondary node".formatted(TOKEN_HSM_0),
                () -> assertCanSignOnSecondary(TOKEN_HSM_0, "key-test-secondary-3"));
        Step.and("digest can be signed using key 'key-test-secondary-4' from token '%s' on secondary node".formatted(TOKEN_HSM_0),
                () -> assertCanSignOnSecondary(TOKEN_HSM_0, "key-test-secondary-4"));
    }

    @Test
    @Order(4)
    @DisplayName("Loading token with transient certificate from HSM")
    void loadingTokenWithTransientCertificateFromHsm() {
        Step.given("all keys are deleted from token '%s'".formatted(TOKEN_HSM_0), this::deleteAllHsmKeys);
        Step.and("token '%s' has 0 key on primary node".formatted(TOKEN_HSM_0), () -> assertKeyCount(PRIMARY, 0));
        Step.and("primary node is refreshed", () -> client(PRIMARY).refreshModules());
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.when("new key with id '1357' and certificate magically appears on HSM", () -> newKeyAppearsOnHsm("1357"));
        Step.and("primary node is refreshed", () -> client(PRIMARY).refreshModules());
        Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
        Step.then("token '%s' has 1 key on secondary node".formatted(TOKEN_HSM_0), () -> assertKeyCount(SECONDARY, 1));
        Step.and("token '%s' token is not saved to configuration on primary node".formatted(TOKEN_HSM_0),
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0, PRIMARY).isSavedToConfiguration()).isFalse());
        Step.and("token '%s' token is not saved to configuration on secondary node".formatted(TOKEN_HSM_0),
                () -> assertThat(tokenInfoByFriendlyName(TOKEN_HSM_0, SECONDARY).isSavedToConfiguration()).isFalse());
    }

    private void generateKey(String tokenFriendlyName, String keyLabel, KeyAlgorithm algorithm) {
        var tokenId = tokenIdByFriendlyName(tokenFriendlyName);
        var keyInfo = client().generateKey(tokenId, keyLabel, algorithm);
        client().setKeyFriendlyName(keyInfo.getId(), keyLabel);
    }

    @SneakyThrows
    private void assertCanSignOnSecondary(String tokenFriendlyName, String keyName) {
        var key = findKeyInToken(tokenFriendlyName, keyName, SECONDARY);
        var digest = "%s-%d".formatted(UUID.randomUUID(), System.currentTimeMillis());
        byte[] bytes = signClient(SECONDARY).sign(key.getId(), signAlgorithmFor(key),
                calculateDigest(SHA256, digest.getBytes(StandardCharsets.UTF_8)));
        assertThat(bytes).isNotEmpty();
    }

    private void deleteAllHsmKeys() {
        tokenInfoByFriendlyName(TOKEN_HSM_0, PRIMARY).getKeyInfo()
                .forEach(keyInfo -> client(PRIMARY).deleteKey(keyInfo.getId(), true));
    }

    private void assertKeyCount(NodeType nodeType, int count) {
        var tokenInfo = client(nodeType).getTokens().stream()
                .filter(t -> TOKEN_HSM_0.equals(t.getFriendlyName()))
                .findFirst()
                .orElseThrow();
        assertThat(tokenInfo.getKeyInfo()).hasSize(count);
    }

    private void newKeyAppearsOnHsm(String keyId) {
        var result = containerSetup.execInContainer(SIGNER, "/add-key-into-hsm.sh", keyId);
        assertThat(result.getExitCode()).isEqualTo(0);
    }
}
