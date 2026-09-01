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
package org.niis.xroad.common.vault;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoopVaultClientTest {

    private final NoopVaultClient vaultClient = new NoopVaultClient();

    @Test
    void getAcmeAccountKeyShouldReturnEmpty() {
        var result = vaultClient.getAcmeAccountKey("some-alias");

        assertThat(result).isEmpty();
    }

    @Test
    void createAcmeAccountKeyShouldThrow() {
        var acmeAccountKey = new AcmeAccountKey(null, null, Instant.now());

        assertThatThrownBy(() -> vaultClient.createAcmeAccountKey("some-alias", acmeAccountKey))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getDsTlsEnrollmentStatusShouldReturnEmpty() {
        var result = vaultClient.getDsTlsEnrollmentStatus();

        assertThat(result).isEmpty();
    }

    @Test
    void createDsTlsEnrollmentStatusShouldThrow() {
        var status = new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, Instant.now(), null);

        assertThatThrownBy(() -> vaultClient.createDsTlsEnrollmentStatus(status))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acmeAccountKeyPathShouldBeSanitizedAndCollisionFreePerAlias() {
        var pathOne = vaultClient.getAcmeAccountKeyPath("auth_CS:ORG:MEMBER1");
        var pathTwo = vaultClient.getAcmeAccountKeyPath("sign_CS:ORG:MEMBER1");

        assertThat(pathOne).startsWith(VaultClient.ACME_ACCOUNT_KEYS_BASE_PATH + "/");
        assertThat(pathOne).doesNotContain(":");
        assertThat(pathOne).isNotEqualTo(pathTwo);
    }
}
