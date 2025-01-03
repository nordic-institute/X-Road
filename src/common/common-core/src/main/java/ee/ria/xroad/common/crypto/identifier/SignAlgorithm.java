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

import ee.ria.xroad.common.crypto.CryptoException;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA1;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA224;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA256;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA384;
import static ee.ria.xroad.common.crypto.identifier.DigestAlgorithm.SHA512;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_ECDSA;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_RSA_PKCS;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_RSA_PKCS_PSS;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA1;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA224;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA256;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA384;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA512;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256_MGF1;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA384;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA384_MGF1;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA512;
import static org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA512_MGF1;

public sealed interface SignAlgorithm {
    Index INDEX = new Index();

    /** Digital signature algorithm URIs. */
    SignAlgorithm SHA1_WITH_RSA = INDEX.create("SHA1withRSA", ALGO_ID_SIGNATURE_RSA_SHA1, CKM_RSA_PKCS, SHA1);
    SignAlgorithm SHA256_WITH_RSA = INDEX.create("SHA256withRSA", ALGO_ID_SIGNATURE_RSA_SHA256, CKM_RSA_PKCS, SHA256);
    SignAlgorithm SHA384_WITH_RSA = INDEX.create("SHA384withRSA", ALGO_ID_SIGNATURE_RSA_SHA384, CKM_RSA_PKCS, SHA384);
    SignAlgorithm SHA512_WITH_RSA = INDEX.create("SHA512withRSA", ALGO_ID_SIGNATURE_RSA_SHA512, CKM_RSA_PKCS, SHA512);

    SignAlgorithm SHA256_WITH_RSA_AND_MGF1 = INDEX.create("SHA256withRSAandMGF1", ALGO_ID_SIGNATURE_RSA_SHA256_MGF1,
            CKM_RSA_PKCS_PSS, SHA256);
    SignAlgorithm SHA384_WITH_RSA_AND_MGF1 = INDEX.create("SHA384withRSAandMGF1", ALGO_ID_SIGNATURE_RSA_SHA384_MGF1,
            CKM_RSA_PKCS_PSS, SHA384);
    SignAlgorithm SHA512_WITH_RSA_AND_MGF1 = INDEX.create("SHA512withRSAandMGF1", ALGO_ID_SIGNATURE_RSA_SHA512_MGF1,
            CKM_RSA_PKCS_PSS, SHA512);

    SignAlgorithm SHA1_WITH_ECDSA = INDEX.create("SHA1withECDSA", ALGO_ID_SIGNATURE_ECDSA_SHA1, CKM_ECDSA, SHA1);
    SignAlgorithm SHA224_WITH_ECDSA = INDEX.create("SHA224withECDSA", ALGO_ID_SIGNATURE_ECDSA_SHA224, CKM_ECDSA, SHA224);
    SignAlgorithm SHA256_WITH_ECDSA = INDEX.create("SHA256withECDSA", ALGO_ID_SIGNATURE_ECDSA_SHA256, CKM_ECDSA, SHA256);
    SignAlgorithm SHA384_WITH_ECDSA = INDEX.create("SHA384withECDSA", ALGO_ID_SIGNATURE_ECDSA_SHA384, CKM_ECDSA, SHA384);
    SignAlgorithm SHA512_WITH_ECDSA = INDEX.create("SHA512withECDSA", ALGO_ID_SIGNATURE_ECDSA_SHA512, CKM_ECDSA, SHA512);

    String name();

    String uri();

    default KeyAlgorithm algorithm() {
        return signMechanism().keyAlgorithm();
    }

    DigestAlgorithm digest();

    SignMechanism signMechanism();

    static SignAlgorithm ofUri(String uri) {
        return INDEX.ofUri(uri).orElseGet(() -> new UnknownSignAlgorithm(null, uri));
    }

    static SignAlgorithm ofName(String name) {
        return INDEX.ofName(name).orElseGet(() -> new UnknownSignAlgorithm(name, null));
    }

    static SignAlgorithm ofDigestAndMechanism(DigestAlgorithm digestAlgorithm, SignMechanism signMechanism) {
        return INDEX.ofDigestAndSignMechanism(digestAlgorithm, signMechanism)
                .orElseThrow(
                        () -> new CryptoException("No matching signature algorithm for digest: %s and mechanism: %s"
                                .formatted(digestAlgorithm.name(), signMechanism.name()))
                );
    }

    record KnownSignAlgorithm(String name, String uri, SignMechanism signMechanism, DigestAlgorithm digest) implements SignAlgorithm {
        @Override
        public String toString() {
            return "kSA[name=%s, uri=%s]".formatted(name, uri);
        }
    }

    record UnknownSignAlgorithm(String name, String uri) implements SignAlgorithm {
        @Override
        public String name() {
            if (name == null) {
                throw new CryptoException("Unknown signature algorithm name for uri: %s".formatted(uri()));
            }
            return name;
        }

        @Override
        public String uri() {
            if (uri == null) {
                throw new CryptoException("Unknown signature algorithm uri for name: %s".formatted(name()));
            }
            return uri;
        }

        @Override
        public KeyAlgorithm algorithm() {
            throw new CryptoException("Unknown key type of signature algorithm: %s"
                    .formatted(this));
        }

        @Override
        public DigestAlgorithm digest() {
            throw new CryptoException("Unknown digest method of signature algorithm: %s"
                    .formatted(this));
        }

        @Override
        public SignMechanism signMechanism() {
            throw new CryptoException("Unknown sign mechanism of signature algorithm: %s"
                    .formatted(this));
        }

        @Override
        public String toString() {
            return "uSA[name=%s, uri=%s]".formatted(name, uri);
        }
    }

    final class Index {
        private final Map<String, KnownSignAlgorithm> byName = new HashMap<>();
        private final Map<String, KnownSignAlgorithm> byUri = new HashMap<>();
        private final Map<Key, KnownSignAlgorithm> byDigestAndMechanism = new HashMap<>();

        private Index() {
            Providers.init();
        }

        private KnownSignAlgorithm create(String name, String uri, SignMechanism mechanism, DigestAlgorithm digest) {
            var alg = new KnownSignAlgorithm(name, uri, mechanism, digest);
            byName.put(StringUtils.lowerCase(name), alg);
            byUri.put(uri, alg);
            byDigestAndMechanism.put(new Key(digest, mechanism), alg);
            return alg;
        }

        private Optional<SignAlgorithm> ofUri(String uri) {
            return Optional.ofNullable(byUri.get(uri));
        }

        private Optional<SignAlgorithm> ofName(String name) {
            return Optional.ofNullable(byName.get(StringUtils.lowerCase(name)));
        }

        private Optional<SignAlgorithm> ofDigestAndSignMechanism(DigestAlgorithm algorithm, SignMechanism mechanism) {
            return Optional.ofNullable(byDigestAndMechanism.get(new Key(algorithm, mechanism)));
        }

        private record Key(DigestAlgorithm algorithm, SignMechanism mechanism) {
        }
    }
}
