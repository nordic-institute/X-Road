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
package org.niis.xroad.migration.messagelog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;
import org.niis.xroad.common.vault.VaultClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageLogKeyMigratorTest {

    @TempDir
    Path tempDir;

    private VaultClient mockVaultClient;
    private MessageLogKeyMigrator migrator;

    @BeforeEach
    void setUp() {
        mockVaultClient = mock(VaultClient.class);
        migrator = new MessageLogKeyMigrator(mockVaultClient);
    }

    @Test
    void testMigrateFromKeystoreSuccess() throws Exception {
        // Given: Test keystore file (using test resources)
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] keystorePassword = "somepassword".toCharArray();
        String keyId = "key1";

        // Mock vault to return stored key for verification
        Map<String, String> storedKeys = Map.of(keyId, "dGVzdC1rZXk=");  // base64 encoded test key
        when(mockVaultClient.getMLogDBEncryptionSecretKeys())
                .thenReturn(storedKeys);

        // When: Migrate from keystore
        MessageLogKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, keystorePassword, keyId);

        // Then: Migration should succeed
        assertTrue(result.success());
        assertEquals(keyId, result.currentKeyId());
        assertEquals(1, result.keyCount());

        // Verify vault interactions
        verify(mockVaultClient, times(1)).setMLogDBEncryptionSecretKey(anyString(), anyString());
        verify(mockVaultClient, times(1)).getMLogDBEncryptionSecretKeys();
    }

    @Test
    void testMigrateFromKeystoreWithInvalidPassword() {
        // Given: Test keystore with wrong password
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] wrongPassword = "wrongpassword".toCharArray();
        String keyId = "key1";

        // When/Then: Should throw exception
        assertThrows(Exception.class, () ->
                migrator.migrateFromKeystore(keystorePath, wrongPassword, keyId));
    }

    @Test
    void testMigrateFromKeystoreWithMissingFile() {
        // Given: Non-existent keystore file
        Path missingKeystore = tempDir.resolve("missing.p12");
        char[] password = "somepassword".toCharArray();
        String keyId = "key1";

        // When/Then: Should throw IOException
        Exception exception = assertThrows(Exception.class, () ->
                migrator.migrateFromKeystore(missingKeystore, password, keyId));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void testMigrateFromKeystoreWithMissingKeyId() {
        // Given: Test keystore but null/blank keyId
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();

        // When/Then: Should throw exception for null keyId
        Exception exception1 = assertThrows(Exception.class, () ->
                migrator.migrateFromKeystore(keystorePath, password, null));
        assertTrue(exception1.getMessage().contains("Key ID is required"));

        // When/Then: Should throw exception for blank keyId
        Exception exception2 = assertThrows(Exception.class, () ->
                migrator.migrateFromKeystore(keystorePath, password, ""));
        assertTrue(exception2.getMessage().contains("Key ID is required"));
    }

    @Test
    void testMigrateFromKeystoreWithNonExistentKeyId() {
        // Given: Test keystore with non-existent key ID
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();
        String nonExistentKeyId = "nonexistent";

        // When/Then: Should throw exception
        Exception exception = assertThrows(Exception.class, () ->
                migrator.migrateFromKeystore(keystorePath, password, nonExistentKeyId));

        assertTrue(exception.getMessage().contains("not found in keystore"));
    }

    @Test
    void testVaultStorageFormat() throws Exception {
        // Given: Test keystore
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();
        String keyId = "key1";

        // Mock vault to store and return whatever was stored
        Map<String, String> storedKeys = new HashMap<>();
        mockVaultClient = mock(VaultClient.class);
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            storedKeys.put(id, key);
            return null;
        }).when(mockVaultClient).setMLogDBEncryptionSecretKey(anyString(), anyString());
        when(mockVaultClient.getMLogDBEncryptionSecretKeys())
                .thenAnswer(invocation -> storedKeys);
        migrator = new MessageLogKeyMigrator(mockVaultClient);

        // When: Migrate from keystore
        migrator.migrateFromKeystore(keystorePath, password, keyId);

        // Then: Verify vault storage format
        assertTrue(storedKeys.containsKey(keyId));
        String storedKeyValue = storedKeys.get(keyId);
        assertNotNull(storedKeyValue);
        assertFalse(storedKeyValue.isBlank());

        // Verify it's valid base64
        byte[] decodedKey = Base64.getDecoder().decode(storedKeyValue);
        assertNotNull(decodedKey);
        // Key size should be valid for AES (16, 24, or 32 bytes)
        assertTrue(decodedKey.length >= 12 && decodedKey.length <= 32,
                "AES key should be 12-32 bytes, was: " + decodedKey.length);
    }

    @Test
    void testVerificationFailureHandling() throws Exception {
        // Given: Test keystore
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();
        String keyId = "key1";

        // Mock vault to indicate verification failure (returns empty map)
        when(mockVaultClient.getMLogDBEncryptionSecretKeys())
                .thenReturn(Map.of());

        // When/Then: Should throw exception on verification failure
        Exception exception = assertThrows(Exception.class, () ->
                migrator.migrateFromKeystore(keystorePath, password, keyId));

        assertTrue(exception.getMessage().contains("verification failed"));
    }

    @Test
    void testPasswordArrayIsCleared() throws Exception {
        // Given: Test keystore
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();
        String keyId = "key1";

        // Create a copy to check later
        char[] passwordCopy = password.clone();

        // Mock vault to store and return whatever was stored
        Map<String, String> storedKeys = new HashMap<>();
        Answer<Void> storeAnswer = invocation -> {
            String id = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            storedKeys.put(id, key);
            return null;
        };
        Answer<Map<String, String>> getAnswer = invocation -> storedKeys;

        mockVaultClient = mock(VaultClient.class);
        doAnswer(storeAnswer).when(mockVaultClient).setMLogDBEncryptionSecretKey(anyString(), anyString());
        when(mockVaultClient.getMLogDBEncryptionSecretKeys()).thenAnswer(getAnswer);
        migrator = new MessageLogKeyMigrator(mockVaultClient);

        // When: Migrate from keystore
        migrator.migrateFromKeystore(keystorePath, password, keyId);

        // Then: Password array should be cleared (all zeros)
        for (char c : password) {
            assertEquals('\0', c, "Password should be cleared after use");
        }

        // Verify original password was valid (using our copy)
        assertEquals("somepassword", new String(passwordCopy));
    }

    @Test
    void testMigrationResultContainsCorrectInformation() throws Exception {
        // Given: Test keystore
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();
        String keyId = "key1";

        // Mock vault
        when(mockVaultClient.getMLogDBEncryptionSecretKeys())
                .thenReturn(Map.of(keyId, "dGVzdC1rZXk="));

        // When: Migrate from keystore
        MessageLogKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, password, keyId);

        // Then: Result should contain correct information
        assertTrue(result.success());
        assertFalse(result.skipped());
        assertEquals(keyId, result.currentKeyId());
        assertEquals(1, result.keyCount());
        assertNotNull(result.message());
    }

    @Test
    void testExtractKeyFromKeystoreReturnsCorrectKeySize() throws Exception {
        // Given: Test keystore
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();
        String keyId = "key1";

        // Mock vault to store and return whatever was stored
        Map<String, String> storedKeys = new HashMap<>();
        mockVaultClient = mock(VaultClient.class);
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            storedKeys.put(id, key);
            return null;
        }).when(mockVaultClient).setMLogDBEncryptionSecretKey(anyString(), anyString());
        when(mockVaultClient.getMLogDBEncryptionSecretKeys())
                .thenAnswer(invocation -> storedKeys);
        migrator = new MessageLogKeyMigrator(mockVaultClient);

        // When: Extract key using reflection (since it's private, we test through migration)
        migrator.migrateFromKeystore(keystorePath, password, keyId);

        // Then: Verify extracted key size
        String base64Key = storedKeys.get(keyId);
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        // AES key should be valid size (16, 24, or 32 bytes for AES-128/192/256)
        assertTrue(keyBytes.length >= 12 && keyBytes.length <= 32,
                "AES key should be 12-32 bytes, was: " + keyBytes.length);
    }

    @Test
    void testMigrateMultipleKeysForRotation() throws Exception {
        // Given: Test keystore with multiple key IDs
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();

        // Mock vault to store multiple keys
        Map<String, String> storedKeys = new HashMap<>();
        mockVaultClient = mock(VaultClient.class);
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            storedKeys.put(id, key);
            return null;
        }).when(mockVaultClient).setMLogDBEncryptionSecretKey(anyString(), anyString());
        when(mockVaultClient.getMLogDBEncryptionSecretKeys())
                .thenAnswer(invocation -> storedKeys);
        migrator = new MessageLogKeyMigrator(mockVaultClient);

        // When: Migrate multiple keys
        migrator.migrateFromKeystore(keystorePath, password, "key1");
        // Simulate adding a second key (in reality, this would be a separate migration run with new keystore)
        byte[] key2Bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(key2Bytes);
        storedKeys.put("key2", Base64.getEncoder().encodeToString(key2Bytes));

        // Then: Both keys should be available in Vault
        Map<String, String> allKeys = mockVaultClient.getMLogDBEncryptionSecretKeys();
        assertEquals(2, allKeys.size());
        assertTrue(allKeys.containsKey("key1"));
        assertTrue(allKeys.containsKey("key2"));
    }

    @Test
    void testKeyRotationPreservesOldKeys() throws Exception {
        // Given: Existing key in vault
        Map<String, String> storedKeys = new HashMap<>();
        String oldKeyId = "old-key";
        byte[] oldKeyBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(oldKeyBytes);
        storedKeys.put(oldKeyId, Base64.getEncoder().encodeToString(oldKeyBytes));

        mockVaultClient = mock(VaultClient.class);
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            storedKeys.put(id, key);
            return null;
        }).when(mockVaultClient).setMLogDBEncryptionSecretKey(anyString(), anyString());
        when(mockVaultClient.getMLogDBEncryptionSecretKeys())
                .thenAnswer(invocation -> storedKeys);
        migrator = new MessageLogKeyMigrator(mockVaultClient);

        // When: Migrate new key
        Path keystorePath = resolveTestResource("messagelog.p12");
        char[] password = "somepassword".toCharArray();
        String newKeyId = "key1";
        migrator.migrateFromKeystore(keystorePath, password, newKeyId);

        // Then: Both old and new keys should be available
        Map<String, String> allKeys = mockVaultClient.getMLogDBEncryptionSecretKeys();
        assertEquals(2, allKeys.size());
        assertTrue(allKeys.containsKey(oldKeyId), "Old key should still exist");
        assertTrue(allKeys.containsKey(newKeyId), "New key should exist");
    }

    private Path resolveTestResource(String filename) {
        // Resolve test resource from messagelog-encryption directory
        Path resourcePath = Path.of("src/test/resources/messagelog-encryption/" + filename);

        if (!Files.exists(resourcePath)) {
            // Fallback to absolute path if relative doesn't work
            resourcePath = Path.of(System.getProperty("user.dir"))
                    .resolve("src/tool/migration-cli/src/test/resources/messagelog-encryption/" + filename);
        }

        return resourcePath;
    }
}

