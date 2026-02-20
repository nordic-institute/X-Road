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

import ee.ria.xroad.common.crypto.RsaKeyManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.time.Instant;
import java.util.Date;

import static org.bouncycastle.bcpg.PublicKeyPacket.VERSION_4;
import static org.niis.xroad.common.core.exception.ErrorCode.PGP_ENCODE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.PGP_INTERNAL_ERROR;

/**
 * Generates fresh PGP keys for tests.
 */
@Slf4j
@RequiredArgsConstructor
public final class PgpKeyGenerator {
    private static final int KEY_SIZE = 4096; // RSA key size
    private static final int VALIDITY_YEARS = 10; // Key validity period

    private final RsaKeyManager rsaKeyManager = new RsaKeyManager(KEY_SIZE);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Generates a new PGP key pair.
     * Keys are automatically generated with secure defaults.
     * <p>
     * This method:
     * 1. Generates a 4096-bit RSA key pair
     * 2. Creates self-signed PGP keys (no passphrase)
     * 3. Exports to armored format
     *
     */
    public GeneratedKeyInfo generate(String identity) {
        log.info("Generating fresh PGP keys for identity: {}", identity);

        try {
            var keyPair = rsaKeyManager.generateKeyPair();
            var secretKeyRing = createSecretKeyRing(keyPair, identity);

            String secretKeyArmored = exportSecretKey(secretKeyRing);
            String publicKeyArmored = exportPublicKey(secretKeyRing);

            long keyId = secretKeyRing.getSecretKey().getKeyID();
            // Format key ID as 16-character hex string (padded with leading zeros)
            String keyIdHex = PgpKeyUtils.formatKeyId(keyId);

            log.info("Successfully generated and stored PGP keys (ID: {})", keyIdHex);

            return new GeneratedKeyInfo(
                    keyIdHex,
                    identity,
                    Instant.now(),
                    VALIDITY_YEARS,
                    secretKeyArmored,
                    publicKeyArmored
            );

        } catch (PGPException e) {
            throw XrdRuntimeException.systemException(PGP_INTERNAL_ERROR)
                    .cause(e
                    ).build();
        }
    }


    private PGPSecretKeyRing createSecretKeyRing(KeyPair keyPair, String identity)
            throws PGPException {

        // Create PGP key pair from Java KeyPair (use JcaPGPKeyPair for Java security classes)
        var pgpKeyPair = new JcaPGPKeyPair(
                VERSION_4,
                PublicKeyAlgorithmTags.RSA_GENERAL,
                keyPair,
                new Date()
        );

        // Setup digest calculator for key encryption (SHA-1, required by spec)
        PGPDigestCalculator sha1Calc = new BcPGPDigestCalculatorProvider()
                .get(HashAlgorithmTags.SHA1);

        // Create signature subpackets with explicit key flags to ensure encryption capability
        var signatureSubpacketGenerator = new PGPSignatureSubpacketGenerator();

        // Explicitly set key flags: certify, sign, encrypt communications, encrypt storage
        // This ensures the key can be used for both signing AND encryption
        signatureSubpacketGenerator.setKeyFlags(false,
                KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA
                        | KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);

        var hashedPackets = signatureSubpacketGenerator.generate();

        // Key ring generator
        var keyRingGen = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                pgpKeyPair,
                identity,
                sha1Calc,
                hashedPackets,
                null,
                new BcPGPContentSignerBuilder(
                        pgpKeyPair.getPublicKey().getAlgorithm(),
                        HashAlgorithmTags.SHA256
                ),
                new BcPBESecretKeyEncryptorBuilder(
                        SymmetricKeyAlgorithmTags.CAST5, // Symmetric encryption algorithm
                        sha1Calc
                ).build(new char[0])
        );

        return keyRingGen.generateSecretKeyRing();
    }

    private String exportSecretKey(PGPSecretKeyRing secretKeyRing) {
        var out = new ByteArrayOutputStream();
        try (var armoredOut = new ArmoredOutputStream(out)) {
            secretKeyRing.encode(armoredOut);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(PGP_ENCODE_FAILED)
                    .cause(e)
                    .build();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String exportPublicKey(PGPSecretKeyRing secretKeyRing) {
        var out = new ByteArrayOutputStream();
        try (var armoredOut = new ArmoredOutputStream(out)) {
            // Export the public key ring (certificate) from the secret key ring
            // This creates a proper PGPPublicKeyRing that can be read by PGPPublicKeyRingCollection
            // The toCertificate() method extracts all public keys and creates a proper key ring
            secretKeyRing.toCertificate().encode(armoredOut);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(PGP_ENCODE_FAILED)
                    .cause(e)
                    .build();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public record GeneratedKeyInfo(
            String keyId,
            String identity,
            Instant createdAt,
            int validityYears,
            String secretData,
            String publicData
    ) {
    }
}

