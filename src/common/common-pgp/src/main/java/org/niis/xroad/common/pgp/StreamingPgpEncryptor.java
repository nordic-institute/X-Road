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


import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.niis.xroad.common.pgp.model.PgpKeyPair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

/**
 * Handles streaming OpenPGP encryption and signing.
 * Uses constant memory regardless of input size.
 */
@Slf4j
public final class StreamingPgpEncryptor {

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB
    private static final int CIPHER_BUFFER_SIZE = 4 * 1024; // 4KB

    /**
     * Encrypts and signs a stream.
     *
     * @param input          Input stream
     * @param output         Output stream
     * @param recipients     List of recipient public keys
     * @param signingKeyPair Key pair for signing
     * @param signerUserId   User ID of the signer (e.g., Security Server ID)
     */
    public void encryptAndSign(
            InputStream input,
            OutputStream output,
            List<PGPPublicKey> recipients,
            PgpKeyPair signingKeyPair,
            String signerUserId
    ) throws IOException, PGPException {

        var encryptGen = createEncryptionGenerator(recipients);

        try (var encryptedOut = encryptGen.open(output, new byte[CIPHER_BUFFER_SIZE]);
             var compressedOut = openCompressedStream(encryptedOut)) {

            var signatureGen = createSignatureGenerator(signingKeyPair, signerUserId);
            signatureGen.generateOnePassVersion(false).encode(compressedOut);

            try (var literalOut = openLiteralStream(compressedOut)) {
                streamAndSign(input, literalOut, signatureGen);
            }

            // Write signature AFTER literal stream is closed
            signatureGen.generate().encode(compressedOut);
        }
    }

    private PGPEncryptedDataGenerator createEncryptionGenerator(List<PGPPublicKey> recipients) {
        var encryptGen = new PGPEncryptedDataGenerator(
                new BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
        );

        recipients.forEach(recipient ->
                encryptGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(recipient))
        );

        return encryptGen;
    }

    private OutputStream openCompressedStream(OutputStream out) throws IOException, PGPException {
        // Use UNCOMPRESSED to match old GPG implementation: --compress-algo none
        var compressGen = new PGPCompressedDataGenerator(CompressionAlgorithmTags.UNCOMPRESSED);

        return compressGen.open(out, new byte[CIPHER_BUFFER_SIZE]);
    }

    private PGPSignatureGenerator createSignatureGenerator(PgpKeyPair keyPair, String signerUserId) throws PGPException {
        // Create content signer builder
        var contentSignerBuilder = new JcaPGPContentSignerBuilder(
                keyPair.privateKey().getPublicKeyPacket().getAlgorithm(),
                HashAlgorithmTags.SHA256
        ).setProvider(BouncyCastleProvider.PROVIDER_NAME);

        // Create signature generator (constructor takes builder and public key)
        var signatureGen = new PGPSignatureGenerator(contentSignerBuilder, keyPair.publicKey());

        signatureGen.init(PGPSignature.BINARY_DOCUMENT, keyPair.privateKey());

        var subpacketGen = new PGPSignatureSubpacketGenerator();
        subpacketGen.addSignerUserID(false, signerUserId);
        signatureGen.setHashedSubpackets(subpacketGen.generate());

        return signatureGen;
    }

    private OutputStream openLiteralStream(OutputStream out) throws IOException {
        var literalGen = new PGPLiteralDataGenerator();
        return literalGen.open(
                out,
                PGPLiteralData.BINARY,
                PGPLiteralData.CONSOLE,
                new Date(),
                new byte[CIPHER_BUFFER_SIZE]
        );
    }

    private void streamAndSign(
            InputStream input,
            OutputStream output,
            PGPSignatureGenerator signatureGen
    ) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesWritten = 0;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            signatureGen.update(buffer, 0, bytesRead);
            totalBytesWritten += bytesRead;
        }

        output.flush();
        log.debug("Finished writing literal data, total bytes: {}", totalBytesWritten);
    }
}

