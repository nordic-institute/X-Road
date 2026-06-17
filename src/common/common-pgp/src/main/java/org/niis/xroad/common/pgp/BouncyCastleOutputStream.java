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

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.PGPException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * OutputStream that encrypts and signs data using BouncyCastle, writing to a file.
 *
 * <p>This implementation buffers all data in memory before encrypting to the target file.
 * For large files, consider using streaming encryption directly via BouncyCastlePgpEncryption.
 */
@Slf4j
public final class BouncyCastleOutputStream extends OutputStream {

    private static final int INITIAL_BUFFER_SIZE = 64 * 1024; // 64KB

    private final BouncyCastlePgpEncryptionService encryption;
    private final Path outputFile;
    private final Set<String> recipientKeyIds;
    private final ByteArrayOutputStream buffer;
    private boolean closed = false;

    /**
     * Creates an OutputStream that encrypts data using BouncyCastle/Vault.
     *
     * @param encryption      BouncyCastle encryption facade
     * @param outputFile      Target file for encrypted output
     * @param recipientKeyIds Set of recipient key IDs (hex format), or null for self-encryption
     */
    public BouncyCastleOutputStream(
            BouncyCastlePgpEncryptionService encryption,
            Path outputFile,
            Set<String> recipientKeyIds) {
        this.encryption = encryption;
        this.outputFile = outputFile;
        this.recipientKeyIds = recipientKeyIds;
        this.buffer = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE);

        log.debug("Created BouncyCastleOutputStream for {}, recipients: {}",
                outputFile, recipientKeyIds != null ? recipientKeyIds.size() : "self");
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        buffer.write(b);
    }

    @Override
    public void write(@Nonnull byte[] b) throws IOException {
        ensureOpen();
        buffer.write(b);
    }

    @Override
    public void write(@Nonnull byte[] b, int off, int len) throws IOException {
        ensureOpen();
        buffer.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        buffer.flush();
    }

    /**
     * Closes the stream, encrypting buffered data and writing to the output file.
     *
     * @throws IOException if encryption or file writing fails
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        try {
            // Get buffered plaintext data
            byte[] plaintextData = buffer.toByteArray();
            log.debug("Encrypting {} bytes to {}", plaintextData.length, outputFile);

            // Create temporary file for encrypted output
            Path tempFile = Files.createTempFile(
                    outputFile.getParent(),
                    ".tmp-encrypted-",
                    ".pgp"
            );

            try {
                // Encrypt to temporary file
                try (var inputStream = new ByteArrayInputStream(plaintextData);
                     var outputStream = Files.newOutputStream(tempFile)) {

                    encryption.encryptAndSign(inputStream, outputStream, recipientKeyIds);
                }

                // Move encrypted file to target location (atomic)
                Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                log.debug("Successfully encrypted and wrote to {}", outputFile);

            } catch (PGPException e) {
                // Clean up temp file on error
                deleteSafely(tempFile);

                throw new IOException("PGP encryption failed", e);
            } catch (IOException e) {
                // Clean up temp file on error
                deleteSafely(tempFile);

                throw e;
            }

        } finally {
            buffer.close();
        }
    }

    private void deleteSafely(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", path, e.getMessage());
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
    }

}

