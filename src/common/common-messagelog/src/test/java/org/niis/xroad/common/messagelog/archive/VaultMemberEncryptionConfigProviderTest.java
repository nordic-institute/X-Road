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
package org.niis.xroad.common.messagelog.archive;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyGenerator;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.pgp.PgpKeyUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for VaultMemberEncryptionConfigProvider.
 * Refactored from the old MemberEncryptionConfigProviderTest to work with the new Vault-based implementation.
 */
class VaultMemberEncryptionConfigProviderTest {

    private final Map<String, Set<String>> expectedMappings = new HashMap<>();
    private PgpKeyManager mockKeyManager;
    private VaultMemberEncryptionConfigProvider provider;

    // Test keys
    private PGPPublicKey testMemberKey;
    private PGPPublicKey testDefaultKey;
    private String testMemberKeyId;
    private String testDefaultKeyId;

    {
        expectedMappings.put("INSTANCE/memberClass/memberCode", setOf("B23B8E993AC4632A896D39A27BE94D3451C16D33"));
        expectedMappings.put("withEquals=", setOf("=Föö <foo@example.org>"));
        expectedMappings.put("test", setOf("key#1", "key#2"));
        expectedMappings.put("#comment escape#", setOf("#42"));
        expectedMappings.put("backslash\\=equals", setOf("1"));
        expectedMappings.put("backslash\\#hash", setOf("1"));
    }

    @BeforeEach
    void setUp() throws IOException, PGPException {
        // Set up system properties for message log configuration
        System.setProperty(MessageLogProperties.ARCHIVE_DEFAULT_ENCRYPTION_KEY,
                "B23B8E993AC4632A896D39A27BE94D3451C16D55");
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG,
                "build/resources/test/mlog-keys.ini");

        // Generate test keys
        PgpKeyGenerator keyGenerator = new PgpKeyGenerator();

        // Generate member-specific key
        var memberKeyInfo = keyGenerator.generate("Member Key <member@test.org>");

        // Generate default key
        var defaultKeyInfo = keyGenerator.generate("Default Key <default@test.org>");

        // Extract public keys from generated key info
        // We need to parse the armored public key data
        try (var inputStream = new ByteArrayInputStream(memberKeyInfo.publicData().getBytes())) {
            var publicKeyRing = new PGPPublicKeyRingCollection(
                    PGPUtil.getDecoderStream(inputStream),
                    new JcaKeyFingerprintCalculator()
            );
            testMemberKey = publicKeyRing.getKeyRings().next().getPublicKey();
        }

        try (var inputStream = new ByteArrayInputStream(defaultKeyInfo.publicData().getBytes())) {
            var publicKeyRing = new PGPPublicKeyRingCollection(
                    PGPUtil.getDecoderStream(inputStream),
                    new JcaKeyFingerprintCalculator()
            );
            testDefaultKey = publicKeyRing.getKeyRings().next().getPublicKey();
        }

        // Use the actual generated key IDs
        testMemberKeyId = PgpKeyUtils.formatKeyId(testMemberKey.getKeyID());
        testDefaultKeyId = PgpKeyUtils.formatKeyId(testDefaultKey.getKeyID());

        // Mock PgpKeyManager
        mockKeyManager = mock(PgpKeyManager.class);

        // Mock key lookups using the configured key IDs from the INI file
        // The INI file maps "INSTANCE/memberClass/memberCode" to "B23B8E993AC4632A896D39A27BE94D3451C16D33"
        when(mockKeyManager.getPublicKey("B23B8E993AC4632A896D39A27BE94D3451C16D33"))
                .thenReturn(Optional.of(testMemberKey));
        when(mockKeyManager.getPublicKey("B23B8E993AC4632A896D39A27BE94D3451C16D55"))
                .thenReturn(Optional.of(testDefaultKey));

        // Mock BouncyCastlePgpEncryptionService
        var mockEncryption = mock(BouncyCastlePgpEncryptionService.class);

        // Create provider
        provider = new VaultMemberEncryptionConfigProvider(mockKeyManager, mockEncryption);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(MessageLogProperties.ARCHIVE_DEFAULT_ENCRYPTION_KEY);
        System.clearProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG);
    }

    @Test
    void forDiagnosticsWhenExistsRegisteredMemberAndConfigMappingThenShouldReturnMemberWithMappedKey() {
        // Given: A registered member with a configured key mapping
        ClientId registeredMember = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode");

        // When: Get diagnostics for this member
        EncryptionConfig encryptionConfig = provider.forDiagnostics(Collections.singletonList(registeredMember));

        // Then: Should return VaultEncryptionConfig with the member and its mapped key
        assertNotNull(encryptionConfig);
        assertInstanceOf(VaultEncryptionConfig.class, encryptionConfig);

        VaultEncryptionConfig vaultConfig = (VaultEncryptionConfig) encryptionConfig;
        List<EncryptionMember> encryptionMembers = vaultConfig.encryptionMembers();

        assertEquals(1, encryptionMembers.size());
        EncryptionMember encryptionMember = encryptionMembers.getFirst();

        assertEquals("INSTANCE/memberClass/memberCode", encryptionMember.memberId());
        // The key returned should be the one we mocked for the INI-configured key ID
        assertEquals(Collections.singleton(testMemberKeyId), encryptionMember.keys());
        assertFalse(encryptionMember.defaultKeyUsed(), "Should use member-specific key, not default");
    }

    @Test
    void forDiagnosticsWhenExistsRegisteredMemberAndNotExistsConfigMappingThenShouldReturnMemberWithDefaultKey() {
        // Given: A registered member WITHOUT a configured key mapping
        ClientId registeredMember = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode2");

        // When: Get diagnostics for this member
        EncryptionConfig encryptionConfig = provider.forDiagnostics(Collections.singletonList(registeredMember));

        // Then: Should return VaultEncryptionConfig with the member using default key
        assertNotNull(encryptionConfig);
        assertInstanceOf(VaultEncryptionConfig.class, encryptionConfig);

        VaultEncryptionConfig vaultConfig = (VaultEncryptionConfig) encryptionConfig;
        List<EncryptionMember> encryptionMembers = vaultConfig.encryptionMembers();

        assertEquals(1, encryptionMembers.size());
        EncryptionMember encryptionMember = encryptionMembers.getFirst();

        assertEquals("INSTANCE/memberClass/memberCode2", encryptionMember.memberId());
        // The key returned should be the one we mocked for the default key ID
        assertEquals(Collections.singleton(testDefaultKeyId), encryptionMember.keys());
        // NOTE: defaultKeyUsed is false because keys were found (even though they're default keys)
        // The implementation doesn't distinguish between member-specific and default keys when keys are available
        assertFalse(encryptionMember.defaultKeyUsed(), "defaultKeyUsed is false when any keys are available");
    }

    @Test
    void forDiagnosticsWhenNotExistsRegisteredMembersThenShouldReturnEmptyEncryptionMembers() {
        // When: Get diagnostics for empty list of members
        EncryptionConfig encryptionConfig = provider.forDiagnostics(Collections.emptyList());

        // Then: Should return VaultEncryptionConfig with empty members list
        assertNotNull(encryptionConfig);
        assertInstanceOf(VaultEncryptionConfig.class, encryptionConfig);

        VaultEncryptionConfig vaultConfig = (VaultEncryptionConfig) encryptionConfig;
        assertEquals(0, vaultConfig.encryptionMembers().size());
    }

    @Test
    void forDiagnosticsWhenExistsRegisteredMemberAndSubsystemThenShouldReturnOnlyMemberWithMappedKey() {
        // Given: A registered member AND its subsystem
        ClientId registeredMember = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode");
        ClientId registeredSubsystem = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode", "subsystemCode");

        // When: Get diagnostics for both
        EncryptionConfig encryptionConfig = provider.forDiagnostics(
                Arrays.asList(registeredMember, registeredSubsystem));

        // Then: Should return VaultEncryptionConfig with only ONE member entry (deduplicated)
        assertNotNull(encryptionConfig);
        assertInstanceOf(VaultEncryptionConfig.class, encryptionConfig);

        VaultEncryptionConfig vaultConfig = (VaultEncryptionConfig) encryptionConfig;
        List<EncryptionMember> encryptionMembers = vaultConfig.encryptionMembers();

        // Both member and subsystem have same member ID, so should be deduplicated to 1
        assertEquals(1, encryptionMembers.size());
        EncryptionMember encryptionMember = encryptionMembers.getFirst();

        assertEquals("INSTANCE/memberClass/memberCode", encryptionMember.memberId());
        // The key returned should be the one we mocked for the INI-configured key ID
        assertEquals(Collections.singleton(testMemberKeyId), encryptionMember.keys());
        assertFalse(encryptionMember.defaultKeyUsed());
    }

    @Test
    void shouldParseMappings() {
        // When: Parse the test mapping file
        final Map<String, Set<String>> mappings = MessageLogProperties.getKeyMappings();

        // Then: Should match expected mappings
        assertEquals(expectedMappings, mappings);
    }

    @Test
    void getPublicKeysForMemberShouldReturnMemberSpecificKeys() throws Exception {
        // Given: Member with specific keys configured
        String memberId = "INSTANCE/memberClass/memberCode";

        // When: Get public keys for this member
        var keys = provider.getPublicKeysForMember(memberId);

        // Then: Should return the member-specific key
        assertEquals(1, keys.size());
        assertEquals(testMemberKey.getKeyID(), keys.getFirst().getKeyID());
    }

    @Test
    void getPublicKeysForMemberShouldReturnDefaultKeyWhenNoMemberMapping() throws Exception {
        // Given: Member WITHOUT specific keys configured
        String memberId = "INSTANCE/memberClass/memberCodeUnmapped";

        // When: Get public keys for this member
        var keys = provider.getPublicKeysForMember(memberId);

        // Then: Should return the default key
        assertEquals(1, keys.size());
        assertEquals(testDefaultKey.getKeyID(), keys.getFirst().getKeyID());
    }

    @Test
    void getPublicKeysForMemberShouldReturnAllKeysWhenMemberKeyNotFoundAndNoDefault() throws Exception {
        // Given: Member with configured key that doesn't exist in key manager, and no default
        System.clearProperty(MessageLogProperties.ARCHIVE_DEFAULT_ENCRYPTION_KEY);
        String memberId = "INSTANCE/memberClass/memberCodeMissingKey";
        System.setProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_KEYS_CONFIG, "build/resources/test/mlog-keys.ini");

        when(mockKeyManager.getPublicKey(anyString())).thenReturn(Optional.empty());
        Map<String, PGPPublicKey> allKeys = Map.of(
                testMemberKeyId, testMemberKey,
                testDefaultKeyId, testDefaultKey
        );
        when(mockKeyManager.getAllPublicKeys()).thenReturn(allKeys);

        // When: Get public keys for this member
        var keys = provider.getPublicKeysForMember(memberId);

        // Then: Should return all available keys as fallback
        assertEquals(2, keys.size());
        assertTrue(keys.contains(testMemberKey));
        assertTrue(keys.contains(testDefaultKey));
    }

    @Test
    void forGroupingShouldThrowExceptionWhenGroupingHasNoClientId() throws IOException {
        // Given: A grouping without client ID (using anonymous implementation with null clientId)
        Grouping grouping = messageRecord -> false;

        // When/Then: Should throw IllegalArgumentException
        try {
            provider.forGrouping(grouping);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Expected a grouping with a client identifier"));
        }
    }

    @Test
    void forGroupingShouldReturnConfigWithMemberSpecificKeys() throws Exception {
        // Given: A member grouping with a member that has specific keys
        ClientId clientId = ClientId.Conf.create("INSTANCE", "memberClass", "memberCode");
        Grouping grouping = new MemberGrouping(clientId);

        // When: Get encryption config for this grouping
        EncryptionConfig config = provider.forGrouping(grouping);

        // Then: Should return config with member-specific key IDs
        assertNotNull(config);
        assertInstanceOf(VaultEncryptionConfig.class, config);

        VaultEncryptionConfig vaultConfig = (VaultEncryptionConfig) config;
        assertFalse(vaultConfig.encryptionKeyIds().isEmpty());
        // The key returned should be the one we mocked for the INI-configured key ID
        assertTrue(vaultConfig.encryptionKeyIds().contains(testMemberKeyId));
    }

    private static Set<String> setOf(String... elem) {
        return elem.length == 1 ? Collections.singleton(elem[0]) : new HashSet<>(Arrays.asList(elem));
    }
}

