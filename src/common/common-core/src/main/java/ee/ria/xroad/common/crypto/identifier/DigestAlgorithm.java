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
package ee.ria.xroad.common.crypto.identifier;

import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.lang3.StringUtils;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public sealed interface DigestAlgorithm {
    Index INDEX = new Index();
    /** Hash algorithm uri constants. */
    DigestAlgorithm MD5 = INDEX.create(MessageDigestAlgorithms.MD5, MessageDigestAlgorithm.ALGO_ID_DIGEST_NOT_RECOMMENDED_MD5);
    DigestAlgorithm SHA1 = INDEX.create(MessageDigestAlgorithms.SHA_1, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);
    DigestAlgorithm SHA224 = INDEX.create(MessageDigestAlgorithms.SHA_224, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA224);
    DigestAlgorithm SHA256 = INDEX.create(MessageDigestAlgorithms.SHA_256, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
    DigestAlgorithm SHA384 = INDEX.create(MessageDigestAlgorithms.SHA_384, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA384);
    DigestAlgorithm SHA512 = INDEX.create(MessageDigestAlgorithms.SHA_512, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA512);

    String name();

    String uri();

    String toString();

    static DigestAlgorithm ofUri(String uri) {
        return INDEX.ofUri(uri).orElseGet(() -> new UnknownDigestAlgorithm(null, uri));
    }

    static DigestAlgorithm ofName(String name) {
        return INDEX.ofName(name).orElseGet(() -> new UnknownDigestAlgorithm(name, null));
    }

    record KnownDigestAlgorithm(String name, String uri) implements DigestAlgorithm {
        @Override
        public String toString() {
            return "kDA[name=%s, uri=%s]".formatted(name, uri);
        }
    }

    record UnknownDigestAlgorithm(String name, String uri) implements DigestAlgorithm {

        @Override
        public String name() {
            if (name == null) {
                throw new UnknownAlgorithmException("Unknown digest algorithm name for uri: %s".formatted(uri()));
            }
            return name;
        }

        @Override
        public String uri() {
            if (uri == null) {
                throw new UnknownAlgorithmException("Unknown digest algorithm uri for name: %s".formatted(name()));
            }
            return uri;
        }

        @Override
        public String toString() {
            return "uDA[name=%s, uri=%s]".formatted(name, uri);
        }
    }

    final class Index {
        private final Map<String, DigestAlgorithm> byName = new HashMap<>();
        private final Map<String, DigestAlgorithm> byUri = new HashMap<>();

        private Index() {
        }

        private KnownDigestAlgorithm create(String name, String uri) {
            var alg = new KnownDigestAlgorithm(name, uri);
            byName.put(StringUtils.lowerCase(name), alg);
            byUri.put(uri, alg);
            return alg;
        }

        private Optional<DigestAlgorithm> ofUri(String uri) {
            return Optional.ofNullable(byUri.get(uri));
        }

        private Optional<DigestAlgorithm> ofName(String name) {
            return Optional.ofNullable(byName.get(StringUtils.lowerCase(name)));
        }
    }
}



