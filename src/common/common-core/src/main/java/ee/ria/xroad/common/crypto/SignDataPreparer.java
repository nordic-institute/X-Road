/*
 * The MIT License
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
package ee.ria.xroad.common.crypto;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import java.util.Map;

public sealed interface SignDataPreparer {

    byte[] prepare(byte[] data);

    static SignDataPreparer of(SignAlgorithm algorithm) {
        return switch (algorithm.signMechanism()) {
            case CKM_RSA_PKCS -> RsaPkcsPreparer.INSTANCE;
            case CKM_RSA_PKCS_PSS, CKM_ECDSA, CKM_EDDSA -> NoopPreparer.INSTANCE;
        };
    }

    final class NoopPreparer implements SignDataPreparer {

        private static final NoopPreparer INSTANCE = new NoopPreparer();

        private NoopPreparer() {
        }

        public byte[] prepare(byte[] data) {
            return data;
        }
    }

    final class RsaPkcsPreparer implements SignDataPreparer {

        private static final RsaPkcsPreparer INSTANCE = new RsaPkcsPreparer();

        private static final byte[] SHA1_DIGEST_PREFIX = new byte[]{
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b,
                0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

        private static final byte[] SHA224_DIGEST_PREFIX = new byte[]{
                0x30, 0x2d, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x04, 0x05, 0x00, 0x04, 0x1c};

        private static final byte[] SHA256_DIGEST_PREFIX = new byte[]{
                0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20};

        private static final byte[] SHA384_DIGEST_PREFIX = new byte[]{
                0x30, 0x41, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x02, 0x05, 0x00, 0x04, 0x30};

        private static final byte[] SHA512_DIGEST_PREFIX = new byte[]{
                0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40};

        private RsaPkcsPreparer() {
        }


        private static final Map<Integer, byte[]> CACHE = Map.of(
                Digests.SHA1_DIGEST_LENGTH, SHA1_DIGEST_PREFIX,
                Digests.SHA224_DIGEST_LENGTH, SHA224_DIGEST_PREFIX,
                Digests.SHA256_DIGEST_LENGTH, SHA256_DIGEST_PREFIX,
                Digests.SHA384_DIGEST_LENGTH, SHA384_DIGEST_PREFIX,
                Digests.SHA512_DIGEST_LENGTH, SHA512_DIGEST_PREFIX
        );

        private static byte[] getPrefix(byte[] digest) {
            if (CACHE.containsKey(digest.length)) {
                return CACHE.get(digest.length);
            }

            throw new CryptoException("Invalid digest length: " + digest.length);
        }

        /**
         * DigestInfo ::= SEQUENCE {
         *      digestAlgorithm AlgorithmIdentifier,
         *      digest OCTET STRING
         * }
         */
        @Override
        public byte[] prepare(byte[] data) {
            byte[] prefix = getPrefix(data);
            byte[] digestInfo = new byte[prefix.length + data.length];

            System.arraycopy(prefix, 0, digestInfo, 0, prefix.length);
            System.arraycopy(data, 0, digestInfo, prefix.length, data.length);

            return digestInfo;

        }
    }
}
