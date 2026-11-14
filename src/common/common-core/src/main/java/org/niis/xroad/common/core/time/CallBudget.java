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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * One time limit for a whole call, shared by every step of it. The steps ask the budget how long they may
 * take instead of using a fixed limit of their own, so the steps together never outlast the call's limit.
 */
public final class CallBudget {

    private final Clock clock;
    private final Duration total;
    private final Instant deadline;

    private CallBudget(Clock clock, Duration total) {
        this.clock = clock;
        this.total = total;
        this.deadline = clock.instant().plus(total);
    }

    /** @return a budget of {@code total} that starts running now, as measured by {@code clock} */
    public static CallBudget startingNow(Clock clock, Duration total) {
        return new CallBudget(clock, total);
    }

    /** @return the limit the budget started with */
    public Duration total() {
        return total;
    }

    /** @return the time left, never negative */
    public Duration remaining() {
        var left = Duration.between(clock.instant(), deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** @return true when no time is left */
    public boolean isExhausted() {
        return remaining().isZero();
    }

    /** @return the time one step may take: its own {@code cap}, or the time left when that is shorter */
    public Duration stage(Duration cap) {
        return min(cap, remaining());
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
