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

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Migrates ACME account key pairs from a PKCS12 keystore to Vault, carrying each alias's certificate
 * expiry forward as the rotation-due timestamp. The certificate itself is discarded.
 */
@Slf4j
@RequiredArgsConstructor
public final class AcmeAccountKeyMigrator {

    private final VaultClient vaultClient;
    private final AcmeAccountKeyAliasResolver aliasResolver;

    /**
     * Migrates every alias found in the given PKCS12 keystore into its own Vault entry.
     *
     * @param keystorePath     path to the PKCS12 keystore file
     * @param keystorePassword password protecting the keystore; cleared in place once the extraction pass is done
     * @return migration result listing every alias that was migrated
     * @throws IOException if the keystore cannot be read or an alias fails to migrate
     */
    public AcmeAccountKeyMigrationResult migrateFromKeystore(Path keystorePath, char[] keystorePassword) throws IOException {
        log.info("Starting ACME account key migration from keystore: {}", keystorePath);

        if (!Files.exists(keystorePath)) {
            throw new IOException("Keystore file not found: " + keystorePath);
        }

        try {
            KeyStore keyStore = CryptoUtils.loadPkcs12KeyStore(keystorePath.toFile(), keystorePassword);
            List<String> migratedAliases = migrateAliases(keyStore);

            if (migratedAliases.isEmpty()) {
                log.warn("Keystore {} contains no migratable ACME account key aliases", keystorePath);
                return AcmeAccountKeyMigrationResult.skipped("Keystore contains no migratable aliases");
            }

            log.info("Migrated {} ACME account key(s): {}", migratedAliases.size(), migratedAliases);
            return AcmeAccountKeyMigrationResult.success(migratedAliases);
        } catch (Exception e) {
            log.error("Failed to migrate ACME account keys", e);
            throw new IOException("Migration failed: " + e.getMessage(), e);
        } finally {
            Arrays.fill(keystorePassword, '\0');
        }
    }

    private List<String> migrateAliases(KeyStore keyStore) throws GeneralSecurityException {
        List<String> migratedAliases = new ArrayList<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            migrateAlias(keyStore, alias).ifPresent(migratedAliases::add);
        }
        return migratedAliases;
    }

    private Optional<String> migrateAlias(KeyStore keyStore, String enumeratedAlias) throws GeneralSecurityException {
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(enumeratedAlias);
        if (certificate == null) {
            log.warn("Skipping alias '{}': keystore entry is missing a certificate", enumeratedAlias);
            return Optional.empty();
        }

        List<String> passwordCandidates = passwordCandidatesFor(enumeratedAlias);
        for (String candidate : passwordCandidates) {
            Optional<PrivateKey> privateKey = tryDecrypt(keyStore, enumeratedAlias, candidate);
            if (privateKey.isPresent()) {
                storeInVault(candidate, privateKey.get(), certificate);
                return Optional.of(candidate);
            }
        }

        log.warn("Skipping alias '{}': none of the {} candidate password(s) tried could recover the private key "
                + "(no matching client identifier in serverconf, or a stale entry)", enumeratedAlias, passwordCandidates.size());
        return Optional.empty();
    }

    /**
     * Every original-case client identifier the resolver considers plausible for this enumerated alias,
     * plus the enumerated alias itself as a fallback - the real password whenever it was already lowercase.
     */
    private List<String> passwordCandidatesFor(String enumeratedAlias) {
        List<String> candidates = new ArrayList<>(aliasResolver.resolveOriginalCaseAliasCandidates(enumeratedAlias));
        if (!candidates.contains(enumeratedAlias)) {
            candidates.add(enumeratedAlias);
        }
        return candidates;
    }

    private Optional<PrivateKey> tryDecrypt(KeyStore keyStore, String enumeratedAlias, String passwordCandidate)
            throws GeneralSecurityException {
        try {
            return Optional.ofNullable((PrivateKey) keyStore.getKey(enumeratedAlias, passwordCandidate.toCharArray()));
        } catch (UnrecoverableKeyException _) {
            return Optional.empty();
        }
    }

    private void storeInVault(String alias, PrivateKey privateKey, X509Certificate certificate) {
        Instant expiresAt = certificate.getNotAfter().toInstant();
        vaultClient.createAcmeAccountKey(alias, new AcmeAccountKey(privateKey, certificate.getPublicKey(), expiresAt));
        log.info("Migrated ACME account key for alias '{}', rotation due {}", alias, expiresAt);
    }
}
