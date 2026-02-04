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
package org.niis.xroad.common.pgp;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.pgp.model.PgpKeyPair;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PgpKeyManager.
 * Tests key loading, caching, and the automatic inclusion of signing public key in encryption recipients.
 */
class PgpKeyManagerTest {

    private PgpKeyProvider mockKeyProvider;
    private PgpKeyManager keyManager;
    private String testSigningKeyId;
    private String testPublicKeysArmored;

    @BeforeEach
    void setUp() {
        // Generate test keys
        PgpKeyGenerator keyGenerator = new PgpKeyGenerator();
        var keyInfo = keyGenerator.generate("Test User <test@example.com>");

        testSigningKeyId = keyInfo.keyId(); // Already formatted with leading zeros
        String testSecretKeyArmored = keyInfo.secretData();
        testPublicKeysArmored = keyInfo.publicData();

        // Mock key provider
        mockKeyProvider = mock(PgpKeyProvider.class);
        when(mockKeyProvider.getSigningSecretKey()).thenReturn(testSecretKeyArmored);
        when(mockKeyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(testPublicKeysArmored));

        keyManager = new PgpKeyManager(mockKeyProvider);
    }

    @Test
    void testSigningPublicKeyIsAutomaticallyAddedToEncryptionKeys() {
        // When: Get signing key pair
        PgpKeyPair signingKeyPair = keyManager.getSigningKeyPair();
        PGPPublicKey signingPublicKey = signingKeyPair.publicKey();

        // Then: Signing public key should always be added (RSA keys can encrypt)
        Map<String, PGPPublicKey> allPublicKeys = keyManager.getAllPublicKeys();
        String signingKeyId = PgpKeyUtils.formatKeyId(signingPublicKey.getKeyID());

        assertTrue(allPublicKeys.containsKey(signingKeyId),
                "Signing public key should be automatically added to encryption keys");

        Optional<PGPPublicKey> retrievedKey = keyManager.getPublicKey(signingKeyId);
        assertTrue(retrievedKey.isPresent(), "Signing public key should be retrievable by ID");
        assertEquals(signingPublicKey.getKeyID(), retrievedKey.get().getKeyID(),
                "Retrieved public key should match the signing key pair's public key");
    }

    @Test
    void testSigningPublicKeyNotDuplicatedIfAlreadyPresent() {
        // Given: Keys are loaded
        Map<String, PGPPublicKey> allPublicKeys = keyManager.getAllPublicKeys();
        PGPPublicKey signingPublicKey = keyManager.getSigningKeyPair().publicKey();

        // When: Count how many times the signing key appears
        String signingKeyId = PgpKeyUtils.formatKeyId(signingPublicKey.getKeyID());
        long count = allPublicKeys.entrySet().stream()
                .filter(entry -> entry.getKey().equals(signingKeyId))
                .count();

        // Then: Should appear exactly once (no duplication)
        assertEquals(1, count, "Signing public key should appear exactly once");
    }

    @Test
    void testGetSigningKeyPairReturnsValidKeyPair() {
        // When: Get signing key pair
        PgpKeyPair signingKeyPair = keyManager.getSigningKeyPair();

        // Then: Key pair should be valid
        assertNotNull(signingKeyPair, "Signing key pair should not be null");
        assertNotNull(signingKeyPair.privateKey(), "Private key should not be null");
        assertNotNull(signingKeyPair.publicKey(), "Public key should not be null");
        assertEquals(testSigningKeyId, signingKeyPair.keyIdHex(), "Key ID should match expected test key ID");
    }

    @Test
    void testGetAllPublicKeysIncludesSigningKey() {
        // When: Get all public keys
        Map<String, PGPPublicKey> allPublicKeys = keyManager.getAllPublicKeys();
        PGPPublicKey signingPublicKey = keyManager.getSigningKeyPair().publicKey();
        String signingKeyId = PgpKeyUtils.formatKeyId(signingPublicKey.getKeyID());

        // Then: Should always contain signing key (RSA keys can encrypt)
        assertNotNull(allPublicKeys, "Public keys map should not be null");
        assertFalse(allPublicKeys.isEmpty(), "Should have at least one public key (the signing public key)");
        assertTrue(allPublicKeys.containsKey(signingKeyId),
                "Public keys should include the signing public key");
    }

    @Test
    void testGetPublicKeyByIdWorksForSigningKey() {
        // Given: Signing key pair
        PgpKeyPair signingKeyPair = keyManager.getSigningKeyPair();
        String signingKeyId = signingKeyPair.keyIdHex();
        PGPPublicKey signingPublicKey = signingKeyPair.publicKey();

        // When: Get public key by signing key ID
        Optional<PGPPublicKey> publicKey = keyManager.getPublicKey(signingKeyId);

        // Then: Should always return the signing public key (RSA keys can encrypt)
        assertTrue(publicKey.isPresent(), "Signing public key should be found by ID");
        assertEquals(signingPublicKey.getKeyID(), publicKey.get().getKeyID(),
                "Retrieved key should match signing public key");
    }

    @Test
    void testGetPublicKeyByIdReturnEmptyForUnknownKey() {
        // When: Try to get a non-existent key
        Optional<PGPPublicKey> publicKey = keyManager.getPublicKey("0000000000000000");

        // Then: Should return empty
        assertTrue(publicKey.isEmpty(), "Should return empty for unknown key ID");
    }

    @Test
    void testKeysAreLoadedOnlyOnceAndCached() {
        // When: Access keys multiple times
        keyManager.getSigningKeyPair();
        keyManager.getAllPublicKeys();
        keyManager.getSigningKeyPair();
        keyManager.getAllPublicKeys();

        // Then: Key provider should be called only once (lazy loading + caching)
        verify(mockKeyProvider, times(1)).getSigningSecretKey();
        verify(mockKeyProvider, times(1)).getEncryptionPublicKeys();
    }

    @Test
    void testLazyLoadingInitializesKeysOnFirstAccess() {
        // Given: Fresh key manager (keys not loaded yet)
        // (mockKeyProvider has not been called yet)

        // When: Access signing key pair for the first time
        keyManager.getSigningKeyPair();

        // Then: Keys should be loaded from provider
        verify(mockKeyProvider, times(1)).getSigningSecretKey();
        verify(mockKeyProvider, times(1)).getEncryptionPublicKeys();
    }

    @Test
    void testConcurrentAccessInitializesKeysOnlyOnce() throws InterruptedException {
        // Given: Multiple threads trying to access keys simultaneously
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: Multiple threads access keys concurrently
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // All threads wait here
                    PgpKeyPair keyPair = keyManager.getSigningKeyPair();
                    if (keyPair != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    completionLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // Release all threads at once
        completionLatch.await(); // Wait for all threads to complete

        // Then: Keys should be loaded only once despite concurrent access
        verify(mockKeyProvider, times(1)).getSigningSecretKey();
        verify(mockKeyProvider, times(1)).getEncryptionPublicKeys();
        assertEquals(threadCount, successCount.get(), "All threads should successfully get the key pair");
    }

    @Test
    void testReturnedPublicKeysMapIsImmutable() {
        // When: Get all public keys
        Map<String, PGPPublicKey> publicKeys = keyManager.getAllPublicKeys();

        // Then: Map should be immutable (defensive copy)
        assertNotNull(publicKeys);
        try {
            publicKeys.put("TEST_KEY_ID", null);
            fail("Should throw UnsupportedOperationException for immutable map");
        } catch (UnsupportedOperationException e) {
            // Expected - map is immutable
        }
    }

    @Test
    void testMultipleCallsReturnSamePublicKeysMap() {
        // When: Get public keys multiple times
        Map<String, PGPPublicKey> keys1 = keyManager.getAllPublicKeys();
        Map<String, PGPPublicKey> keys2 = keyManager.getAllPublicKeys();

        // Then: Should return equivalent maps (due to caching)
        assertEquals(keys1.size(), keys2.size(), "Maps should have same size");
        keys1.keySet().forEach(keyId -> assertTrue(keys2.containsKey(keyId), "Both maps should contain the same keys"));
    }

    @Test
    void testSigningKeyCanBeUsedForEncryption() {
        // Given: Signing key pair
        PgpKeyPair signingKeyPair = keyManager.getSigningKeyPair();
        String signingKeyId = signingKeyPair.keyIdHex();
        PGPPublicKey signingPublicKey = signingKeyPair.publicKey();

        // When: Get the signing public key from encryption keys
        Optional<PGPPublicKey> encryptionKey = keyManager.getPublicKey(signingKeyId);

        // Then: RSA signing key should always be available for encryption
        assertTrue(encryptionKey.isPresent(),
                "Signing public key should be available for encryption (RSA keys can encrypt)");
        assertEquals(signingPublicKey.getKeyID(), encryptionKey.get().getKeyID(),
                "Retrieved key should match signing public key");
    }

    @Test
    void testKeyManagerWithMultipleRecipientKeys() {
        // Given: Generate additional recipient keys
        var keyGenerator = new PgpKeyGenerator();
        var recipient1 = keyGenerator.generate("XRD/Class/Member/ServerCode");
        var recipient2 = keyGenerator.generate("XRD/Class/Member/ServerCode2");

        // Then: Signing key should always be available for encryption (RSA keys can encrypt)
        assertFalse(keyManager.getAllPublicKeys().isEmpty(),
                "Key manager should have signing key available for encryption");

        // Verify recipient keys can also be loaded by creating key managers for them individually
        when(mockKeyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(recipient1.publicData()));
        PgpKeyManager manager1 = new PgpKeyManager(mockKeyProvider);
        assertFalse(manager1.getAllPublicKeys().isEmpty(),
                "Recipient 1 key should be loaded");

        when(mockKeyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(recipient2.publicData()));
        PgpKeyManager manager2 = new PgpKeyManager(mockKeyProvider);
        assertFalse(manager2.getAllPublicKeys().isEmpty(),
                "Recipient 2 key should be loaded");
    }

    @Test
    void testKeyManagerFailsWithInvalidSecretKey() {
        // Given: Invalid secret key data
        PgpKeyProvider invalidProvider = mock(PgpKeyProvider.class);
        when(invalidProvider.getSigningSecretKey()).thenReturn("Not a valid PGP key at all!");
        when(invalidProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(testPublicKeysArmored));

        PgpKeyManager invalidKeyManager = new PgpKeyManager(invalidProvider);

        // When/Then: Should throw exception on first access
        var exception = assertThrows(RuntimeException.class, invalidKeyManager::getSigningKeyPair);

        assertNotNull(exception.getMessage(), "Exception should have a message");
    }

    @Test
    void testKeyManagerFailsWithEmptySecretKey() {
        // Given: Empty secret key
        PgpKeyProvider emptyProvider = mock(PgpKeyProvider.class);
        when(emptyProvider.getSigningSecretKey()).thenReturn("");
        when(emptyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(testPublicKeysArmored));

        PgpKeyManager emptyKeyManager = new PgpKeyManager(emptyProvider);

        // When/Then: Should throw exception on first access
        var exception = assertThrows(RuntimeException.class, emptyKeyManager::getAllPublicKeys);

        assertNotNull(exception.getMessage(), "Exception should have a message");
    }

    @Test
    void testKeyManagerWorksWithNoPublicKeys() {
        // Given: Key provider with only signing key, no public encryption keys
        PgpKeyGenerator keyGenerator = new PgpKeyGenerator();
        var keyInfo = keyGenerator.generate("Test User <test@example.com>");

        PgpKeyProvider providerWithNoPublicKeys = mock(PgpKeyProvider.class);
        when(providerWithNoPublicKeys.getSigningSecretKey()).thenReturn(keyInfo.secretData());
        when(providerWithNoPublicKeys.getEncryptionPublicKeys()).thenReturn(Optional.empty());

        PgpKeyManager keyManagerNoPublicKeys = new PgpKeyManager(providerWithNoPublicKeys);

        // When: Get all public keys
        Map<String, PGPPublicKey> publicKeys = keyManagerNoPublicKeys.getAllPublicKeys();

        // Then: Should have exactly one key - the signing public key
        assertEquals(1, publicKeys.size(), "Should have only the signing public key");

        String signingKeyId = keyInfo.keyId();
        assertTrue(publicKeys.containsKey(signingKeyId),
                "Public keys map should contain the signing public key");

        PGPPublicKey signingPublicKey = publicKeys.get(signingKeyId);
        assertNotNull(signingPublicKey, "Signing public key should be present");

        // Verify the signing key pair is also accessible
        PgpKeyPair signingKeyPair = keyManagerNoPublicKeys.getSigningKeyPair();
        assertNotNull(signingKeyPair, "Signing key pair should be accessible");
        assertEquals(signingKeyId, signingKeyPair.keyIdHex(),
                "Signing key pair should have the expected key ID");
    }
}

