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

package org.niis.xroad.e2e.parity;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Probes a call until it returns the expected {@link Verdict} or the deadline passes. Access-right and
 * service changes reach the provider through a cache, so a verdict flips only after that cache expires.
 * A probe that throws counts as {@link Verdict#ERROR} and polling continues.
 */
public final class VerdictPoller {

    /** Time source and sleeper, replaceable so tests need not wait. */
    public interface Clock {
        long nowMillis();

        void sleepMillis(long millis);
    }

    /**
     * @param verdict the verdict the probe reached
     * @param detail  a short description of what came back, for example a fault code, or {@code null} for none
     */
    public record Observation(Verdict verdict, String detail) {
    }

    /**
     * @param last     the verdict of the final probe
     * @param detail   what came back on the final probe, or {@code null} for none
     * @param matched  whether the final probe returned the expected verdict
     * @param attempts how many probes ran
     * @param elapsed  time spent from the first probe to the last
     */
    public record Result(Verdict last, String detail, boolean matched, int attempts, Duration elapsed) {

        /** The assertion message for a scenario that did not reach its expected verdict. */
        public String failureMessage(String scenarioId, Verdict expected) {
            var message = "%s: expected %s but got %s after %d attempts in %d s"
                    .formatted(scenarioId, expected, last, attempts, elapsed.toSeconds());
            return detail == null ? message : message + "; last reply: " + detail;
        }
    }

    private static final Clock SYSTEM = new Clock() {
        @Override
        public long nowMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public void sleepMillis(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while polling", e);
            }
        }
    };

    private final Clock clock;

    public VerdictPoller() {
        this(SYSTEM);
    }

    public VerdictPoller(Clock clock) {
        this.clock = clock;
    }

    public Result await(Supplier<Observation> probe, Verdict expected, Duration timeout, Duration interval) {
        var start = clock.nowMillis();
        var attempts = 0;
        while (true) {
            attempts++;
            var observation = probeOnce(probe);
            var elapsedMillis = clock.nowMillis() - start;
            var matched = observation.verdict() == expected;
            if (matched || elapsedMillis >= timeout.toMillis()) {
                return new Result(observation.verdict(), observation.detail(), matched, attempts,
                        Duration.ofMillis(elapsedMillis));
            }
            clock.sleepMillis(interval.toMillis());
        }
    }

    private static Observation probeOnce(Supplier<Observation> probe) {
        try {
            return probe.get();
        } catch (RuntimeException e) {
            return new Observation(Verdict.ERROR, "exception " + e.getClass().getSimpleName());
        }
    }
}
