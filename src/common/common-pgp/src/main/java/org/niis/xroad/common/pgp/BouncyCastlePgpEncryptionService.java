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
import org.bouncycastle.openpgp.PGPException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * Main facade for BouncyCastle-based OpenPGP encryption.
 */
@Slf4j
@RequiredArgsConstructor
public class BouncyCastlePgpEncryptionService {
    private final PgpKeyManager keyManager;
    private final PgpKeyResolver keyResolver;
    private final StreamingPgpEncryptor encryptor;

    /**
     * Encrypts and signs a stream.
     * <p>
     * If recipientKeyIds is null or empty, uses self-encryption (signing key as recipient).
     * Streams data with constant memory (64KB buffer).
     *
     * @param input           Input stream (plaintext)
     * @param output          Output stream (encrypted)
     * @param recipientKeyIds Set of recipient key IDs (hex), or null for self-encryption
     * @throws IOException  If I/O error occurs
     * @throws PGPException If encryption fails
     */
    public void encryptAndSign(InputStream input, OutputStream output, Set<String> recipientKeyIds)
            throws IOException, PGPException {

        long startTime = System.currentTimeMillis();
        log.debug("Starting encryption for {} recipient(s)", recipientKeyIds != null ? recipientKeyIds.size() : 1);

        var recipients = keyResolver.resolveRecipients(recipientKeyIds);
        var signingKeyPair = keyManager.getSigningKeyPair();

        encryptor.encryptAndSign(input, output, recipients, signingKeyPair);

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Encryption completed in {}ms", duration);
    }


}

