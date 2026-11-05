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
package org.niis.xroad.securityserver.restapi.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * In-memory exponential backoff for publication attempts. The pause after the n-th consecutive failure of a
 * key doubles from {@link #INITIAL_DELAY} up to {@link #MAX_DELAY}, shortened by a random factor so that servers
 * do not retry in step. It also remembers which request kinds must send a one-entry probe next, and the smallest
 * size of a request of a kind that the Central Server accepted after refusing a larger one. Nothing is
 * persisted: a restart forgets every pause, probe and size, so the next pass may retry early and halve an
 * oversized request again.
 */
public final class PublicationBackoff {

    static final Duration INITIAL_DELAY = Duration.ofSeconds(30);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);
    static final double MIN_JITTER_FACTOR = 0.75;
    private static final int MAX_DOUBLINGS = 16;

    /**
     * The kind of request a failure belongs to.
     */
    public enum RequestKind {
        SYSTEM,
        MEMBER
    }

    /**
     * What a pause applies to: every value of one request kind, or one value of it.
     *
     * @param kind    the request kind
     * @param valueId the value the pause applies to, {@code null} for the whole request kind
     */
    public record Key(RequestKind kind, String valueId) {

        public static Key ofKind(RequestKind kind) {
            return new Key(kind, null);
        }

        public static Key ofValue(RequestKind kind, String valueId) {
            return new Key(kind, valueId);
        }
    }

    private record Entry(int failures, Instant retryAt) {
    }

    private final Clock clock;
    private final DoubleSupplier jitter;
    private final Map<Key, Entry> entries = new HashMap<>();
    private final Set<RequestKind> probing = EnumSet.noneOf(RequestKind.class);
    private final Map<RequestKind, Integer> acceptedSizes = new EnumMap<>(RequestKind.class);

    /**
     * @param clock  the time source
     * @param jitter supplies a number in {@code [0, 1)}; the pause is its base length times
     *               {@code MIN_JITTER_FACTOR + (1 - MIN_JITTER_FACTOR) * jitter}
     */
    public PublicationBackoff(Clock clock, DoubleSupplier jitter) {
        this.clock = clock;
        this.jitter = jitter;
    }

    /**
     * @return whether the key has no pause or its pause is over
     */
    public synchronized boolean mayAttempt(Key key) {
        var entry = entries.get(key);
        return entry == null || !clock.instant().isBefore(entry.retryAt());
    }

    /**
     * Starts the next, longer pause of the key.
     */
    public synchronized void recordFailure(Key key) {
        var failures = entries.containsKey(key) ? entries.get(key).failures() + 1 : 1;
        entries.put(key, new Entry(failures, clock.instant().plus(delayAfter(failures))));
    }

    /**
     * Starts a pause of the longest length, {@link #MAX_DELAY}, before jitter.
     */
    public synchronized void recordMaximumFailure(Key key) {
        var failures = MAX_DOUBLINGS + 1;
        entries.put(key, new Entry(failures, clock.instant().plus(delayAfter(failures))));
    }

    /**
     * Ends the pause of the key and resets its growth.
     */
    public synchronized void recordSuccess(Key key) {
        entries.remove(key);
    }

    /**
     * Makes the next request of the kind a one-entry probe.
     */
    public synchronized void requireProbe(RequestKind kind) {
        probing.add(kind);
    }

    /**
     * Ends the probe of the kind: its requests have normal size again.
     */
    public synchronized void clearProbe(RequestKind kind) {
        probing.remove(kind);
    }

    /**
     * Remembers the size of a request that the Central Server accepted after refusing a larger one. Later
     * requests of the kind start no larger than it; the size never drops below one and never grows back.
     */
    public synchronized void rememberAcceptedSize(RequestKind kind, int size) {
        acceptedSizes.merge(kind, Math.max(1, size), Math::min);
    }

    /**
     * @return one during a probe, else the configured batch size or the remembered accepted size when that is
     * smaller
     */
    public synchronized int batchSize(RequestKind kind, int configured) {
        if (probing.contains(kind)) {
            return 1;
        }
        return Math.min(configured, acceptedSizes.getOrDefault(kind, Integer.MAX_VALUE));
    }

    private Duration delayAfter(int failures) {
        var base = INITIAL_DELAY.multipliedBy(1L << Math.min(failures - 1, MAX_DOUBLINGS));
        var capped = base.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : base;
        var factor = MIN_JITTER_FACTOR + (1 - MIN_JITTER_FACTOR) * jitter.getAsDouble();
        return Duration.ofMillis((long) (capped.toMillis() * factor));
    }
}
