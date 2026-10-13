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

package org.niis.xroad.edc.extension.assetaccess.agreement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefusedAgreementCooldownTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-13T10:00:00Z"));
    private final RefusedAgreementCooldown cooldown = new RefusedAgreementCooldown(new MutableClock(now), Duration.ofSeconds(300));

    @Test
    void unknownAgreementIsNotCoolingDown() {
        assertThat(cooldown.isCoolingDown("agreement-1")).isFalse();
    }

    @Test
    void refusedAgreementCoolsDownForTheConfiguredDurationOnly() {
        cooldown.markRefused("agreement-1");

        now.set(now.get().plusSeconds(299));
        assertThat(cooldown.isCoolingDown("agreement-1")).isTrue();

        now.set(now.get().plusSeconds(1));
        assertThat(cooldown.isCoolingDown("agreement-1")).isFalse();
    }

    @Test
    void markingAgainRestartsTheCooldown() {
        cooldown.markRefused("agreement-1");
        now.set(now.get().plusSeconds(200));
        cooldown.markRefused("agreement-1");

        now.set(now.get().plusSeconds(200));

        assertThat(cooldown.isCoolingDown("agreement-1")).isTrue();
    }

    @Test
    void theSetStaysWithinItsBoundByDroppingTheEntryClosestToExpiry() {
        for (int i = 0; i < RefusedAgreementCooldown.MAX_ENTRIES; i++) {
            cooldown.markRefused("agreement-" + i);
            now.set(now.get().plusMillis(1));
        }

        cooldown.markRefused("agreement-extra");

        assertThat(cooldown.isCoolingDown("agreement-0")).isFalse();
        assertThat(cooldown.isCoolingDown("agreement-1")).isTrue();
        assertThat(cooldown.isCoolingDown("agreement-extra")).isTrue();
    }

    @Test
    void nonPositiveCooldownIsRejected() {
        assertThatThrownBy(() -> new RefusedAgreementCooldown(new MutableClock(now), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefusedAgreementCooldown(new MutableClock(now), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
