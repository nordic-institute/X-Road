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
package ee.ria.xroad.common.conf.globalconf;

import org.junit.jupiter.api.Test;

import static ee.ria.xroad.common.util.CryptoUtils.SHA1_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA256_ID;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertHashTest {
    public static final byte[] CERT_BYTES = {1, 2, 3};
    public static final byte[] SHA1_HASH = decodeBase64("cDeAcZjCKn0rCAc3HXY3eahP388=");
    public static final byte[] SHA256_HASH = decodeBase64("A5BYxvLAy0ksUzsKTRTvd8wPeKvMztUofYShogEc+4E=");

    @Test
    void calculateHash() {
        CertHash certHash = new CertHash(CERT_BYTES);

        assertThat(certHash.getHash(SHA256_ID)).isEqualTo(SHA256_HASH);
        assertThat(certHash.getHash(SHA1_ID)).isEqualTo(SHA1_HASH);
    }

    @Test
    void getHash() {
        CertHash certHash = new CertHash(SHA256_ID, SHA256_HASH);

        var hash = certHash.getHash(SHA256_ID);

        assertThat(hash).isEqualTo(SHA256_HASH);
    }

    @Test
    void getHashInvalidAlgorithm() {
        CertHash certHash = new CertHash(SHA256_ID, SHA256_HASH);

        assertThatThrownBy(() -> certHash.getHash(SHA1_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hash for algorithm " + SHA1_ID + " is not available");
    }

}
