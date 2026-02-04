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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.vault.VaultClient;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EncryptionInitializationService.
 */
@ExtendWith(MockitoExtension.class)
class EncryptionInitializationServiceTest {

    private static final String SECURITY_SERVER_ID = "TEST-SERVER-001";
    private static final String DEFAULT_KEY_ID = "default";
    private static final String EXISTING_KEY_ID = "existing-key-1";

    @Mock
    private VaultClient vaultClient;

    private EncryptionInitializationService encryptionInitializationService;

    @BeforeEach
    void setUp() {
        // Create a new instance with mocked VaultClient
        // Note: PgpKeyGenerator is created internally, so we use the real implementation
        encryptionInitializationService = new EncryptionInitializationService(vaultClient);
    }

    @Test
    void initializeMessageLogArchivalEncryptionSuccess() {
        // Given
        ArgumentCaptor<String> secretKeyCaptor = ArgumentCaptor.forClass(String.class);

        // When
        encryptionInitializationService.initializeMessageLogArchivalEncryption(SECURITY_SERVER_ID);

        // Then
        verify(vaultClient, times(1)).setMLogArchivalSigningSecretKey(secretKeyCaptor.capture());

        // Verify that a secret key was stored (PGP armored format)
        String secretKey = secretKeyCaptor.getValue();
        assertThat(secretKey)
                .isNotNull()
                .isNotBlank()
                .as("Stored key should be in PGP armored format")
                .satisfies(key -> assertThat(key.contains("BEGIN PGP PRIVATE KEY BLOCK")
                        || key.contains("BEGIN PGP SECRET KEY BLOCK")).isTrue());
    }

    @Test
    void initializeMessageLogDatabaseEncryptionNoExistingKeysSuccess() {
        // Given
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(Collections.emptyMap());

        ArgumentCaptor<String> keyIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> base64KeyCaptor = ArgumentCaptor.forClass(String.class);

        // When
        encryptionInitializationService.initializeMessageLogDatabaseEncryption();

        // Then
        verify(vaultClient, times(1)).getMLogDBEncryptionSecretKeys();
        verify(vaultClient, times(1)).setMLogDBEncryptionSecretKey(keyIdCaptor.capture(), base64KeyCaptor.capture());

        // Verify key ID
        assertThat(keyIdCaptor.getValue()).isEqualTo(DEFAULT_KEY_ID);

        // Verify key is valid base64 and 32 bytes (256-bit)
        String base64Key = base64KeyCaptor.getValue();
        assertThat(base64Key)
                .isNotNull()
                .isNotBlank();

        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        assertThat(decodedKey)
                .as("Master key should be 32 bytes (256-bit)")
                .hasSize(32);
    }

    @Test
    void initializeMessageLogDatabaseEncryptionExistingKeysSkipsInitialization() {
        // Given
        var existingKeys = Map.of(
                EXISTING_KEY_ID, "existing-base64-key-1",
                "existing-key-2", "existing-base64-key-2"
        );
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(existingKeys);

        // When
        encryptionInitializationService.initializeMessageLogDatabaseEncryption();

        // Then
        verify(vaultClient, times(1)).getMLogDBEncryptionSecretKeys();
        verify(vaultClient, never()).setMLogDBEncryptionSecretKey(anyString(), anyString());
    }

    @Test
    void initializeMessageLogDatabaseEncryptionGeneratesUniqueKeys() {
        // Given
        when(vaultClient.getMLogDBEncryptionSecretKeys())
                .thenReturn(Collections.emptyMap())
                .thenReturn(Collections.emptyMap());

        ArgumentCaptor<String> base64KeyCaptor = ArgumentCaptor.forClass(String.class);

        // When - initialize first time
        encryptionInitializationService.initializeMessageLogDatabaseEncryption();
        verify(vaultClient, times(1)).setMLogDBEncryptionSecretKey(eq(DEFAULT_KEY_ID), base64KeyCaptor.capture());
        String key1 = base64KeyCaptor.getValue();

        // Reset captor and initialize second time
        base64KeyCaptor = ArgumentCaptor.forClass(String.class);
        encryptionInitializationService.initializeMessageLogDatabaseEncryption();
        verify(vaultClient, times(2)).setMLogDBEncryptionSecretKey(eq(DEFAULT_KEY_ID), base64KeyCaptor.capture());
        String key2 = base64KeyCaptor.getValue();

        // Then - verify keys are different (random generation)
        assertThat(key1)
                .as("Each initialization should generate a unique key")
                .isNotEqualTo(key2);
    }

    @Test
    void initializeMessageLogDatabaseEncryptionKeyIsValidBase64() {
        // Given
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(Collections.emptyMap());

        ArgumentCaptor<String> base64KeyCaptor = ArgumentCaptor.forClass(String.class);

        // When
        encryptionInitializationService.initializeMessageLogDatabaseEncryption();

        // Then
        verify(vaultClient).setMLogDBEncryptionSecretKey(eq(DEFAULT_KEY_ID), base64KeyCaptor.capture());

        String base64Key = base64KeyCaptor.getValue();
        // Verify it's valid base64 by decoding it
        assertThatNoException()
                .as("Generated key should be valid base64")
                .isThrownBy(() -> Base64.getDecoder().decode(base64Key));
    }

    @Test
    void initializeMessageLogDatabaseEncryptionUsesDefaultKeyId() {
        // Given
        when(vaultClient.getMLogDBEncryptionSecretKeys()).thenReturn(Collections.emptyMap());

        ArgumentCaptor<String> keyIdCaptor = ArgumentCaptor.forClass(String.class);

        // When
        encryptionInitializationService.initializeMessageLogDatabaseEncryption();

        // Then
        verify(vaultClient).setMLogDBEncryptionSecretKey(keyIdCaptor.capture(), anyString());
        assertThat(keyIdCaptor.getValue())
                .as("Should use default key ID constant")
                .isEqualTo(DEFAULT_KEY_ID);
    }
}

