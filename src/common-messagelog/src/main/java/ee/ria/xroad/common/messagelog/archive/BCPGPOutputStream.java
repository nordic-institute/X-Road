/**
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
package ee.ria.xroad.common.messagelog.archive;

import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

/**
 * an OutputStream that encrypts and optionally signs the data using Bouncy Castle PGP implementation.
 */
public class BCPGPOutputStream extends FilterOutputStream {

    private final OutputStream dataOutputStream;
    private final PGPEncryptedDataGenerator pgpEncryptedDataGenerator;
    private final PGPLiteralDataGenerator pgpLiteralDataGenerator;
    private final PGPSignatureGenerator pgpSignatureGenerator;
    private final OutputStream encryptingOutputStream;

    private static final int PACKET_SIZE = 8 * 1024;

    /**
     * Constructs an OutputStream that encrypts data with PGP
     *
     * @param out     Outputstream to write the encrypted data to
     * @param signKey Signing key, can be null in which case signing is skipped
     * @param keys    One or more encryption keys (recipients)
     * @throws PGPException If setting up PGP fails
     * @throws IOException  If setting up PGP fails
     */
    public BCPGPOutputStream(OutputStream out, PGPSecretKey signKey, PGPPublicKey... keys)
            throws IOException {
        super(out);

        final JcePGPDataEncryptorBuilder builder = new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_128);
        builder.setWithIntegrityPacket(true);
        pgpEncryptedDataGenerator = new PGPEncryptedDataGenerator(builder);

        for (PGPPublicKey key : keys) {
            pgpEncryptedDataGenerator.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(key));
        }

        try {
            encryptingOutputStream = pgpEncryptedDataGenerator.open(out, new byte[PACKET_SIZE]);

            if (signKey != null) {
                pgpSignatureGenerator = new PGPSignatureGenerator(
                        new JcaPGPContentSignerBuilder(signKey.getPublicKey().getAlgorithm(),
                                HashAlgorithmTags.SHA256));
                final PGPPrivateKey privateKey = signKey
                        .extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().build(null));
                final PGPSignatureSubpacketGenerator generator = new PGPSignatureSubpacketGenerator();
                generator.setIssuerFingerprint(false, signKey);
                pgpSignatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
                pgpSignatureGenerator.setHashedSubpackets(generator.generate());
                pgpSignatureGenerator.generateOnePassVersion(false).encode(encryptingOutputStream);
            } else {
                pgpSignatureGenerator = null;
            }
        } catch (PGPException e) {
            throw new IOException(e);
        }

        pgpLiteralDataGenerator = new PGPLiteralDataGenerator();
        dataOutputStream = pgpLiteralDataGenerator.open(
                encryptingOutputStream,
                PGPLiteralDataGenerator.BINARY,
                "",
                new Date(),
                new byte[PACKET_SIZE]);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Override
    public void write(int b) throws IOException {
        if (pgpSignatureGenerator != null) {
            pgpSignatureGenerator.update((byte) (b & 0xFF));
        }
        dataOutputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (pgpSignatureGenerator != null) {
            pgpSignatureGenerator.update(b, off, len);
        }
        dataOutputStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        dataOutputStream.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            try {
                pgpLiteralDataGenerator.close();
                if (pgpSignatureGenerator != null) {
                    pgpSignatureGenerator.generate().encode(encryptingOutputStream);
                }
            } catch (PGPException e) {
                throw new IOException(e);
            } finally {
                pgpEncryptedDataGenerator.close();
            }
        } finally {
            super.close();
        }
    }

}
