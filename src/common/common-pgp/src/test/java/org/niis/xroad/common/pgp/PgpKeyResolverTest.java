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

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PgpKeyResolver - focuses on error handling scenarios.
 */
class PgpKeyResolverTest {

    private PgpKeyManager mockKeyManager;
    private PgpKeyResolver keyResolver;
    private String testKeyId;

    @BeforeEach
    void setUp() {
        // Generate test key
        PgpKeyGenerator keyGenerator = new PgpKeyGenerator();
        var keyInfo = keyGenerator.generate("Test User <test@example.com>");
        testKeyId = keyInfo.keyId();

        // Mock key provider
        PgpKeyProvider mockKeyProvider = mock(PgpKeyProvider.class);
        when(mockKeyProvider.getSigningSecretKey()).thenReturn(keyInfo.secretData());
        when(mockKeyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(keyInfo.publicData()));

        mockKeyManager = new PgpKeyManager(mockKeyProvider);
        keyResolver = new PgpKeyResolver(mockKeyManager);
    }

    @Test
    void testResolveRecipientsWithNullUsesSigningKey() throws PGPException {
        // When: Resolve with null key IDs (self-encryption)
        List<PGPPublicKey> recipients = keyResolver.resolveRecipients(null);

        // Then: Should return signing key
        assertNotNull(recipients);
        assertEquals(1, recipients.size());
        assertEquals(mockKeyManager.getSigningKeyPair().publicKey().getKeyID(), recipients.getFirst().getKeyID());
    }

    @Test
    void testResolveRecipientsWithEmptySetUsesSigningKey() throws PGPException {
        // When: Resolve with empty set (self-encryption)
        List<PGPPublicKey> recipients = keyResolver.resolveRecipients(Set.of());

        // Then: Should return signing key
        assertNotNull(recipients);
        assertEquals(1, recipients.size());
        assertEquals(mockKeyManager.getSigningKeyPair().publicKey().getKeyID(), recipients.getFirst().getKeyID());
    }

    @Test
    void testResolveRecipientsWithValidKeyId() throws PGPException {
        // When: Resolve with valid key ID
        List<PGPPublicKey> recipients = keyResolver.resolveRecipients(Set.of(testKeyId));

        // Then: Should return the requested key
        assertNotNull(recipients);
        assertEquals(1, recipients.size());
    }

    @Test
    void testResolveRecipientsThrowsExceptionForMissingKey() {
        // Given: Non-existent key ID
        String missingKeyId = "AAAAAAAAAAAAAAAA";

        // When: Try to resolve non-existent key
        PGPException exception = assertThrows(PGPException.class,
                () -> keyResolver.resolveRecipients(Set.of(missingKeyId)));

        // Then: Should throw exception with helpful message
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Public keys not found"),
                "Exception should mention missing keys");
        assertTrue(exception.getMessage().contains(missingKeyId),
                "Exception should include the missing key ID");
    }

    @Test
    void testResolveRecipientsThrowsExceptionForMultipleMissingKeys() {
        // Given: Multiple non-existent key IDs
        String missingKey1 = "AAAAAAAAAAAAAAAA";
        String missingKey2 = "BBBBBBBBBBBBBBBB";
        Set<String> missingKeys = Set.of(missingKey1, missingKey2);

        // When: Try to resolve non-existent keys
        PGPException exception = assertThrows(PGPException.class,
                () -> keyResolver.resolveRecipients(missingKeys));

        // Then: Should throw exception mentioning all missing keys
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains(missingKey1),
                "Exception should include first missing key ID");
        assertTrue(exception.getMessage().contains(missingKey2),
                "Exception should include second missing key ID");
    }

    @Test
    void testResolveRecipientsWithMixedValidAndInvalidKeys() {
        // Given: Mix of valid and invalid key IDs
        String invalidKeyId = "CCCCCCCCCCCCCCCC";
        Set<String> mixedKeys = Set.of(testKeyId, invalidKeyId);

        // When: Try to resolve mixed keys
        PGPException exception = assertThrows(PGPException.class,
                () -> keyResolver.resolveRecipients(mixedKeys));

        // Then: Should fail and mention the invalid key
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains(invalidKeyId),
                "Exception should include the invalid key ID");
    }
}

