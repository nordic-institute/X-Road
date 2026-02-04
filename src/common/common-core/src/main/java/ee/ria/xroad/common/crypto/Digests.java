/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.crypto;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.HexCalculationException;

import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.util.EncoderUtils.encodeHex;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Digests {
    /**
     * Hash algorithm digest lengths.
     */
    public static final int SHA1_DIGEST_LENGTH = 20;
    public static final int SHA224_DIGEST_LENGTH = 28;
    public static final int SHA256_DIGEST_LENGTH = 32;
    public static final int SHA384_DIGEST_LENGTH = 48;
    public static final int SHA512_DIGEST_LENGTH = 64;

    /**
     * Digest provider instance.
     */
    public static final DigestCalculatorProvider DIGEST_PROVIDER = new BcDigestCalculatorProvider();

    /**
     * Default digest algorithm id used for calculating configuration anchor hashes.
     */
    public static final DigestAlgorithm DEFAULT_ANCHOR_HASH_ALGORITHM_ID = DigestAlgorithm.SHA224;
    public static final DigestAlgorithm DEFAULT_UPLOAD_FILE_HASH_ALGORITHM = DigestAlgorithm.SHA224;

    /**
     * Global default digest method identifier and URL.
     */
    public static final DigestAlgorithm DEFAULT_DIGEST_ALGORITHM = DigestAlgorithm.SHA512;

    /**
     * A cache of BouncyCastle algorithm identifiers
     */
    private static final Map<DigestAlgorithm, AlgorithmIdentifier> ALGORITHM_IDENTIFIER_CACHE = new HashMap<>();

    /**
     * Calculates message digest using the provided digest calculator.
     *
     * @param dc   the digest calculator
     * @param data the data
     * @return message digest
     * @throws IOException if the digest cannot be calculated
     */
    public static byte[] calculateDigest(DigestCalculator dc, byte[] data) throws IOException {
        dc.getOutputStream().write(data);
        dc.getOutputStream().close();
        return dc.getDigest();
    }

    /**
     * Calculates message digest using the provided digest calculator.
     *
     * @param dc   the digest calculator
     * @param data the data
     * @return message digest
     * @throws IOException if the digest cannot be calculated
     */
    public static byte[] calculateDigest(DigestCalculator dc, InputStream data)
            throws IOException {
        IOUtils.copy(data, dc.getOutputStream());
        dc.getOutputStream().close();
        return dc.getDigest();
    }

    /**
     * Calculates message digest using the provided algorithm.
     *
     * @param algorithm the algorithm
     * @param data      the data
     * @return message digest
     * @throws IOException if an I/O error occurred
     */
    public static byte[] calculateDigest(AlgorithmIdentifier algorithm, byte[] data) throws IOException {
        DigestCalculator dc = createDigestCalculator(algorithm);
        return calculateDigest(dc, data);
    }

    /**
     * Calculates message digest using the provided algorithm id.
     *
     * @param algorithm the algorithm
     * @param data      the data
     * @return message digest
     * @throws IOException if an I/O error occurred
     */
    public static byte[] calculateDigest(DigestAlgorithm algorithm, byte[] data) throws IOException {
        DigestCalculator dc = createDigestCalculator(algorithm);
        return calculateDigest(dc, data);
    }

    /**
     * Calculates message digest using the provided algorithm id.
     *
     * @param algorithm the algorithm
     * @param data      the data
     * @return message digest
     * @throws IOException if an I/O error occurred
     */
    public static byte[] calculateDigest(DigestAlgorithm algorithm, InputStream data)
            throws IOException {
        DigestCalculator dc = createDigestCalculator(algorithm);
        return calculateDigest(dc, data);
    }

    /**
     * Creates a new digest calculator with the specified algorithm identifier.
     *
     * @param algorithm the algorithm identifier
     * @return a new digest calculator instance
     * @throws XrdRuntimeException if the calculator cannot be created
     */
    public static DigestCalculator createDigestCalculator(AlgorithmIdentifier algorithm) {
        try {
            return DIGEST_PROVIDER.get(algorithm);
        } catch (OperatorCreationException e) {
            throw XrdRuntimeException.systemInternalError("Failed to create digest calculator for algorithm: " + algorithm, e);
        }
    }

    /**
     * Creates a new digest calculator with the specified algorithm name.
     *
     * @param algorithm the algorithm name
     * @return a new digest calculator instance
     */
    public static DigestCalculator createDigestCalculator(DigestAlgorithm algorithm) {
        return createDigestCalculator(getAlgorithmIdentifier(algorithm));
    }

    /**
     * @param alg the algorithm identifier
     * @return the cached AlgorithmIdentifier object for the given digest
     * algorithm identifier.
     */
    public static AlgorithmIdentifier getAlgorithmIdentifier(DigestAlgorithm alg) {
        return ALGORITHM_IDENTIFIER_CACHE.computeIfAbsent(alg, key ->
                new DefaultDigestAlgorithmIdentifierFinder().find(key.name()));
    }

    /**
     * Calculates a SHA-224 digest of the given bytes and encodes it in
     * format 92:62:34:C5:39:1B:95:1F:BF:AF:8D:D6:23:24:AE:56:83:DC...
     *
     * @param bytes the bytes
     * @return calculated hex hash uppercase and separated by semicolons String
     * @throws HexCalculationException if any errors occur
     */
    public static String calculateAnchorHashDelimited(byte[] bytes) {
        try {
            return hexDigest(DEFAULT_ANCHOR_HASH_ALGORITHM_ID, bytes)
                    .toUpperCase()
                    .replaceAll("(?<=..)(..)", ":$1");
        } catch (Exception e) {
            throw new HexCalculationException(e);
        }
    }

    /**
     * Digests the input data and hex-encodes the result.
     *
     * @param hashAlg Name of the hash algorithm
     * @param data    Data to be hashed
     * @return hex encoded String of the input data digest
     * @throws IOException if an I/O error occurred
     */
    public static String hexDigest(DigestAlgorithm hashAlg, byte[] data) throws IOException {
        return encodeHex(calculateDigest(hashAlg, data));
    }

    /**
     * Digests the input data and hex-encodes the result.
     *
     * @param hashAlg Name of the hash algorithm
     * @param data    Data to be hashed
     * @return hex encoded String of the input data digest
     * @throws IOException if an I/O error occurred
     */
    public static String hexDigest(DigestAlgorithm hashAlg, String data) throws IOException {
        return hexDigest(hashAlg, data.getBytes(StandardCharsets.UTF_8));
    }
}
