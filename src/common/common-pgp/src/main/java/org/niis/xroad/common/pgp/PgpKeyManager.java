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


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.pgp.model.PgpKeyPair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Manages PGP key loading and caching from Vault.
 * Responsible for key lifecycle and lazy loading.
 */
@Slf4j
@RequiredArgsConstructor
public final class PgpKeyManager {

    private final PgpKeyProvider pgpKeyProvider;

    @SuppressWarnings("java:S3077")
    private volatile LoadedKeys keys;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Immutable cache of loaded keys.
     * Using a record ensures thread-safe publication of both keys atomically.
     */
    private record LoadedKeys(
            PgpKeyPair signingKeyPair,
            Map<String, PGPPublicKey> publicKeys
    ) {
        LoadedKeys {
            publicKeys = Map.copyOf(publicKeys);
        }
    }

    /**
     * Gets the loaded keys, initializing them if necessary.
     * This method handles the lazy loading logic.
     */
    private LoadedKeys getLoadedKeys() {
        if (keys == null) {
            initialize();
        }
        return keys;
    }

    private void initialize() {
        synchronized (this) {
            if (keys == null) {
                log.info("Loading PGP keys from Vault");
                try {
                    // Load both keys atomically
                    String secretKeyArmored = pgpKeyProvider.getSigningSecretKey();
                    Optional<String> publicKeysArmoredOpt = pgpKeyProvider.getEncryptionPublicKeys();

                    PgpKeyPair signing = loadSecretKey(secretKeyArmored);

                    // Load public keys if provided, otherwise start with empty map
                    Map<String, PGPPublicKey> publicKeys = publicKeysArmoredOpt
                            .map(armored -> {
                                try {
                                    return loadPublicKeys(armored);
                                } catch (IOException | PGPException e) {
                                    log.error("Failed to load public keys", e);
                                    throw XrdRuntimeException.systemException(e);
                                }
                            })
                            .orElseGet(() -> {
                                log.debug("No encryption public keys provided, will use only signing key for encryption");
                                return new HashMap<>();
                            });

                    // Always include the signing public key in the encryption recipients
                    // This ensures the server can decrypt its own archives
                    publicKeys.put(PgpKeyUtils.formatKeyId(signing.publicKey().getKeyID()), signing.publicKey());

                    keys = new LoadedKeys(signing, publicKeys);
                    log.info("Successfully loaded PGP keys from Vault");
                } catch (IOException | PGPException e) {
                    log.error("Failed to load keys from Vault", e);
                    throw XrdRuntimeException.systemException(e);
                }
            }
        }
    }

    /**
     * Gets the signing key pair, loading from Vault if necessary.
     */
    public PgpKeyPair getSigningKeyPair() {
        return getLoadedKeys().signingKeyPair();
    }

    /**
     * Gets a public key by ID.
     */
    public Optional<PGPPublicKey> getPublicKey(String keyId) {
        return Optional.ofNullable(getLoadedKeys().publicKeys().get(keyId.toUpperCase()));
    }

    /**
     * Gets all public keys.
     */
    public Map<String, PGPPublicKey> getAllPublicKeys() {
        return getLoadedKeys().publicKeys();
    }


    private PgpKeyPair loadSecretKey(String armored) throws IOException, PGPException {
        log.debug("Loading secret key from armored string (length: {})", armored.length());

        try (var is = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {

            var keyRingCollection = new PGPSecretKeyRingCollection(is, new BcKeyFingerprintCalculator());
            log.debug("Secret key ring collection size: {}", keyRingCollection.size());

            return StreamSupport.stream(keyRingCollection.spliterator(), false)
                    .findFirst()
                    .map(keyRing -> {
                        try {
                            PgpKeyPair keyPair = extractSigningKeyPair(keyRing);
                            log.debug("Extracted signing key pair with ID: {}", keyPair.keyIdHex());
                            return keyPair;
                        } catch (PGPException e) {
                            throw XrdRuntimeException.systemException(e);
                        }
                    })
                    .orElseThrow(() -> new PGPException("No secret key ring found"));
        }
    }

    private PgpKeyPair extractSigningKeyPair(PGPSecretKeyRing keyRing) throws PGPException {
        return StreamSupport.stream(keyRing.spliterator(), false)
                .filter(PGPSecretKey::isSigningKey)
                .findFirst()
                .map(secretKey -> {
                    try {
                        PGPPrivateKey privateKey = secretKey.extractPrivateKey(
                                new JcePBESecretKeyDecryptorBuilder()
                                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                        .build(new char[0])
                        );
                        PGPPublicKey publicKey = secretKey.getPublicKey();

                        // Extract the first (primary) user ID from the key
                        String userId = extractPrimaryUserId(publicKey);
                        log.debug("Extracted user ID '{}' from signing key {}",
                                userId, PgpKeyUtils.formatKeyId(publicKey.getKeyID()));

                        return new PgpKeyPair(privateKey, publicKey, userId);
                    } catch (PGPException e) {
                        throw XrdRuntimeException.systemInternalError("Failed to extract private key", e);
                    }
                })
                .orElseThrow(() -> new PGPException("No signing key found in secret key ring"));
    }

    /**
     * Extracts the primary user ID from a PGP public key.
     * Returns the first user ID found, or a default if none exist.
     */
    private String extractPrimaryUserId(PGPPublicKey publicKey) {
        var userIds = publicKey.getUserIDs();
        if (userIds.hasNext()) {
            return userIds.next();
        }
        // Fallback if no user ID is set (shouldn't happen in practice)
        log.warn("No user ID found in PGP key {}, using default", PgpKeyUtils.formatKeyId(publicKey.getKeyID()));
        return "X-Road Message Log";
    }

    private Map<String, PGPPublicKey> loadPublicKeys(String armored) throws IOException, PGPException {
        log.debug("Loading public keys from armored string (length: {})", armored.length());

        try (var is = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {

            var keyRingCollection = new PGPPublicKeyRingCollection(is, new BcKeyFingerprintCalculator());
            log.debug("Public key ring collection size: {}", keyRingCollection.size());

            Map<String, PGPPublicKey> result = StreamSupport.stream(keyRingCollection.spliterator(), false)
                    .flatMap(keyRing -> {
                        log.debug("Processing public key ring");
                        return StreamSupport.stream(keyRing.spliterator(), false);
                    })
                    .filter(pubKey -> {
                        boolean isEncryption = pubKey.isEncryptionKey();
                        log.debug("Public key {} isEncryptionKey: {}",
                                Long.toHexString(pubKey.getKeyID()).toUpperCase(), isEncryption);
                        return isEncryption;
                    })
                    .collect(HashMap::new,
                            (map, pubKey) -> {
                                String keyId = PgpKeyUtils.formatKeyId(pubKey.getKeyID());
                                map.put(keyId, pubKey);
                                log.debug("Loaded public encryption key: {}", keyId);
                            },
                            HashMap::putAll
                    );

            log.debug("Total public encryption keys loaded: {}", result.size());
            return result;
        }
    }

}

