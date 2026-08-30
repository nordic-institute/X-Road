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
package org.niis.xroad.migration.acme;

import ee.ria.xroad.common.util.CertUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class AcmeAccountKeyMigratorTest {

    private static final char[] KEYSTORE_PASSWORD = "keystore-password".toCharArray();
    private static final int KEY_LENGTH = 2048;
    private static final long CERTIFICATE_EXPIRATION_DAYS = 400;

    @TempDir
    Path tempDir;

    private VaultClient vaultClient;
    private AcmeAccountKeyMigrator migrator;
    private final Map<String, AcmeAccountKey> storedKeys = new HashMap<>();
    private final Map<String, KeyPair> generatedKeyPairs = new HashMap<>();
    private final Map<String, Instant> certificateExpiries = new HashMap<>();

    @BeforeEach
    void setUp() {
        vaultClient = mock(VaultClient.class);
        doAnswer(invocation -> {
            String alias = invocation.getArgument(0);
            AcmeAccountKey key = invocation.getArgument(1);
            storedKeys.put(alias, key);
            return null;
        }).when(vaultClient).createAcmeAccountKey(any(), any());
        migrator = new AcmeAccountKeyMigrator(vaultClient, AcmeAccountKeyAliasResolver.identity());
    }

    @Test
    void shouldMigrateEveryAliasIndependently() throws Exception {
        Path keystorePath = buildKeystore("member1", "auth_member2", "sign_member2");
        char[] password = KEYSTORE_PASSWORD.clone();

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, password);

        assertThat(result.success()).isTrue();
        assertThat(result.migratedAliases()).containsExactlyInAnyOrder("member1", "auth_member2", "sign_member2");
        assertThat(result.keyCount()).isEqualTo(3);
        assertThat(storedKeys).containsOnlyKeys("member1", "auth_member2", "sign_member2");

        for (String alias : storedKeys.keySet()) {
            AcmeAccountKey stored = storedKeys.get(alias);
            KeyPair originalKeyPair = generatedKeyPairs.get(alias);

            assertThat(stored.privateKey()).isEqualTo(originalKeyPair.getPrivate());
            assertThat(stored.publicKey()).isEqualTo(originalKeyPair.getPublic());
            assertThat(stored.expiresAt()).isEqualTo(certificateExpiries.get(alias));
        }
    }

    @Test
    void shouldOnlyStoreTheKeyPairAndExpiryNotTheCertificate() throws Exception {
        Path keystorePath = buildKeystore("member1");

        migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        verify(vaultClient, times(1)).createAcmeAccountKey(eq("member1"), any(AcmeAccountKey.class));
        verifyNoMoreInteractions(vaultClient);
    }

    @Test
    void shouldClearPasswordAfterMigration() throws Exception {
        Path keystorePath = buildKeystore("member1");
        char[] password = KEYSTORE_PASSWORD.clone();

        migrator.migrateFromKeystore(keystorePath, password);

        assertThat(password).containsOnly('\0');
    }

    @Test
    void shouldClearPasswordEvenOnFailure() throws Exception {
        Path keystorePath = buildKeystore("member1");
        char[] wrongPassword = "wrong-password".toCharArray();

        assertThatThrownBy(() -> migrator.migrateFromKeystore(keystorePath, wrongPassword))
                .isInstanceOf(IOException.class);

        assertThat(wrongPassword).containsOnly('\0');
    }

    @Test
    void shouldThrowWhenKeystoreFileMissing() {
        Path missing = tempDir.resolve("missing.p12");
        char[] password = KEYSTORE_PASSWORD.clone();

        assertThatThrownBy(() -> migrator.migrateFromKeystore(missing, password))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void shouldMigrateMixedCaseAliasUnderItsOriginalCase() throws Exception {
        String originalCaseAlias = "sign_DEV:COM:1234";
        Path keystorePath = buildKeystore(originalCaseAlias);

        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234"));
        migrator = new AcmeAccountKeyMigrator(vaultClient, resolver);

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        assertThat(result.success()).isTrue();
        assertThat(result.migratedAliases()).containsExactly(originalCaseAlias);
        assertThat(storedKeys).containsOnlyKeys(originalCaseAlias);
        assertThat(storedKeys.get(originalCaseAlias).privateKey())
                .isEqualTo(generatedKeyPairs.get(originalCaseAlias).getPrivate());
    }

    @Test
    void shouldMigrateAliasWithMixedCaseWithinASingleSegment() throws Exception {
        String originalCaseAlias = "sign_DeV:com:1234";
        Path keystorePath = buildKeystore(originalCaseAlias);

        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DeV:com:1234"));
        migrator = new AcmeAccountKeyMigrator(vaultClient, resolver);

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        assertThat(result.success()).isTrue();
        assertThat(result.migratedAliases()).containsExactly(originalCaseAlias);
        assertThat(storedKeys).containsOnlyKeys(originalCaseAlias);
    }

    @Test
    void shouldDisambiguateACaseCollisionByTestingEachCandidateAsTheDecryptPassword() throws Exception {
        String realAlias = "sign_DEV:COM:1234";
        Path keystorePath = buildKeystore(realAlias);

        // "dev:COM:1234" only differs by case from the real client, and neither client's
        // identifier alone can be preferred - only the real one will actually decrypt the entry.
        AcmeAccountKeyAliasResolver resolver =
                AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234", "dev:COM:1234"));
        migrator = new AcmeAccountKeyMigrator(vaultClient, resolver);

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        assertThat(result.success()).isTrue();
        assertThat(result.migratedAliases()).containsExactly(realAlias);
        assertThat(storedKeys).containsOnlyKeys(realAlias);
    }

    @Test
    void shouldSkipUnresolvableAliasWithoutAbortingTheRestOfTheBatch() throws Exception {
        String resolvableAlias = "sign_DEV:COM:1234";
        String unresolvableAlias = "sign_XYZ:ORG:9999";
        Path keystorePath = buildKeystore(resolvableAlias, unresolvableAlias);

        AcmeAccountKeyAliasResolver resolver = AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("DEV:COM:1234"));
        migrator = new AcmeAccountKeyMigrator(vaultClient, resolver);

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        assertThat(result.success()).isTrue();
        assertThat(result.migratedAliases()).containsExactly(resolvableAlias);
        assertThat(storedKeys).containsOnlyKeys(resolvableAlias);
    }

    @Test
    void shouldSkipWhenNoCaseCollisionCandidateDecryptsTheEntry() throws Exception {
        String staleAlias = "sign_DEV:COM:1234";
        Path keystorePath = buildKeystore(staleAlias);

        // Neither known candidate matches the entry's real (stale/removed) original-case password.
        AcmeAccountKeyAliasResolver resolver =
                AcmeAccountKeyAliasResolver.fromKnownClientIds(List.of("Dev:COM:1234", "dEV:COM:1234"));
        migrator = new AcmeAccountKeyMigrator(vaultClient, resolver);

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        assertThat(result.success()).isFalse();
        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(vaultClient);
    }

    @Test
    void shouldReportNoMigratableAliasesWhenTheOnlyAliasIsUnresolvable() throws Exception {
        String originalCaseAlias = "sign_DEV:COM:1234";
        Path keystorePath = buildKeystore(originalCaseAlias);

        migrator = new AcmeAccountKeyMigrator(vaultClient, AcmeAccountKeyAliasResolver.identity());

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        assertThat(result.success()).isFalse();
        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(vaultClient);
    }

    @Test
    void shouldSkipWhenKeystoreHasNoAliases() throws Exception {
        Path keystorePath = tempDir.resolve("empty.p12");
        KeyStore emptyKeyStore = KeyStore.getInstance("PKCS12");
        emptyKeyStore.load(null, KEYSTORE_PASSWORD);
        try (OutputStream out = Files.newOutputStream(keystorePath)) {
            emptyKeyStore.store(out, KEYSTORE_PASSWORD);
        }

        AcmeAccountKeyMigrationResult result = migrator.migrateFromKeystore(keystorePath, KEYSTORE_PASSWORD.clone());

        assertThat(result.success()).isFalse();
        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(vaultClient);
    }

    private Path buildKeystore(String... aliases) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, KEYSTORE_PASSWORD);

        for (String alias : aliases) {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(KEY_LENGTH, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            generatedKeyPairs.put(alias, keyPair);

            X509Certificate[] certificateChain = CertUtils.createSelfSignedCertificate(alias, keyPair, CERTIFICATE_EXPIRATION_DAYS);
            certificateExpiries.put(alias, certificateChain[0].getNotAfter().toInstant().truncatedTo(ChronoUnit.SECONDS));

            keyStore.setKeyEntry(alias, keyPair.getPrivate(), alias.toCharArray(), certificateChain);
        }

        Path keystorePath = tempDir.resolve("acme-" + java.util.UUID.randomUUID() + ".p12");
        try (OutputStream out = Files.newOutputStream(keystorePath)) {
            keyStore.store(out, KEYSTORE_PASSWORD);
        }
        return keystorePath;
    }
}
