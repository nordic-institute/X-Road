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
package org.niis.xroad.common.acme;

import org.shredzone.acme4j.exception.AcmeException;

import java.util.Objects;
import java.util.Optional;

/**
 * Carries a certificate profile id from {@link AcmeService} down to the ACME connection layer out of band, so that
 * the connection needs no knowledge of CSR contents or CA lists to decide whether (and with which value) to send
 * the {@code profile_id} header.
 * <p>
 * acme4j creates a connection per call and gives it no way to accept caller-supplied, per-request context; every
 * acme4j operation participating in one order, however, runs synchronously on the thread that placed the order,
 * with no internal thread hand-off. A {@link ThreadLocal} scoped around the call that needs the profile id is
 * therefore safe, provided the caller never continues the same order on a different thread.
 * <p>
 * The value is always cleared in a {@code finally} block, so a failed call never leaks its profile id to whichever
 * order a pooled thread services next.
 */
public final class AcmeProfileIdContext {

    private static final ThreadLocal<String> CURRENT_PROFILE_ID = new ThreadLocal<>();

    private AcmeProfileIdContext() {
    }

    /**
     * An ACME operation that may fail with an {@link AcmeException}.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface AcmeAction<T> {
        T run() throws AcmeException;
    }

    /**
     * Runs {@code action} with {@code profileId} visible to {@link #current()} on the calling thread, for the
     * duration of the call. A {@code null} profile id runs {@code action} unchanged.
     */
    public static <T> T runWithProfileId(String profileId, AcmeAction<T> action) throws AcmeException {
        Objects.requireNonNull(action, "action must not be null");
        if (profileId == null) {
            return action.run();
        }
        CURRENT_PROFILE_ID.set(profileId);
        try {
            return action.run();
        } finally {
            CURRENT_PROFILE_ID.remove();
        }
    }

    /**
     * The profile id set by the innermost enclosing {@link #runWithProfileId(String, AcmeAction)} call on this
     * thread, or empty if none is active.
     */
    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT_PROFILE_ID.get());
    }
}
