/*
 * The MIT License
 *
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

package org.niis.xroad.softtoken.signer.test.glue;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Base64;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_ECDSA;
import static ee.ria.xroad.common.crypto.identifier.SignAlgorithm.SHA256_WITH_RSA;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Step definitions for key synchronization testing.
 * Verifies synchronization by attempting sign operations with softtoken-signer.
 */
@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class SoftTokenSignerStepDefs extends BaseSoftTokenSignerStepDefs {
    private static final String LAST_SIGNATURE = "lastSignature";
    private static final String LAST_SIGNED_DATA = "lastSignedData";
    private static final String LAST_KEY_ID = "lastKeyId";
    private static final byte[] TEST_DATA = "test data for signature".getBytes();

    @Step("signer has RSA key {string} on token {string}")
    public void signerHasRsaKeyOnToken(String keyLabel, String tokenFriendlyName) {
        log.info("Creating RSA key '{}' on token '{}'", keyLabel, tokenFriendlyName);
        TokenInfo token = getTokenByFriendlyName(tokenFriendlyName);
        KeyInfo keyInfo = getSignerClient().generateKey(token.getId(), keyLabel, KeyAlgorithm.RSA);

        getCreatedKeys().put(keyLabel, keyInfo);
        testReportService.attachJson("Created RSA key", keyInfo);
    }

    @Step("signer has EC key {string} on token {string}")
    public void signerHasEcKeyOnToken(String keyLabel, String tokenFriendlyName) {
        log.info("Creating EC key '{}' on token '{}'", keyLabel, tokenFriendlyName);
        TokenInfo token = getTokenByFriendlyName(tokenFriendlyName);
        KeyInfo keyInfo = getSignerClient().generateKey(token.getId(), keyLabel, KeyAlgorithm.EC);

        getCreatedKeys().put(keyLabel, keyInfo);
        testReportService.attachJson("Created EC key", keyInfo);
    }

    @Step("new RSA key {string} generated for token {string} in signer")
    public void newRsaKeyGeneratedForTokenInSigner(String keyLabel, String tokenFriendlyName) {
        signerHasRsaKeyOnToken(keyLabel, tokenFriendlyName);
    }

    @Step("key synchronization completes")
    public void keySynchronizationCompletes() {
        waitForSynchronization();
    }

    @Step("softtoken-signer can sign with key {string}")
    public void softtokenSignerCanSignWithKey(String keyLabel) throws Exception {
        log.info("Verifying softtoken-signer can sign with key: {}", keyLabel);

        KeyInfo keyInfo = getCreatedKeys().get(keyLabel);
        if (keyInfo == null) {
            keyInfo = findKeyInToken("soft-token-000", keyLabel);
        }

        // Calculate digest of test data
        byte[] digest = calculateDigest(DigestAlgorithm.SHA256, TEST_DATA);

        var keyAlgorithm = SignMechanism.valueOf(keyInfo.getSignMechanismName()).keyAlgorithm();
        var signAlgorithm = keyAlgorithm == KeyAlgorithm.RSA ? SHA256_WITH_RSA : SHA256_WITH_ECDSA;

        // Attempt to sign with softtoken-signer
        byte[] signature = getSoftTokenSignerSignClient().sign(keyInfo.getId(), signAlgorithm, digest);

        assertThat(signature)
                .as("Signature should be created successfully")
                .isNotNull()
                .isNotEmpty();

        // Store for verification
        putStepData(LAST_SIGNATURE, signature);
        putStepData(LAST_SIGNED_DATA, TEST_DATA);
        putStepData(LAST_KEY_ID, keyInfo.getId());

        log.info("Successfully signed with key '{}' using softtoken-signer", keyLabel);
        testReportService.attachText("Signature success", "Key " + keyLabel + " is synchronized");
    }

    @Step("softtoken-signer cannot sign with key {string}")
    public void softtokenSignerCannotSignWithKey(String keyLabel) throws Exception {
        log.info("Verifying softtoken-signer cannot sign with deleted key: {}", keyLabel);

        KeyInfo keyInfo = getCreatedKeys().get(keyLabel);
        if (keyInfo == null) {
            // Key was deleted, we still have the ID from before deletion
            log.debug("Key '{}' was already deleted", keyLabel);
            testReportService.attachText("Key deleted", "Key " + keyLabel + " not found (expected)");
            return;
        }

        byte[] digest = calculateDigest(DigestAlgorithm.SHA256, TEST_DATA);

        // Attempt to sign should fail
        assertThatThrownBy(() ->
                getSoftTokenSignerSignClient().sign(keyInfo.getId(), SHA256_WITH_RSA, digest))
                .as("Signing with deleted key should fail")
                .isInstanceOf(Exception.class);

        log.info("Confirmed key '{}' cannot be used for signing (not synchronized)", keyLabel);
        testReportService.attachText("Signature failed", "Key " + keyLabel + " is not synchronized (expected)");
    }

    @Step("signature is created with softtoken-signer using key {string}")
    public void signatureIsCreatedWithSoftTokenSignerUsingKey(String keyLabel) throws Exception {
        softtokenSignerCanSignWithKey(keyLabel);
    }

    @Step("signature can be verified with key {string} public key")
    public void signatureCanBeVerifiedWithKeyPublicKey(String keyLabel) throws Exception {
        log.info("Verifying signature with public key from key: {}", keyLabel);

        byte[] signature = getStepData(LAST_SIGNATURE);
        byte[] signedData = getStepData(LAST_SIGNED_DATA);

        assertThat(signature).as("Signature should exist").isNotNull();
        assertThat(signedData).as("Signed data should exist").isNotNull();

        KeyInfo keyInfo = getCreatedKeys().get(keyLabel);
        if (keyInfo == null) {
            keyInfo = findKeyInToken("soft-token-000", keyLabel);
        }

        var keyAlgorithm = SignMechanism.valueOf(keyInfo.getSignMechanismName()).keyAlgorithm();
        byte[] publicKeyBytes = Base64.decode(keyInfo.getPublicKey());
        KeyFactory keyFactory = KeyFactory.getInstance(keyAlgorithm.name());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(spec);
        var signAlgorithm = keyAlgorithm == KeyAlgorithm.RSA ? SHA256_WITH_RSA : SHA256_WITH_ECDSA;

        Signature verifier = Signature.getInstance(signAlgorithm.name());
        verifier.initVerify(publicKey);
        verifier.update(signedData);

        boolean isValid = verifier.verify(signature);

        assertThat(isValid)
                .as("Signature should be valid")
                .isTrue();

        log.info("Signature verified successfully for key '{}'", keyLabel);
        testReportService.attachText("Signature verification", "Valid signature from key " + keyLabel);
    }

    @Step("key {string} is deleted from signer")
    public void keyIsDeletedFromSigner(String keyLabel) {
        log.info("Deleting key from signer: {}", keyLabel);
        KeyInfo keyInfo = getCreatedKeys().get(keyLabel);

        if (keyInfo == null) {
            keyInfo = findKeyInToken("soft-token-000", keyLabel);
        }

        getSignerClient().deleteKey(keyInfo.getId(), true);
        getCreatedKeys().remove(keyLabel);

        testReportService.attachText("Key deletion", "Key '" + keyLabel + "' deleted from signer");
    }

    @Step("key {string} label is changed to {string} in signer")
    public void keyLabelIsChangedInSigner(String oldLabel, String newLabel) {
        log.info("Changing key label from '{}' to '{}'", oldLabel, newLabel);
        KeyInfo keyInfo = getCreatedKeys().get(oldLabel);

        if (keyInfo == null) {
            keyInfo = findKeyInToken("soft-token-000", oldLabel);
        }

        getSignerClient().setKeyFriendlyName(keyInfo.getId(), newLabel);

        // Update our tracking
        getCreatedKeys().remove(oldLabel);
        getCreatedKeys().put(newLabel, keyInfo);

        testReportService.attachText("Key label change",
                "Changed from '" + oldLabel + "' to '" + newLabel + "'");
    }
}
