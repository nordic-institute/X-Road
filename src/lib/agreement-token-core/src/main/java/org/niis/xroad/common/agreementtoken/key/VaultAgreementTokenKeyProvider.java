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
package org.niis.xroad.common.agreementtoken.key;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provisions and caches the agreement-token signing key in OpenBao, following the same only-if-absent
 * provisioning shape as {@code AcmeClient}'s account key: read what is there, and generate one only if
 * nothing exists yet. Key ids are the base-10 string of a monotonically increasing version ("1", "2", ...),
 * so the active key is simply the highest id currently in the cache — no separate "current key" pointer
 * record is needed, and nothing is ever deleted from the store.
 * <p>
 * {@link #activeKey()} and {@link #keyById(String)} read a single, atomically swapped snapshot populated by
 * {@link #refresh()}; they never call {@link VaultClient} themselves, so verifying a token is always
 * network-free.
 */
@Slf4j
public final class VaultAgreementTokenKeyProvider implements AgreementTokenKeyProvider {

    private static final int SECRET_KEY_BYTES = 32;

    private final VaultClient vaultClient;
    private final SecureRandom secureRandom;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public VaultAgreementTokenKeyProvider(VaultClient vaultClient) {
        this(vaultClient, new SecureRandom());
    }

    VaultAgreementTokenKeyProvider(VaultClient vaultClient, SecureRandom secureRandom) {
        this.vaultClient = vaultClient;
        this.secureRandom = secureRandom;
        refresh();
        if (snapshot.get().keysById().isEmpty()) {
            bootstrap();
        }
    }

    @Override
    public AgreementTokenSigningKey activeKey() {
        var current = snapshot.get();
        var activeKeyId = current.activeKeyId();
        if (activeKeyId == null) {
            throw XrdRuntimeException.systemException(ErrorCode.AGREEMENT_TOKEN_KEY_NOT_AVAILABLE)
                    .details("No agreement-token signing key is available")
                    .build();
        }
        return current.keysById().get(activeKeyId);
    }

    @Override
    public Optional<AgreementTokenSigningKey> keyById(String keyId) {
        return Optional.ofNullable(snapshot.get().keysById().get(keyId));
    }

    /**
     * Re-lists Vault immediately before writing rather than trusting the (possibly stale) cached snapshot, so
     * the id it advances to reflects whatever the last successful {@link #refresh()} or another replica's
     * write may have missed. This narrows the cross-replica id-collision window to the time between this
     * listing and the write below — it does not remove it: OpenBao's KV v1 mount used here has no
     * compare-and-swap, so two callers racing inside that window can still both compute the same next id and
     * one write overwrites the other. Rotation is operator-driven and rare, so that residual window is
     * accepted rather than solved with a coordination mechanism this store cannot provide.
     */
    @Override
    public synchronized AgreementTokenSigningKey rotate() {
        var freshKeyIds = listKeyIdsOrFail();
        var newKey = generateAndStore(nextKeyId(freshKeyIds));
        refresh();
        return newKey;
    }

    @Override
    public void refresh() {
        var stored = loadFromVaultOrFail();

        var keysById = new HashMap<String, AgreementTokenSigningKey>();
        String activeKeyId = null;
        long maxVersion = -1;
        for (var entry : stored.entrySet()) {
            var keyId = entry.getKey();
            keysById.put(keyId, new AgreementTokenSigningKey(keyId, decode(entry.getValue())));
            var version = parseVersion(keyId);
            if (version > maxVersion) {
                maxVersion = version;
                activeKeyId = keyId;
            }
        }
        snapshot.set(new Snapshot(Map.copyOf(keysById), activeKeyId));
    }

    private synchronized void bootstrap() {
        if (!snapshot.get().keysById().isEmpty()) {
            return;
        }
        var freshKeyIds = listKeyIdsOrFail();
        if (!freshKeyIds.isEmpty()) {
            // Another replica bootstrapped a key between our constructor's first refresh() and now;
            // adopt it instead of writing a second one.
            refresh();
            return;
        }
        generateAndStore(nextKeyId(freshKeyIds));
        refresh();
    }

    private AgreementTokenSigningKey generateAndStore(String keyId) {
        var secretBytes = new byte[SECRET_KEY_BYTES];
        secureRandom.nextBytes(secretBytes);
        try {
            vaultClient.createAgreementTokenSigningKey(keyId, Base64.getEncoder().encodeToString(secretBytes));
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(ErrorCode.AGREEMENT_TOKEN_KEY_STORE_FAILED)
                    .cause(e)
                    .details("Failed to store new agreement-token signing key in OpenBao")
                    .build();
        }
        log.info("Generated agreement-token signing key '{}'", keyId);
        return new AgreementTokenSigningKey(keyId, secretBytes);
    }

    private Set<String> listKeyIdsOrFail() {
        return loadFromVaultOrFail().keySet();
    }

    private Map<String, String> loadFromVaultOrFail() {
        try {
            return vaultClient.getAgreementTokenSigningKeys();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(ErrorCode.AGREEMENT_TOKEN_KEY_STORE_FAILED)
                    .cause(e)
                    .details("Failed to load agreement-token signing keys from OpenBao")
                    .build();
        }
    }

    private String nextKeyId(Set<String> existingKeyIds) {
        var maxExisting = existingKeyIds.stream()
                .mapToLong(this::parseVersion)
                .max()
                .orElse(0);
        return String.valueOf(maxExisting + 1);
    }

    private long parseVersion(String keyId) {
        try {
            return Long.parseLong(keyId);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static byte[] decode(String base64Secret) {
        return Base64.getDecoder().decode(base64Secret);
    }

    private record Snapshot(Map<String, AgreementTokenSigningKey> keysById, String activeKeyId) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), null);
    }
}
