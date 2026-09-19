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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.vault.VaultClient.LAST_ERROR_KEY;
import static org.niis.xroad.common.vault.VaultClient.METHOD_KEY;
import static org.niis.xroad.common.vault.VaultClient.NEXT_RENEWAL_TIME_KEY;

class DsTlsEnrollmentStatusSecretTest {

    private final VaultClient vaultClient = new NoopVaultClient();

    @Test
    void clearedRecordShouldSerializeToANonEmptySecretWithEveryKeyBlank() {
        var secret = vaultClient.toDsTlsEnrollmentStatusSecret(new DsTlsEnrollmentStatus(null, null, null));

        assertThat(secret).containsOnlyKeys(METHOD_KEY, NEXT_RENEWAL_TIME_KEY, LAST_ERROR_KEY);
        assertThat(secret.values()).allMatch(String::isEmpty);
    }

    @Test
    void clearedRecordShouldRoundTrip() {
        var cleared = new DsTlsEnrollmentStatus(null, null, null);

        var parsed = vaultClient.toDsTlsEnrollmentStatus(vaultClient.toDsTlsEnrollmentStatusSecret(cleared));

        assertThat(parsed).isEqualTo(cleared);
        assertThat(parsed.configured()).isFalse();
    }

    @Test
    void fullRecordShouldRoundTrip() {
        var full = new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, Instant.parse("2026-12-01T10:15:30Z"), "CA unreachable");

        var parsed = vaultClient.toDsTlsEnrollmentStatus(vaultClient.toDsTlsEnrollmentStatusSecret(full));

        assertThat(parsed).isEqualTo(full);
    }

    @Test
    void recordWithoutOptionalKeysShouldParse() {
        var parsed = vaultClient.toDsTlsEnrollmentStatus(Map.of(METHOD_KEY, "MANUAL"));

        assertThat(parsed).isEqualTo(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.MANUAL, null, null));
    }

    @Test
    void blankValuesShouldParseAsAbsent() {
        var parsed = vaultClient.toDsTlsEnrollmentStatus(Map.of(METHOD_KEY, "", NEXT_RENEWAL_TIME_KEY, " ", LAST_ERROR_KEY, ""));

        assertThat(parsed).isEqualTo(new DsTlsEnrollmentStatus(null, null, null));
    }
}
