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
package org.niis.xroad.signer.softtoken.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SyncHealthStateTest {

    private SyncHealthState syncHealthState;

    @BeforeEach
    void setUp() {
        syncHealthState = new SyncHealthState();
    }

    @Test
    void initialStateHasZeroFailuresAndEmptyOptionals() {
        assertThat(syncHealthState.getConsecutiveFailures()).isZero();
        assertThat(syncHealthState.getLastSuccessfulSync()).isEmpty();
        assertThat(syncHealthState.getLastFailureMessage()).isEmpty();
    }

    @Test
    void recordSuccessResetsFailuresAndUpdatesTimestamp() {
        var before = Instant.now();
        syncHealthState.recordSuccess();

        assertThat(syncHealthState.getConsecutiveFailures()).isZero();
        assertThat(syncHealthState.getLastSuccessfulSync()).isPresent();
        assertThat(syncHealthState.getLastSuccessfulSync().get()).isAfterOrEqualTo(before);
        assertThat(syncHealthState.getLastFailureMessage()).isEmpty();
    }

    @Test
    void recordFailureIncrementsCountAndSetsMessage() {
        syncHealthState.recordFailure("test error");

        assertThat(syncHealthState.getConsecutiveFailures()).isEqualTo(1);
        assertThat(syncHealthState.getLastFailureMessage()).contains("test error");
    }

    @Test
    void consecutiveFailuresIncrementOnMultipleFailures() {
        syncHealthState.recordFailure("error 1");
        syncHealthState.recordFailure("error 2");
        syncHealthState.recordFailure("error 3");

        assertThat(syncHealthState.getConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    void recordSuccessAfterFailuresResetsCount() {
        syncHealthState.recordFailure("error 1");
        syncHealthState.recordFailure("error 2");
        syncHealthState.recordFailure("error 3");

        syncHealthState.recordSuccess();

        assertThat(syncHealthState.getConsecutiveFailures()).isZero();
    }

    @Test
    void recordFailureDoesNotClearLastSuccessfulSync() {
        syncHealthState.recordSuccess();
        var successTimestamp = syncHealthState.getLastSuccessfulSync();
        assertThat(successTimestamp).isPresent();

        syncHealthState.recordFailure("err");

        assertThat(syncHealthState.getLastSuccessfulSync()).isEqualTo(successTimestamp);
    }
}
