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

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.acl.PoolVisibility.Target;
import org.niis.xroad.e2e.parity.acl.SubsystemPool.Entry;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Clock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoolVisibilityTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration INTERVAL = Duration.ofSeconds(2);
    private static final Target SIBLING_ON_SS0 = new Target(SubsystemPool.SS0, SubsystemPool.SIBLING_OF_CONSUMER);
    private static final Target SECOND_CONSUMER_ON_SS0 = new Target(SubsystemPool.SS0, SubsystemPool.SECOND_CONSUMER);

    @Test
    void targetsPairEachRegisteredEntryWithTheServersThatDoNotHostIt() {
        var targets = PoolVisibility.targets(SubsystemPool.entries(), SubsystemPool.SERVERS);

        assertThat(targets).containsExactly(SIBLING_ON_SS0, SECOND_CONSUMER_ON_SS0);
    }

    @Test
    void targetsSkipEntriesThatAreNeverRegistered() {
        var saved = new Entry("DEV:COM:1234:Saved", SubsystemPool.SS0, false);

        assertThat(PoolVisibility.targets(List.of(saved), SubsystemPool.SERVERS)).isEmpty();
    }

    @Test
    void returnsAtTheFirstPollWhenEveryTargetIsKnown() {
        var clock = new FakeClock();

        PoolVisibility.await(List.of(SIBLING_ON_SS0, SECOND_CONSUMER_ON_SS0), target -> true, TIMEOUT, INTERVAL, clock);

        assertThat(clock.sleeps).isEmpty();
    }

    @Test
    void keepsPollingUntilTheLastTargetBecomesKnown() {
        var clock = new FakeClock();
        var asked = new ArrayList<Target>();

        PoolVisibility.await(List.of(SIBLING_ON_SS0, SECOND_CONSUMER_ON_SS0), target -> {
            asked.add(target);
            return target.equals(SIBLING_ON_SS0) || asked.size() > 3;
        }, TIMEOUT, INTERVAL, clock);

        assertThat(clock.sleeps).containsExactly(INTERVAL.toMillis(), INTERVAL.toMillis());
    }

    @Test
    void doesNotAskAgainAboutATargetThatIsAlreadyKnown() {
        var clock = new FakeClock();
        var asked = new ArrayList<Target>();

        PoolVisibility.await(List.of(SIBLING_ON_SS0, SECOND_CONSUMER_ON_SS0), target -> {
            asked.add(target);
            return target.equals(SIBLING_ON_SS0) || asked.size() > 2;
        }, TIMEOUT, INTERVAL, clock);

        assertThat(asked).containsExactly(SIBLING_ON_SS0, SECOND_CONSUMER_ON_SS0, SECOND_CONSUMER_ON_SS0);
    }

    @Test
    void failsAfterTheTimeoutNamingTheSubsystemAndTheServerStillUnknown() {
        var clock = new FakeClock();

        assertThatThrownBy(() -> PoolVisibility.await(List.of(SIBLING_ON_SS0, SECOND_CONSUMER_ON_SS0),
                target -> target.equals(SIBLING_ON_SS0), TIMEOUT, INTERVAL, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SubsystemPool.SECOND_CONSUMER + " on " + SubsystemPool.SS0)
                .hasMessageNotContaining(SubsystemPool.SIBLING_OF_CONSUMER);
        assertThat(clock.now).isGreaterThanOrEqualTo(TIMEOUT.toMillis());
    }

    private static final class FakeClock implements Clock {

        private final List<Long> sleeps = new ArrayList<>();
        private long now;

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public void sleepMillis(long millis) {
            sleeps.add(millis);
            now += millis;
        }
    }
}
