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
package org.niix.xroad.common.pgp.test;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.niis.xroad.common.pgp.model.PgpKeyPair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles streaming OpenPGP decryption and signature verification.
 * Uses constant memory regardless of input size.
 */
@Slf4j
public final class StreamingPgpDecryptor {

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB

    /**
     * Decrypts and verifies a stream.
     *
     * @param input          Encrypted input stream
     * @param output         Decrypted output stream
     * @param signingKeyPair Key pair for decryption and verification
     */
    public void decryptAndVerify(
            InputStream input,
            OutputStream output,
            PgpKeyPair signingKeyPair
    ) throws IOException, PGPException {

        log.debug("Starting decryption with signing key ID: {}", signingKeyPair.keyIdHex());

        var decoderStream = PGPUtil.getDecoderStream(input);
        var pgpFactory = new PGPObjectFactory(decoderStream, new BcKeyFingerprintCalculator());

        Object obj = pgpFactory.nextObject();
        if (log.isTraceEnabled()) {
            log.trace("First object type: {}", obj != null ? obj.getClass().getSimpleName() : "null");
        }

        var encDataList = extractEncryptedDataList(obj, pgpFactory);
        log.trace("Encrypted data list size: {}", encDataList.size());

        var encData = findEncryptedDataForKey(encDataList, signingKeyPair.keyId());
        log.trace("Found encrypted data for key ID: {}", signingKeyPair.keyIdHex());

        try (var clearStream = encData.getDataStream(
                new BcPublicKeyDataDecryptorFactory(signingKeyPair.privateKey()))) {

            var plainFactory = new PGPObjectFactory(clearStream, new BcKeyFingerprintCalculator());
            var result = unwrapCompression(plainFactory);
            var ops = extractAndInitSignature(result.obj(), signingKeyPair);

            var literalData = extractLiteralData(result, ops);
            streamAndVerify(literalData.getInputStream(), output, ops, result.factory());

            if (!encData.verify()) {
                throw new PGPException("Integrity check failed");
            }
        }
    }

    private PGPLiteralData extractLiteralData(UnwrapResult result, PGPOnePassSignature ops)
            throws IOException, PGPException {
        Object plainObj = result.obj();
        if (ops != null) {
            plainObj = result.factory().nextObject();
        }

        if (plainObj instanceof PGPLiteralData literalData) {
            return literalData;
        }

        throw new PGPException("Unexpected data type in encrypted message: "
                + (plainObj != null ? plainObj.getClass().getName() : "null"));
    }

    private PGPEncryptedDataList extractEncryptedDataList(Object obj, PGPObjectFactory factory)
            throws IOException, PGPException {
        if (obj == null) {
            throw new PGPException("Empty or invalid PGP stream");
        }

        // Handle marker packet
        return switch (obj) {
            case PGPEncryptedDataList list -> list;
            case org.bouncycastle.openpgp.PGPMarker ignored -> (PGPEncryptedDataList) factory.nextObject();
            default -> throw new PGPException("Unexpected object type: " + obj.getClass().getName());
        };
    }

    private PGPPublicKeyEncryptedData findEncryptedDataForKey(
            PGPEncryptedDataList encDataList,
            long keyId
    ) throws PGPException {
        for (var encDataObj : encDataList) {
            if (encDataObj instanceof PGPPublicKeyEncryptedData encData
                    && encData.getKeyIdentifier().getKeyId() == keyId) {
                return encData;
            }
        }
        throw new PGPException("Data is not encrypted for our key (ID: " + Long.toHexString(keyId) + ")");
    }

    private record UnwrapResult(Object obj, PGPObjectFactory factory, InputStream stream) {
    }

    private UnwrapResult unwrapCompression(PGPObjectFactory factory) throws IOException, PGPException {
        Object obj = factory.nextObject();

        if (obj instanceof PGPCompressedData compressedData) {
            log.trace("Unwrapping compressed data, algorithm: {}", compressedData.getAlgorithm());
            var compressedStream = compressedData.getDataStream();
            var newFactory = new PGPObjectFactory(compressedStream, new BcKeyFingerprintCalculator());
            Object uncompressed = newFactory.nextObject();
            if (log.isTraceEnabled()) {
                log.trace("Uncompressed object type: {}", uncompressed != null ? uncompressed.getClass().getSimpleName() : "null");
            }
            // Keep reference to the stream to prevent premature closure
            return new UnwrapResult(uncompressed, newFactory, compressedStream);
        }

        if (log.isTraceEnabled()) {
            log.trace("No compression found, object type: {}", obj != null ? obj.getClass().getSimpleName() : "null");
        }
        return new UnwrapResult(obj, factory, null);
    }

    private PGPOnePassSignature extractAndInitSignature(
            Object obj,
            PgpKeyPair keyPair
    ) throws PGPException {
        if (obj instanceof PGPOnePassSignatureList opsList && !opsList.isEmpty()) {
            var ops = opsList.get(0);
            ops.init(
                    new JcaPGPContentVerifierBuilderProvider()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME),
                    keyPair.publicKey()
            );
            return ops;
        }
        return null;
    }

    private void streamAndVerify(
            InputStream input,
            OutputStream output,
            PGPOnePassSignature ops,
            PGPObjectFactory factory
    ) throws IOException, PGPException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            if (ops != null) {
                ops.update(buffer, 0, bytesRead);
            }
            totalBytesRead += bytesRead;
        }

        output.flush();
        log.debug("Decrypted {} bytes", totalBytesRead);

        // Verify signature if present
        if (ops != null) {
            log.trace("Verifying signature");
            var signatureList = (PGPSignatureList) factory.nextObject();
            if (!ops.verify(signatureList.get(0))) {
                throw new PGPException("Signature verification failed");
            }
            log.debug("Signature verified successfully");
        }
    }
}

