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

package org.niis.xroad.edc.extension.assetaccess.cooldown;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownTrackerTest {

    private static final Duration PERIOD = Duration.ofSeconds(180);
    private static final String ADDRESS_1 = "https://ss1.example.org/dsp";
    private static final String ADDRESS_2 = "https://ss2.example.org/dsp";
    private static final String ADDRESS_3 = "https://ss3.example.org/dsp";

    private static final String MEMBER_CONTEXT_ADDRESS = "https://ss1.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String OTHER_MEMBER_CONTEXT_ADDRESS = "https://ss1.example.org:8183/api/dsp/DEV:COM:333/http-dsp-profile-2025-1";
    private static final String SYSTEM_CONTEXT_ADDRESS = "https://ss1.example.org:8183/api/dsp/system/http-dsp-profile-2025-1";
    private static final String OTHER_HOST_ADDRESS = "https://ss2.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String OTHER_PORT_ADDRESS = "https://ss1.example.org:9183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";

    private final FakeTicker ticker = new FakeTicker();
    private final CooldownTracker tracker = new CooldownTracker(PERIOD, ticker);

    @Test
    void markedAddressIsCoolingDownUntilThePeriodExpires() {
        tracker.markFailed(ADDRESS_1);

        assertThat(tracker.isCoolingDown(ADDRESS_1)).isTrue();
        assertThat(tracker.isCoolingDown(ADDRESS_2)).isFalse();

        ticker.advance(PERIOD.minusSeconds(1));
        assertThat(tracker.isCoolingDown(ADDRESS_1)).isTrue();

        ticker.advance(Duration.ofSeconds(1));
        assertThat(tracker.isCoolingDown(ADDRESS_1)).isFalse();
    }

    @Test
    void addressIsSelectableAgainOnceThePeriodHasExpired() {
        tracker.markFailed(ADDRESS_1);
        assertThat(select(ADDRESS_1, ADDRESS_2)).containsExactly(ADDRESS_2);

        ticker.advance(PERIOD);

        assertThat(select(ADDRESS_1, ADDRESS_2)).containsExactly(ADDRESS_1, ADDRESS_2);
    }

    @Test
    void repeatedFailureDuringCooldownRestartsThePeriodFromTheLatestFailure() {
        tracker.markFailed(ADDRESS_1);
        ticker.advance(Duration.ofSeconds(100));

        tracker.markFailed(ADDRESS_1);
        ticker.advance(Duration.ofSeconds(100));

        assertThat(tracker.isCoolingDown(ADDRESS_1)).isTrue();

        ticker.advance(Duration.ofSeconds(80));

        assertThat(tracker.isCoolingDown(ADDRESS_1)).isFalse();
    }

    @Test
    void selectableFailsOpenWhenEveryCandidateIsCoolingDown() {
        tracker.markFailed(ADDRESS_1);
        tracker.markFailed(ADDRESS_2);

        assertThat(select(ADDRESS_1, ADDRESS_2)).containsExactly(ADDRESS_1, ADDRESS_2);
    }

    @Test
    void selectableKeepsALoneCandidateThatIsCoolingDown() {
        tracker.markFailed(ADDRESS_1);

        assertThat(select(ADDRESS_1)).containsExactly(ADDRESS_1);
    }

    @Test
    void selectableKeepsTheGivenOrder() {
        tracker.markFailed(ADDRESS_2);

        assertThat(select(ADDRESS_2, ADDRESS_3, ADDRESS_1)).containsExactly(ADDRESS_3, ADDRESS_1);
    }

    @Test
    void failureForOneParticipantContextPutsTheServersOtherContextsInCooldown() {
        tracker.markFailed(MEMBER_CONTEXT_ADDRESS);

        assertThat(tracker.isCoolingDown(OTHER_MEMBER_CONTEXT_ADDRESS)).isTrue();
        assertThat(tracker.isCoolingDown(SYSTEM_CONTEXT_ADDRESS)).isTrue();
        assertThat(select(OTHER_MEMBER_CONTEXT_ADDRESS, OTHER_HOST_ADDRESS)).containsExactly(OTHER_HOST_ADDRESS);
    }

    @Test
    void failureDoesNotAffectAnotherHostOrAnotherPortOnTheSameHost() {
        tracker.markFailed(MEMBER_CONTEXT_ADDRESS);

        assertThat(tracker.isCoolingDown(OTHER_HOST_ADDRESS)).isFalse();
        assertThat(tracker.isCoolingDown(OTHER_PORT_ADDRESS)).isFalse();
    }

    @Test
    void addressThatIsNotAUrlIsItsOwnKey() {
        tracker.markFailed("not a url");

        assertThat(tracker.isCoolingDown("not a url")).isTrue();
        assertThat(tracker.isCoolingDown("another one")).isFalse();
        assertThat(tracker.isCoolingDown(MEMBER_CONTEXT_ADDRESS)).isFalse();
    }

    @Test
    void zeroPeriodDisablesTheTracker() {
        var disabled = new CooldownTracker(Duration.ZERO, ticker);

        disabled.markFailed(ADDRESS_1);

        assertThat(disabled.isCoolingDown(ADDRESS_1)).isFalse();
        assertThat(disabled.selectable(List.of(ADDRESS_1, ADDRESS_2), Function.identity()))
                .containsExactly(ADDRESS_1, ADDRESS_2);
    }

    @Test
    void negativePeriodDisablesTheTrackerAndReportsAZeroPeriod() {
        var disabled = new CooldownTracker(Duration.ofSeconds(-5), ticker);

        disabled.markFailed(ADDRESS_1);

        assertThat(disabled.isCoolingDown(ADDRESS_1)).isFalse();
        assertThat(disabled.period()).isEqualTo(Duration.ZERO);
    }

    private List<String> select(String... addresses) {
        return tracker.selectable(List.of(addresses), Function.identity());
    }

    private static final class FakeTicker extends Ticker {
        private long ticks;
        @Override
        public long read() {
            return ticks;
        }
        private void advance(Duration duration) {
            ticks += duration.toNanos();
        }
    }
}
