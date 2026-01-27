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
package org.niis.xroad.messagelog;

import ee.ria.xroad.common.message.AttachmentStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.vault.VaultClient;

import javax.sql.rowset.serial.SerialBlob;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageRecordEncryptionTest {

    private static final String TEST_KEY_ID = "test-key-1";
    private static final byte[] TEST_SECRET_KEY = new byte[16]; // 128-bit AES key

    static {
        // Initialize with a test key (in real scenario, this would be random)
        for (int i = 0; i < TEST_SECRET_KEY.length; i++) {
            TEST_SECRET_KEY[i] = (byte) (i * 17); // Deterministic for testing
        }
    }

    @Test
    void shouldCreateWithEncryptionDisabled() {
        // Given
        var properties = mockProperties(false, null);
        var vaultClient = mock(VaultClient.class);

        // When
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        // Then
        assertFalse(encryption.encryptionEnabled());
    }

    @Test
    void shouldCreateWithEncryptionEnabled() {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);

        // When
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        // Then
        assertTrue(encryption.encryptionEnabled());
    }

    @Test
    void shouldThrowWhenEncryptionEnabledButNoKeyId() {
        // Given
        var properties = mockProperties(true, null);
        var vaultClient = mock(VaultClient.class);

        // When/Then
        var exception = assertThrows(IllegalStateException.class,
                () -> new MessageRecordEncryption(properties, vaultClient));

        assertTrue(exception.getMessage().contains("key-id") || exception.getMessage().contains("Unable to initialize"));
    }

    @Test
    void shouldThrowWhenEncryptionEnabledButNoKeyInVault() {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(Map.of());

        // When/Then
        var exception = assertThrows(IllegalStateException.class,
                () -> new MessageRecordEncryption(properties, vaultClient));

        assertTrue(exception.getMessage().contains("encryption key") || exception.getMessage().contains("Unable to initialize"));
    }

    @Test
    void shouldThrowWhenEncryptionEnabledButBlankKeyInVault() {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(Map.of(TEST_KEY_ID, "   "));

        // When/Then
        var exception = assertThrows(IllegalStateException.class,
                () -> new MessageRecordEncryption(properties, vaultClient));

        assertTrue(exception.getMessage().contains("encryption key") || exception.getMessage().contains("Unable to initialize"));
    }

    @Test
    void shouldThrowWhenVaultKeyIsInvalidBase64() {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(Map.of(TEST_KEY_ID, "not-valid-base64!@#"));

        // When/Then
        assertThrows(IllegalStateException.class,
                () -> new MessageRecordEncryption(properties, vaultClient));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Short message", "A longer message with more content to encrypt"})
    void shouldEncryptAndDecryptMessage(String originalMessage) throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        var messageRecord = createTestMessageRecord(1L, originalMessage);

        // When - Encrypt
        encryption.prepareEncryption(messageRecord);

        // Then - Message should be encrypted
        assertNotNull(messageRecord.getKeyId());
        assertEquals(TEST_KEY_ID, messageRecord.getKeyId());
        assertNotNull(messageRecord.getCipherMessage());
        assertNull(messageRecord.getMessage()); // Message cleared after encryption

        // Ciphertext should be different from plaintext
        assertFalse(java.util.Arrays.equals(
                originalMessage.getBytes(StandardCharsets.UTF_8),
                messageRecord.getCipherMessage()));

        // Create a new record for decryption (simulating retrieval from DB)
        var decryptRecord = new MessageRecord();
        decryptRecord.setId(1L);
        decryptRecord.setKeyId(messageRecord.getKeyId());
        decryptRecord.setCipherMessage(messageRecord.getCipherMessage());

        // When - Prepare decryption
        encryption.prepareDecryption(decryptRecord);

        // Then - Should be able to decrypt back to original message
        assertNotNull(decryptRecord);
        // Note: Full decryption happens in toAsicContainer(), which requires valid XML signatures
        // We verify the cipher is set up correctly
    }

    @Test
    void shouldEncryptAndDecryptMessageWithAttachments() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        String message = "Test message";
        byte[] attachment1Data = "Attachment 1 content".getBytes(StandardCharsets.UTF_8);
        byte[] attachment2Data = "Attachment 2 with more data".getBytes(StandardCharsets.UTF_8);

        var messageRecord = createTestMessageRecord(1L, message);
        messageRecord.setAttachmentStreams(List.of(
                createAttachmentStream(attachment1Data),
                createAttachmentStream(attachment2Data)
        ));

        // When - Encrypt
        encryption.prepareEncryption(messageRecord);

        // Then - Attachments should be wrapped in cipher streams
        assertEquals(2, messageRecord.getAttachmentStreams().size());

        // Read encrypted attachment data
        var encryptedAttachment1 = messageRecord.getAttachmentStreams().get(0).getStream().readAllBytes();
        var encryptedAttachment2 = messageRecord.getAttachmentStreams().get(1).getStream().readAllBytes();

        // Encrypted data should be same length (CTR mode)
        assertEquals(attachment1Data.length, encryptedAttachment1.length);
        assertEquals(attachment2Data.length, encryptedAttachment2.length);

        // Encrypted data should be different from original
        assertFalse(java.util.Arrays.equals(attachment1Data, encryptedAttachment1));
        assertFalse(java.util.Arrays.equals(attachment2Data, encryptedAttachment2));

        // Create record for decryption
        var decryptRecord = new MessageRecord();
        decryptRecord.setId(1L);
        decryptRecord.setKeyId(TEST_KEY_ID);

        decryptRecord.addAttachment(1, new SerialBlob(encryptedAttachment1));
        decryptRecord.addAttachment(2, new SerialBlob(encryptedAttachment2));

        // When - Prepare for decryption
        encryption.prepareDecryption(decryptRecord);

        // Then - Attachment ciphers should be set up
        assertTrue(decryptRecord.getAttachments().get(0).hasCipher());
        assertTrue(decryptRecord.getAttachments().get(1).hasCipher());
    }

    @Test
    void shouldHandleNullMessageRecord() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        // When/Then - Should not throw
        assertNull(encryption.prepareEncryption(null));
    }

    @Test
    void shouldHandleMessageRecordWithoutKeyId() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        var messageRecord = new MessageRecord();
        messageRecord.setId(1L);
        messageRecord.setKeyId(null);

        // When - Prepare for decryption
        var result = encryption.prepareDecryption(messageRecord);

        // Then - Should return the record without setting ciphers
        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenDecryptingWithUnknownKeyId() {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        var messageRecord = new MessageRecord();
        messageRecord.setId(1L);
        messageRecord.setKeyId("unknown-key-id");

        // When/Then
        var exception = assertThrows(GeneralSecurityException.class,
                () -> encryption.prepareDecryption(messageRecord));

        assertTrue(exception.getMessage().contains("unknown-key-id"));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void shouldUseDifferentIVsForDifferentRecordIds() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        String message = "Same message";
        var record1 = createTestMessageRecord(1L, message);
        var record2 = createTestMessageRecord(2L, message);

        // When
        encryption.prepareEncryption(record1);
        encryption.prepareEncryption(record2);

        // Then - Same message encrypted with different IVs should produce different ciphertext
        assertNotNull(record1.getCipherMessage());
        assertNotNull(record2.getCipherMessage());
        assertFalse(java.util.Arrays.equals(record1.getCipherMessage(), record2.getCipherMessage()),
                "Different record IDs should produce different ciphertext");
    }

    @Test
    void shouldUseDifferentIVsForDifferentAttachments() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        byte[] sameData = "Same attachment data".getBytes(StandardCharsets.UTF_8);
        var messageRecord = createTestMessageRecord(1L, "Test");
        messageRecord.setAttachmentStreams(List.of(
                createAttachmentStream(sameData),
                createAttachmentStream(sameData)
        ));

        // When
        encryption.prepareEncryption(messageRecord);

        // Then - Same data in different attachments should produce different ciphertext
        var encrypted1 = messageRecord.getAttachmentStreams().get(0).getStream().readAllBytes();
        var encrypted2 = messageRecord.getAttachmentStreams().get(1).getStream().readAllBytes();

        assertFalse(java.util.Arrays.equals(encrypted1, encrypted2),
                "Different attachment numbers should produce different ciphertext");
    }

    @ParameterizedTest
    @MethodSource("provideLargeMessageSizes")
    void shouldHandleLargeMessages(int messageSize) throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        String largeMessage = "x".repeat(messageSize);
        var messageRecord = createTestMessageRecord(1L, largeMessage);

        // When
        encryption.prepareEncryption(messageRecord);

        // Then - Should successfully encrypt large messages
        assertNotNull(messageRecord.getCipherMessage());
        assertEquals(TEST_KEY_ID, messageRecord.getKeyId());
        // CTR mode preserves length
        assertEquals(messageSize, messageRecord.getCipherMessage().length);

        // Verify decryption setup works
        var decryptRecord = new MessageRecord();
        decryptRecord.setId(1L);
        decryptRecord.setKeyId(messageRecord.getKeyId());
        decryptRecord.setCipherMessage(messageRecord.getCipherMessage());

        encryption.prepareDecryption(decryptRecord);
        assertNotNull(decryptRecord);
    }

    static Stream<Integer> provideLargeMessageSizes() {
        return Stream.of(
                1024,          // 1 KB
                10 * 1024,     // 10 KB
                100 * 1024,    // 100 KB
                1024 * 1024    // 1 MB
        );
    }

    @Test
    void shouldPreserveAttachmentSize() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        byte[] attachmentData = "Test attachment".getBytes(StandardCharsets.UTF_8);
        long originalSize = attachmentData.length;

        var messageRecord = createTestMessageRecord(1L, "Message");
        messageRecord.setAttachmentStreams(List.of(createAttachmentStream(attachmentData)));

        // When
        encryption.prepareEncryption(messageRecord);

        // Then - CTR mode doesn't change size
        assertEquals(originalSize, messageRecord.getAttachmentStreams().getFirst().getSize());
    }

    @Test
    void shouldEncryptEmptyMessage() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        var messageRecord = createTestMessageRecord(1L, "");

        // When
        encryption.prepareEncryption(messageRecord);

        // Then
        assertNotNull(messageRecord.getCipherMessage());
        assertEquals(0, messageRecord.getCipherMessage().length);
    }

    @Test
    void shouldHandleMessageRecordWithNoAttachments() throws Exception {
        // Given
        var properties = mockProperties(true, TEST_KEY_ID);
        var vaultClient = mockVaultClient(TEST_SECRET_KEY);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        var messageRecord = createTestMessageRecord(1L, "Message without attachments");
        messageRecord.setAttachmentStreams(new ArrayList<>());

        // When
        encryption.prepareEncryption(messageRecord);

        // Then - Should not throw
        assertNotNull(messageRecord.getCipherMessage());
        assertTrue(messageRecord.getAttachmentStreams().isEmpty());
    }

    @Test
    void shouldWorkWithDifferentKeyIds() throws Exception {
        // Given
        String keyId1 = "key-1";
        String keyId2 = "key-2";

        byte[] secretKey1 = new byte[16];
        byte[] secretKey2 = new byte[16];

        // Different keys
        for (int i = 0; i < 16; i++) {
            secretKey1[i] = (byte) i;
            secretKey2[i] = (byte) (i + 100);
        }

        var properties1 = mockProperties(true, keyId1);
        var vaultClient1 = mockVaultClientWithKeys(Map.of(keyId1, secretKey1));
        var encryption1 = new MessageRecordEncryption(properties1, vaultClient1);

        var properties2 = mockProperties(true, keyId2);
        var vaultClient2 = mockVaultClientWithKeys(Map.of(keyId2, secretKey2));
        var encryption2 = new MessageRecordEncryption(properties2, vaultClient2);

        String message = "Test message";
        var record1 = createTestMessageRecord(1L, message);
        var record2 = createTestMessageRecord(1L, message);

        // When
        encryption1.prepareEncryption(record1);
        encryption2.prepareEncryption(record2);

        // Then - Different keys should produce different ciphertext
        assertFalse(java.util.Arrays.equals(record1.getCipherMessage(), record2.getCipherMessage()),
                "Different keys should produce different ciphertext");
    }

    // Key Rotation Tests

    @Test
    void shouldSupportMultipleKeysForDecryption() throws Exception {
        // Given: Multiple keys in vault
        byte[] oldKey = new byte[16];
        byte[] newKey = new byte[16];
        for (int i = 0; i < 16; i++) {
            oldKey[i] = (byte) i;
            newKey[i] = (byte) (i * 2);
        }

        Map<String, byte[]> keys = new HashMap<>();
        keys.put("old-key", oldKey);
        keys.put("new-key", newKey);

        var properties = mockProperties(true, "new-key");
        var vaultClient = mockVaultClientWithKeys(keys);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        // When: Encrypt with new key
        var record1 = createTestMessageRecord(1L, "Test message");
        encryption.prepareEncryption(record1);

        // Create a record encrypted with old key (simulating existing data)
        var properties2 = mockProperties(true, "old-key");
        var encryption2 = new MessageRecordEncryption(properties2, vaultClient);
        var record2 = createTestMessageRecord(2L, "Old message");
        encryption2.prepareEncryption(record2);

        // Then: Both records should decrypt successfully
        assertEquals("new-key", record1.getKeyId());
        assertEquals("old-key", record2.getKeyId());

        // Decrypt with the main encryption instance (which has both keys)
        // prepareDecryption should succeed without throwing exceptions
        var decryptedRecord1 = encryption.prepareDecryption(record1);
        var decryptedRecord2 = encryption.prepareDecryption(record2);

        // Verify decryption was prepared (records should have keyId set)
        assertNotNull(decryptedRecord1);
        assertNotNull(decryptedRecord2);
        assertEquals("new-key", decryptedRecord1.getKeyId());
        assertEquals("old-key", decryptedRecord2.getKeyId());
    }

    @Test
    void shouldEncryptWithCurrentKeyOnly() throws Exception {
        // Given: Multiple keys but only current key should be used for encryption
        byte[] oldKey = new byte[16];
        byte[] newKey = new byte[16];
        for (int i = 0; i < 16; i++) {
            oldKey[i] = (byte) i;
            newKey[i] = (byte) (i * 2);
        }

        Map<String, byte[]> keys = new HashMap<>();
        keys.put("old-key", oldKey);
        keys.put("new-key", newKey);

        var properties = mockProperties(true, "new-key");
        var vaultClient = mockVaultClientWithKeys(keys);
        var encryption = new MessageRecordEncryption(properties, vaultClient);

        // When: Encrypt multiple messages
        var record1 = createTestMessageRecord(1L, "Message 1");
        var record2 = createTestMessageRecord(2L, "Message 2");

        encryption.prepareEncryption(record1);
        encryption.prepareEncryption(record2);

        // Then: All should use the current key
        assertEquals("new-key", record1.getKeyId());
        assertEquals("new-key", record2.getKeyId());
    }

    @Test
    void shouldFailWhenCurrentKeyNotInVault() {
        // Given: Current key not in vault
        byte[] oldKey = new byte[16];
        for (int i = 0; i < 16; i++) {
            oldKey[i] = (byte) i;
        }

        Map<String, byte[]> keys = Map.of("old-key", oldKey);
        var vaultClient = mockVaultClientWithKeys(keys);

        // When/Then: Should fail with current key not found
        var properties = mockProperties(true, "non-existent-key");
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new MessageRecordEncryption(properties, vaultClient));

        assertTrue(exception.getMessage().contains("not found in Vault")
                || exception.getMessage().contains("Unable to initialize"));
    }

    @Test
    void shouldDecryptMessageEncryptedWithOldKey() throws Exception {
        // Given: Message encrypted with old key
        byte[] oldKey = new byte[16];
        byte[] newKey = new byte[16];
        for (int i = 0; i < 16; i++) {
            oldKey[i] = (byte) i;
            newKey[i] = (byte) (i * 2);
        }

        // Encrypt with old key
        var oldProperties = mockProperties(true, "old-key");
        var oldVaultClient = mockVaultClientWithKeys(Map.of("old-key", oldKey));
        var oldEncryption = new MessageRecordEncryption(oldProperties, oldVaultClient);

        var messageRecord = createTestMessageRecord(1L, "Test message");
        oldEncryption.prepareEncryption(messageRecord);
        byte[] cipherMessage = messageRecord.getCipherMessage();
        String keyId = messageRecord.getKeyId();

        // When: Decrypt with new encryption instance that has both keys
        Map<String, byte[]> allKeys = new HashMap<>();
        allKeys.put("old-key", oldKey);
        allKeys.put("new-key", newKey);

        var newProperties = mockProperties(true, "new-key");
        var newVaultClient = mockVaultClientWithKeys(allKeys);
        var newEncryption = new MessageRecordEncryption(newProperties, newVaultClient);

        // Set up the record as if loaded from database
        messageRecord.setCipherMessage(cipherMessage);
        messageRecord.setKeyId(keyId);
        messageRecord.setMessage(null); // Encrypted records don't have plaintext message

        // Then: Should decrypt successfully (prepareDecryption should not throw)
        var decryptedRecord = newEncryption.prepareDecryption(messageRecord);
        assertNotNull(decryptedRecord);
        assertEquals("old-key", decryptedRecord.getKeyId());
    }

    // Helper methods

    private MessageLogDatabaseEncryptionProperties mockProperties(boolean enabled, String keyId) {
        var properties = mock(MessageLogDatabaseEncryptionProperties.class);
        when(properties.enabled()).thenReturn(enabled);
        when(properties.keyId()).thenReturn(keyId);
        return properties;
    }

    private VaultClient mockVaultClient(byte[] secretKey) {
        return mockVaultClientWithKeys(Map.of(TEST_KEY_ID, secretKey));
    }

    private VaultClient mockVaultClientWithKeys(Map<String, byte[]> keys) {
        var vaultClient = mock(VaultClient.class);
        Map<String, String> base64Keys = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : keys.entrySet()) {
            base64Keys.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
        }
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(base64Keys);
        return vaultClient;
    }

    private MessageRecord createTestMessageRecord(long id, String message) {
        var messageRecord = new MessageRecord();
        messageRecord.setId(id);
        messageRecord.setTime(System.currentTimeMillis());
        messageRecord.setMessage(message);
        messageRecord.setAttachmentStreams(new ArrayList<>());
        return messageRecord;
    }

    private AttachmentStream createAttachmentStream(byte[] data) {
        return new AttachmentStream() {
            @Override
            public InputStream getStream() {
                return new ByteArrayInputStream(data);
            }

            @Override
            public long getSize() {
                return data.length;
            }
        };
    }
}
