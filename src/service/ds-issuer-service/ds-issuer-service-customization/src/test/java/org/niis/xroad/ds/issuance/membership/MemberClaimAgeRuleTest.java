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
package org.niis.xroad.ds.issuance.membership;

import org.eclipse.edc.spi.iam.ClaimToken;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MemberClaimAgeRuleTest {

    private static final long LIFETIME_SECONDS = 300L;
    private static final long LEEWAY_SECONDS = 30L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MemberClaimAgeRule rule = new MemberClaimAgeRule(clock, LIFETIME_SECONDS, LEEWAY_SECONDS);

    @Test
    void passesWhenIatIsExactlyAtTheLifetimePlusLeewayBoundary() {
        var result = rule.checkRule(claimWithIssuedAt(NOW.minusSeconds(LIFETIME_SECONDS + LEEWAY_SECONDS)), null);

        assertThat(result.succeeded()).withFailMessage(result::getFailureDetail).isTrue();
    }

    @Test
    void failsWhenIatIsOneSecondPastTheLifetimePlusLeewayBoundary() {
        var result = rule.checkRule(claimWithIssuedAt(NOW.minusSeconds(LIFETIME_SECONDS + LEEWAY_SECONDS + 1)), null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void passesWhenIatIsNow() {
        var result = rule.checkRule(claimWithIssuedAt(NOW), null);

        assertThat(result.succeeded()).withFailMessage(result::getFailureDetail).isTrue();
    }

    @Test
    void failsWhenIatIsMissing() {
        var result = rule.checkRule(ClaimToken.Builder.newInstance().build(), null);

        assertThat(result.failed()).isTrue();
    }

    private static ClaimToken claimWithIssuedAt(Instant issuedAt) {
        return ClaimToken.Builder.newInstance()
                .claim("iat", issuedAt.getEpochSecond())
                .build();
    }
}
