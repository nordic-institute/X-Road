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
package org.niis.xroad.common.core.time;

import org.junit.jupiter.api.Test;
import org.niis.xroad.test.MutableClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CallBudgetTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));

    @Test
    void remainingShrinksWithTheClockAndNeverGoesNegative() {
        var budget = CallBudget.startingNow(clock, Duration.ofSeconds(60));

        clock.advance(Duration.ofSeconds(25));
        assertThat(budget.remaining()).isEqualTo(Duration.ofSeconds(35));
        assertThat(budget.isExhausted()).isFalse();

        clock.advance(Duration.ofSeconds(100));
        assertThat(budget.remaining()).isEqualTo(Duration.ZERO);
        assertThat(budget.isExhausted()).isTrue();
    }

    @Test
    void stageIsItsCapWhileTimeIsPlentyAndTheTimeLeftOtherwise() {
        var budget = CallBudget.startingNow(clock, Duration.ofSeconds(60));

        assertThat(budget.stage(Duration.ofSeconds(20))).isEqualTo(Duration.ofSeconds(20));

        clock.advance(Duration.ofSeconds(50));
        assertThat(budget.stage(Duration.ofSeconds(20))).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shareDividesTheTimeLeftAmongTheAlternativesLeftAndKeepsTheCap() {
        var budget = CallBudget.startingNow(clock, Duration.ofSeconds(60));

        assertThat(budget.share(Duration.ofSeconds(60), 3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(budget.share(Duration.ofSeconds(15), 3)).isEqualTo(Duration.ofSeconds(15));

        clock.advance(Duration.ofSeconds(30));
        assertThat(budget.share(Duration.ofSeconds(60), 2)).isEqualTo(Duration.ofSeconds(15));
        assertThat(budget.share(Duration.ofSeconds(60), 1)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shareTreatsNoAlternativesLeftAsOne() {
        var budget = CallBudget.startingNow(clock, Duration.ofSeconds(60));

        assertThat(budget.share(Duration.ofSeconds(60), 0)).isEqualTo(Duration.ofSeconds(60));
    }
}
