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

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SignaturesTest {
    private static final String EC_ASN_SIGN = "MEUCIQDInnhVlgJEPII1IxNoLYCPf/4J8aT9bgm2NqGB7"
            + "NikwQIgOG5zIavPdiOcjHOcdYB7Y6AkroehJ9kGSltYu4h7k/E=";
    private static final String EC_RAW_SIGN = "e4a/UFydU5o4lXY/eQyu4m2/VOPiSoWebF9bIMIDY0WodemZpw3EPvhTpSUp4Fqe1Qb08rA+81jdKvVFxWHvYQ==";
    private static final String RSA_SIGN = "    SZtX5nzdtU6ImT0RvM6wW7FhdQI0dqIJxLdjAN2qI7LpOah5S3cj2nbzp0F20hnGAqC3wrljYioAd8pV97tFe16k"
            + "mPW6EshAxVMXsNkHdcU/TAze3QK9s64mhz5Wxg5jJBcZLSElXRV31XkpFzTl+zmYXzEtlftz+bnaS8KX52ZOCuCX7vA4vgH09g5qJjMTx2mbaXEaGEjMc8tZ4g"
            + "lAdhzVEduSzUDDkI3rRiXsuqL7ScBcOPUIJ9NEr2KWJ5QqPqiHu4Mlq+2d6avpOjqfWaIGOUGN3Yybw03kT5yCqj9rQ6KN2p4gJqQlWwugAqvMOTVbP5F9EX0z"
            + "1V8FpmI3QA==";


    @Test
    void isAsn1DerSignature() {
        assertThat(Signatures.isAsn1DerSignature(bytes(EC_ASN_SIGN))).isTrue();
        assertThat(Signatures.isAsn1DerSignature(bytes(EC_RAW_SIGN))).isFalse();
        assertThat(Signatures.isAsn1DerSignature(bytes(RSA_SIGN))).isFalse();
    }

    @Test
    void useRawFormat() throws IOException {
        assertThat(Signatures.useRawFormat(SignAlgorithm.SHA256_WITH_ECDSA, bytes(EC_RAW_SIGN))).containsExactly(bytes(EC_RAW_SIGN));
        assertThat(Signatures.useRawFormat(SignAlgorithm.SHA256_WITH_RSA, bytes(RSA_SIGN))).containsExactly(bytes(RSA_SIGN));

        var raw = Signatures.useRawFormat(SignAlgorithm.SHA256_WITH_ECDSA, bytes(EC_ASN_SIGN));
        assertThat(Signatures.isAsn1DerSignature(raw)).isFalse();
        var ans = Signatures.useAsn1DerFormat(SignAlgorithm.SHA256_WITH_ECDSA, raw);
        assertThat(Signatures.isAsn1DerSignature(ans)).isTrue();
        assertThat(ans).containsExactly(bytes(EC_ASN_SIGN));
    }

    @Test
    void useAsn1DerFormat() throws IOException {
        assertThat(Signatures.useAsn1DerFormat(SignAlgorithm.SHA256_WITH_ECDSA, bytes(EC_ASN_SIGN))).containsExactly(bytes(EC_ASN_SIGN));
        assertThat(Signatures.useAsn1DerFormat(SignAlgorithm.SHA256_WITH_RSA, bytes(RSA_SIGN))).containsExactly(bytes(RSA_SIGN));

        var ans = Signatures.useAsn1DerFormat(SignAlgorithm.SHA256_WITH_ECDSA, bytes(EC_RAW_SIGN));
        assertThat(Signatures.isAsn1DerSignature(ans)).isTrue();
        var raw = Signatures.useRawFormat(SignAlgorithm.SHA256_WITH_ECDSA, ans);
        assertThat(Signatures.isAsn1DerSignature(raw)).isFalse();
        assertThat(raw).containsExactly(bytes(EC_RAW_SIGN));
    }

    private static byte[] bytes(String base64) {
        return Base64.decodeBase64(base64);
    }
}
