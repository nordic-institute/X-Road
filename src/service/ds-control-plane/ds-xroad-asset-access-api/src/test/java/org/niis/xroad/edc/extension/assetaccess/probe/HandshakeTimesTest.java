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

package org.niis.xroad.edc.extension.assetaccess.probe;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HandshakeTimesTest {

    private static final double SMOOTHING_FACTOR = 0.3;
    private static final String FAST = "https://fast.example.org/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String SLOW = "https://slow.example.org/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";

    private static final String MEMBER_CONTEXT_ADDRESS = "https://ss1.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String OTHER_MEMBER_CONTEXT_ADDRESS = "https://ss1.example.org:8183/api/dsp/DEV:COM:333/http-dsp-profile-2025-1";
    private static final String SYSTEM_CONTEXT_ADDRESS = "https://ss1.example.org:8183/api/dsp/system/http-dsp-profile-2025-1";
    private static final String OTHER_PORT_ADDRESS = "https://ss1.example.org:9183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";

    private final HandshakeTimes handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);

    @Test
    void aServerWithoutHandshakesHasNoAverage() {
        assertThat(handshakeTimes.average(FAST)).isEmpty();
    }

    @Test
    void theFirstHandshakeIsTheAverage() {
        handshakeTimes.record(FAST, Duration.ofMillis(100));

        assertThat(handshakeTimes.average(FAST)).contains(Duration.ofMillis(100));
    }

    @Test
    void eachHandshakeMovesTheAverageByTheSmoothingFactorTowardsIt() {
        handshakeTimes.record(FAST, Duration.ofMillis(100));
        handshakeTimes.record(FAST, Duration.ofMillis(200));

        assertThat(handshakeTimes.average(FAST)).contains(Duration.ofMillis(130));
    }

    @Test
    void theSmoothingFactorSetsTheWeightOfTheNewestHandshake() {
        var half = new HandshakeTimes(0.5);
        var latestOnly = new HandshakeTimes(1);
        for (var sample : new int[] {100, 200}) {
            half.record(FAST, Duration.ofMillis(sample));
            latestOnly.record(FAST, Duration.ofMillis(sample));
        }

        assertThat(half.average(FAST)).contains(Duration.ofMillis(150));
        assertThat(latestOnly.average(FAST)).contains(Duration.ofMillis(200));
    }

    @Test
    void recentHandshakesWeighMoreThanOldOnes() {
        var latestIsHigh = new HandshakeTimes(SMOOTHING_FACTOR);
        var earliestIsHigh = new HandshakeTimes(SMOOTHING_FACTOR);
        for (var sample : new int[] {100, 100, 100, 100, 100, 100, 500}) {
            latestIsHigh.record(FAST, Duration.ofMillis(sample));
        }
        for (var sample : new int[] {500, 100, 100, 100, 100, 100, 100}) {
            earliestIsHigh.record(FAST, Duration.ofMillis(sample));
        }

        assertThat(latestIsHigh.average(FAST)).contains(Duration.ofMillis(220));
        assertThat(latestIsHigh.average(FAST).orElseThrow()).isGreaterThan(earliestIsHigh.average(FAST).orElseThrow());
    }

    @Test
    void theAverageFollowsASustainedChange() {
        for (var sample : new int[] {100, 100, 100, 200, 200}) {
            handshakeTimes.record(FAST, Duration.ofMillis(sample));
        }

        assertThat(handshakeTimes.average(FAST)).contains(Duration.ofMillis(151));
    }

    @Test
    void serversAreAveragedIndependently() {
        handshakeTimes.record(FAST, Duration.ofMillis(50));
        handshakeTimes.record(SLOW, Duration.ofMillis(400));

        assertThat(handshakeTimes.average(FAST)).contains(Duration.ofMillis(50));
        assertThat(handshakeTimes.average(SLOW)).contains(Duration.ofMillis(400));
    }

    @Test
    void participantContextsOfOneServerShareOneAverage() {
        handshakeTimes.record(MEMBER_CONTEXT_ADDRESS, Duration.ofMillis(100));
        handshakeTimes.record(OTHER_MEMBER_CONTEXT_ADDRESS, Duration.ofMillis(200));

        assertThat(handshakeTimes.average(SYSTEM_CONTEXT_ADDRESS)).contains(Duration.ofMillis(130));
        assertThat(handshakeTimes.average(MEMBER_CONTEXT_ADDRESS)).contains(Duration.ofMillis(130));
    }

    @Test
    void anotherPortOnTheSameHostHasItsOwnAverage() {
        handshakeTimes.record(MEMBER_CONTEXT_ADDRESS, Duration.ofMillis(100));

        assertThat(handshakeTimes.average(OTHER_PORT_ADDRESS)).isEmpty();
    }

    @Test
    void forgettingOneParticipantContextForgetsTheServerForAllItsContexts() {
        handshakeTimes.record(MEMBER_CONTEXT_ADDRESS, Duration.ofMillis(100));

        handshakeTimes.forget(OTHER_MEMBER_CONTEXT_ADDRESS);

        assertThat(handshakeTimes.average(MEMBER_CONTEXT_ADDRESS)).isEmpty();
        assertThat(handshakeTimes.average(SYSTEM_CONTEXT_ADDRESS)).isEmpty();
    }

    @Test
    void addressThatIsNotAUrlIsItsOwnKey() {
        handshakeTimes.record("not a url", Duration.ofMillis(100));

        assertThat(handshakeTimes.average("not a url")).contains(Duration.ofMillis(100));
        assertThat(handshakeTimes.average("another one")).isEmpty();
        assertThat(handshakeTimes.average(MEMBER_CONTEXT_ADDRESS)).isEmpty();
    }

    @Test
    void aForgottenServerHasNoAverageAndItsNextSampleSeedsANewOne() {
        handshakeTimes.record(FAST, Duration.ofMillis(50));
        handshakeTimes.record(SLOW, Duration.ofMillis(400));

        handshakeTimes.forget(FAST);

        assertThat(handshakeTimes.average(FAST)).isEmpty();
        assertThat(handshakeTimes.average(SLOW)).contains(Duration.ofMillis(400));
        handshakeTimes.record(FAST, Duration.ofMillis(200));
        assertThat(handshakeTimes.average(FAST)).contains(Duration.ofMillis(200));
    }

    @Test
    void theAverageOfAConsistentlyFasterServerStaysBelowASlowerOnesAfterAFewRepeatedAcquisitions() {
        handshakeTimes.record(SLOW, Duration.ofMillis(10));
        handshakeTimes.record(FAST, Duration.ofMillis(60));
        var averageBelow = 0;

        for (var acquisition = 0; acquisition < 10; acquisition++) {
            handshakeTimes.record(SLOW, Duration.ofMillis(300));
            handshakeTimes.record(FAST, Duration.ofMillis(60));
            if (handshakeTimes.average(FAST).orElseThrow().compareTo(handshakeTimes.average(SLOW).orElseThrow()) < 0) {
                averageBelow++;
            }
        }

        assertThat(averageBelow).isEqualTo(10);
        assertThat(handshakeTimes.average(FAST)).contains(Duration.ofMillis(60));
    }
}
