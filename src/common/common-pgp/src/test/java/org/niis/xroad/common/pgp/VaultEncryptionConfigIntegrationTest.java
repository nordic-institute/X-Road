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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niix.xroad.common.pgp.test.StreamingPgpDecryptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for PGP encryption that tests the complete encryption/decryption cycle.
 * Uses real PGP keys (generated for testing) with a mocked key provider.
 */
class VaultEncryptionConfigIntegrationTest {

    @TempDir
    Path tempDir;

    private PgpKeyManager keyManager;
    private StreamingPgpDecryptor decryptor;
    private String keyId;

    @BeforeEach
    void setUp() {
        // Generate real PGP test keys using test fixtures
        PgpKeyGenerator keyGenerator = new PgpKeyGenerator();
        var keyInfo = keyGenerator.generate("Test User <test@example.com>");

        // Extract generated key data
        // Key ID is already formatted with leading zeros by PgpKeyGenerator
        keyId = keyInfo.keyId();
        String secretKeyArmored = keyInfo.secretData();
        String publicKeysArmored = keyInfo.publicData();

        // Verify keys were generated
        assertNotNull(secretKeyArmored, "Secret key should be generated");
        assertNotNull(publicKeysArmored, "Public key should be generated");
        assertTrue(secretKeyArmored.contains("BEGIN PGP PRIVATE KEY BLOCK"), "Secret key should be armored");
        assertTrue(publicKeysArmored.contains("BEGIN PGP PUBLIC KEY BLOCK"), "Public key should be armored");

        // Create mock key provider that returns the generated keys
        PgpKeyProvider mockKeyProvider = mock(PgpKeyProvider.class);
        when(mockKeyProvider.getSigningSecretKey()).thenReturn(secretKeyArmored);
        when(mockKeyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(publicKeysArmored));

        // Initialize key manager and decryptor for tests
        keyManager = new PgpKeyManager(mockKeyProvider);
        decryptor = new StreamingPgpDecryptor();
    }


    @Test
    void testEncryptionConfigCreatesValidStream() throws Exception {
        // Given: Encryption service with test keys
        var encryption = createBouncyCastleEncryption();
        Path outputFile = tempDir.resolve("encrypted-test.pgp");

        // When: Encrypt data directly using BouncyCastleOutputStream
        try (var encryptionStream = new BouncyCastleOutputStream(encryption, outputFile, Collections.singleton(keyId))) {
            encryptionStream.write("Test data".getBytes(StandardCharsets.UTF_8));
        }

        // Then: File should be created and contain data
        assertNotNull(outputFile);
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);
    }

    @Test
    void testPublicKeyIsEncryptionCapable() {
        // Given: Generated keys
        createBouncyCastleEncryption();

        // When: Load public keys
        var allPublicKeys = keyManager.getAllPublicKeys();

        // Then: Should have at least one encryption-capable key
        assertFalse(allPublicKeys.isEmpty(), "Should have at least one public key");
        assertTrue(allPublicKeys.values().stream().anyMatch(org.bouncycastle.openpgp.PGPPublicKey::isEncryptionKey),
                "Should have at least one encryption-capable key");
        assertTrue(allPublicKeys.containsKey(keyId),
                "Should contain the generated key ID: " + keyId + ", but only has: " + allPublicKeys.keySet());
    }

    @Test
    void testFullEncryptionDecryptionCycle() throws Exception {
        // Given: Test data and encryption service
        String originalData = "This is sensitive test data that should be encrypted!";
        byte[] originalBytes = originalData.getBytes(StandardCharsets.UTF_8);

        var encryption = createBouncyCastleEncryption();
        Path encryptedFile = tempDir.resolve("encrypted-data.pgp");

        // When: Encrypt the data
        try (var encryptionStream = new BouncyCastleOutputStream(encryption, encryptedFile, Collections.singleton(keyId))) {
            encryptionStream.write(originalBytes);
        }

        // Then: Encrypted file should exist and be different from original
        assertTrue(Files.exists(encryptedFile));
        byte[] encryptedBytes = Files.readAllBytes(encryptedFile);
        assertTrue(encryptedBytes.length > 0);
        assertTrue(encryptedBytes.length > originalBytes.length, "Encrypted data should be larger due to PGP overhead");

        // Verify it's actual PGP data (starts with PGP marker)
        String encryptedContent = new String(encryptedBytes, StandardCharsets.UTF_8);
        assertTrue(encryptedContent.contains("BEGIN PGP MESSAGE") || isBinaryPGP(encryptedBytes),
                "File should contain PGP encrypted data");

        // When: Decrypt the data
        Path decryptedFile = tempDir.resolve("decrypted-data.txt");
        try (InputStream encryptedInput = Files.newInputStream(encryptedFile);
             OutputStream decryptedOutput = Files.newOutputStream(decryptedFile)) {
            decryptAndVerify(encryptedInput, decryptedOutput);
        }

        // Then: Decrypted data should match original
        byte[] decryptedBytes = Files.readAllBytes(decryptedFile);
        assertArrayEquals(originalBytes, decryptedBytes, "Decrypted data should match original");

        String decryptedData = new String(decryptedBytes, StandardCharsets.UTF_8);
        assertEquals(originalData, decryptedData, "Decrypted text should match original text");
    }

    @Test
    void testEncryptionWithLargerData() throws Exception {
        // Given: Larger test data (1MB)
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        var encryption = createBouncyCastleEncryption();
        Path encryptedFile = tempDir.resolve("large-encrypted.pgp");

        // When: Encrypt large data
        try (var encryptionStream = new BouncyCastleOutputStream(encryption, encryptedFile, Collections.singleton(keyId))) {
            encryptionStream.write(largeData);
        }

        // Then: Should encrypt successfully
        assertTrue(Files.exists(encryptedFile));
        assertTrue(Files.size(encryptedFile) > 0);

        // When: Decrypt large data
        Path decryptedFile = tempDir.resolve("large-decrypted.bin");
        try (InputStream encryptedInput = Files.newInputStream(encryptedFile);
             OutputStream decryptedOutput = Files.newOutputStream(decryptedFile)) {
            decryptAndVerify(encryptedInput, decryptedOutput);
        }

        // Then: Should decrypt correctly
        byte[] decryptedData = Files.readAllBytes(decryptedFile);
        assertArrayEquals(largeData, decryptedData, "Large data should encrypt and decrypt correctly");
    }

    @Test
    void testEncryptionWithSelfEncryption() throws Exception {
        // Given: Self-encryption (null key IDs)
        String testData = "Self-encrypted test data";
        byte[] testBytes = testData.getBytes(StandardCharsets.UTF_8);

        var encryption = createBouncyCastleEncryption();
        Path encryptedFile = tempDir.resolve("self-encrypted.pgp");

        // When: Encrypt with self-encryption (null recipient keys)
        try (var encryptionStream = new BouncyCastleOutputStream(encryption, encryptedFile, null)) {
            encryptionStream.write(testBytes);
        }

        // Then: Should encrypt and be able to decrypt
        assertTrue(Files.exists(encryptedFile));

        Path decryptedFile = tempDir.resolve("self-decrypted.txt");
        try (InputStream encryptedInput = Files.newInputStream(encryptedFile);
             OutputStream decryptedOutput = Files.newOutputStream(decryptedFile)) {
            decryptAndVerify(encryptedInput, decryptedOutput);
        }

        byte[] decryptedBytes = Files.readAllBytes(decryptedFile);
        assertArrayEquals(testBytes, decryptedBytes);
    }

    @Test
    void testMultipleEncryptionOperations() throws Exception {
        // Given: Multiple pieces of data to encrypt
        var encryption = createBouncyCastleEncryption();

        String[] testData = {
                "First message",
                "Second message with more content",
                "Third message: 123456789"
        };

        // When: Encrypt multiple files
        Path[] encryptedFiles = new Path[testData.length];
        for (int i = 0; i < testData.length; i++) {
            encryptedFiles[i] = tempDir.resolve("encrypted-" + i + ".pgp");
            try (var stream = new BouncyCastleOutputStream(encryption, encryptedFiles[i], Collections.singleton(keyId))) {
                stream.write(testData[i].getBytes(StandardCharsets.UTF_8));
            }
        }

        // Then: All should encrypt and decrypt correctly
        for (int i = 0; i < testData.length; i++) {
            Path decryptedFile = tempDir.resolve("decrypted-" + i + ".txt");
            try (InputStream encryptedInput = Files.newInputStream(encryptedFiles[i]);
                 OutputStream decryptedOutput = Files.newOutputStream(decryptedFile)) {
                decryptAndVerify(encryptedInput, decryptedOutput);
            }

            String decrypted = Files.readString(decryptedFile, StandardCharsets.UTF_8);
            assertEquals(testData[i], decrypted, "Message " + i + " should decrypt correctly");
        }
    }

    /**
     * Helper method to create PGP encryption service with mocked key provider.
     * Returns the encryption service for use with BouncyCastleOutputStream.
     */
    private BouncyCastlePgpEncryptionService createBouncyCastleEncryption() {
        var keyResolver = new PgpKeyResolver(keyManager);
        var encryptor = new StreamingPgpEncryptor();
        return new BouncyCastlePgpEncryptionService(keyManager, keyResolver, encryptor);
    }

    /**
     * Helper method to decrypt and verify encrypted PGP data for testing.
     * In production, decryption is not needed for message log archiving.
     */
    private void decryptAndVerify(InputStream input, OutputStream output)
            throws IOException, PGPException {
        var signingKeyPair = keyManager.getSigningKeyPair();
        decryptor.decryptAndVerify(input, output, signingKeyPair);
    }

    /**
     * Helper method to check if bytes represent binary PGP data.
     * Binary PGP messages start with specific packet tags.
     */
    private boolean isBinaryPGP(byte[] data) {
        if (data.length < 2) {
            return false;
        }
        // Check for PGP packet tag (0x80 - 0xFF for old format, 0xC0 - 0xFF for new format)
        return (data[0] & 0x80) != 0;
    }

    @Test
    void testDecryptionFailsWithCorruptedData() {
        // Given: Invalid/corrupted PGP data
        byte[] corruptedData = "This is not valid PGP data at all!".getBytes(StandardCharsets.UTF_8);

        // When/Then: Decryption should fail with PGPException
        assertThrows(PGPException.class, () -> decryptCorruptedData(corruptedData));
    }

    @Test
    void testDecryptionFailsWithEmptyData() {
        // Given: Empty data
        byte[] emptyData = new byte[0];

        // When/Then: Decryption should fail
        assertThrows(Exception.class, () -> decryptCorruptedData(emptyData));
    }

    private void decryptCorruptedData(byte[] data) throws IOException, PGPException {
        try (var input = new java.io.ByteArrayInputStream(data);
             var output = new java.io.ByteArrayOutputStream()) {
            decryptAndVerify(input, output);
        }
    }

    @Test
    void testDecryptionFailsWhenDataTampered() throws Exception {
        // Given: Encrypt some data
        String originalData = "This is important data that must not be tampered with!";
        byte[] originalBytes = originalData.getBytes(StandardCharsets.UTF_8);

        var encryption = createBouncyCastleEncryption();
        Path encryptedFile = tempDir.resolve("tamper-test.pgp");

        // Encrypt the data
        try (var encryptionStream = new BouncyCastleOutputStream(encryption, encryptedFile, Collections.singleton(keyId))) {
            encryptionStream.write(originalBytes);
        }

        // When: Tamper with the encrypted data by flipping bits
        byte[] encryptedBytes = Files.readAllBytes(encryptedFile);
        assertTrue(encryptedBytes.length > 100, "Encrypted data should be large enough to tamper with");

        // Tamper with multiple bytes in the middle of the encrypted data
        int tamperPosition = encryptedBytes.length / 2;
        encryptedBytes[tamperPosition] ^= (byte) 0xFF;
        encryptedBytes[tamperPosition + 1] ^= (byte) 0xAA;
        encryptedBytes[tamperPosition + 2] ^= 0x55;

        Path tamperedFile = tempDir.resolve("tampered.pgp");
        Files.write(tamperedFile, encryptedBytes);

        // Then: Decryption/verification should fail due to integrity check or signature verification
        // Note: Tampering can cause different types of exceptions (PGPException, ClassCastException, etc.)
        // depending on where the data was corrupted, so we expect any Exception
        assertThrows(Exception.class, () -> decryptFile(tamperedFile, tempDir.resolve("tampered-decrypted.txt")));
    }

    @Test
    void testDecryptionFailsWithTruncatedData() throws Exception {
        // Given: Encrypt some data
        String originalData = "Complete message data";
        byte[] originalBytes = originalData.getBytes(StandardCharsets.UTF_8);

        var encryption = createBouncyCastleEncryption();
        Path encryptedFile = tempDir.resolve("truncate-test.pgp");

        // Encrypt the data
        try (var encryptionStream = new BouncyCastleOutputStream(encryption, encryptedFile, Collections.singleton(keyId))) {
            encryptionStream.write(originalBytes);
        }

        // When: Truncate the encrypted data (remove last 50 bytes)
        byte[] encryptedBytes = Files.readAllBytes(encryptedFile);
        assertTrue(encryptedBytes.length > 100, "Encrypted data should be large enough to truncate");

        byte[] truncatedBytes = new byte[encryptedBytes.length - 50];
        System.arraycopy(encryptedBytes, 0, truncatedBytes, 0, truncatedBytes.length);

        Path truncatedFile = tempDir.resolve("truncated.pgp");
        Files.write(truncatedFile, truncatedBytes);

        // Then: Decryption should fail due to incomplete data
        assertThrows(Exception.class, () -> decryptFile(truncatedFile, tempDir.resolve("truncated-decrypted.txt")));
    }

    private void decryptFile(Path encryptedFile, Path decryptedFile) throws IOException, PGPException {
        try (InputStream encryptedInput = Files.newInputStream(encryptedFile);
             OutputStream decryptedOutput = Files.newOutputStream(decryptedFile)) {
            decryptAndVerify(encryptedInput, decryptedOutput);
        }
    }
}

