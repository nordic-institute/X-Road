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
package org.niis.xroad.ds.issuance.identity;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Holds the issuer identity the Central Server operator bound, once bound. {@link #start()} tries {@link
 * BoundIssuerIdentityReader#read()} once synchronously, so a node that starts after binding is ready as soon
 * as {@link #start()} returns; only a failed first attempt falls back to polling, at {@link
 * PeriodicMaterialReloader#DEFAULT_PENDING_INTERVAL}, until the first bound result. Either way, polling then
 * stops for good: a binding never changes, so there is nothing left to poll for. The reader connection opened
 * while waiting is closed as soon as the holder stops.
 *
 * <p>A reader that fails to construct (the Central Server database is unreachable) or fails to read is
 * treated the same as an unbound result: the holder stays unbound and the next poll tries again, reusing the
 * same reader once one has been built successfully.
 *
 * <p>The reloader this holder schedules is closed from within its own completion callback, through a
 * reference this holder assigns only after the schedule call returns. {@link #start()} and {@link
 * #stopPolling()} both check and close that reference, so whichever of the two runs last performs the actual
 * close, however the two race.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class BoundIssuerIdentityHolder implements AutoCloseable {

    static final String NAME = "bound-issuer-identity";

    private final Supplier<BoundIssuerIdentityReader> readerFactory;
    private final Monitor monitor;
    private final Duration pendingInterval;

    private final Object readerLock = new Object();

    private final AtomicReference<BoundIssuerIdentity> identity = new AtomicReference<>();
    private final AtomicReference<PeriodicMaterialReloader<BoundIssuerIdentity>> reloader = new AtomicReference<>();
    private final AtomicReference<BoundIssuerIdentityReader> reader = new AtomicReference<>();
    private volatile boolean stopRequested;
    private boolean readerClosed;

    public BoundIssuerIdentityHolder(Supplier<BoundIssuerIdentityReader> readerFactory, Monitor monitor) {
        this(readerFactory, monitor, PeriodicMaterialReloader.DEFAULT_PENDING_INTERVAL);
    }

    /**
     * Attempts one synchronous load before scheduling any polling, so a node that starts after binding is
     * ready as soon as this call returns, with no poll interval to wait out. Never throws: a first attempt
     * that fails, because the identity is not bound yet or the reader cannot be built or read, is logged and
     * falls back to polling at {@link #pendingInterval} like every later attempt.
     */
    public void start() {
        try {
            applyAndStop(load().material());
            return;
        } catch (Exception e) {
            monitor.warning(NAME + ": the first load did not find a binding; starting degraded and polling", e);
        }
        var scheduled = PeriodicMaterialReloader.scheduleUntilFirstLoad(NAME, pendingInterval, pendingInterval, 1, Duration.ZERO,
                this::load, this::applyAndStop, monitor, null);
        reloader.set(scheduled);
        if (stopRequested) {
            scheduled.close();
        }
    }

    /**
     * @return the bound issuer identity, or empty while no binding has been loaded yet
     */
    public Optional<BoundIssuerIdentity> identity() {
        return Optional.ofNullable(identity.get());
    }

    @Override
    public void close() {
        stopPolling();
        closeReader();
    }

    private PeriodicMaterialReloader.Loaded<BoundIssuerIdentity> load() {
        var currentReader = acquireReader();
        var bound = currentReader.read().orElseThrow(BoundIssuerIdentityHolder::unboundException);
        return new PeriodicMaterialReloader.Loaded<>(bound, bound.did());
    }

    /**
     * Builds the reader (outside {@link #readerLock}, since construction may be slow), keeping it only if the
     * holder has not closed in the meantime; a reader built after {@link #close()} ran is closed right away
     * instead of being kept, so every poll after that builds and discards one.
     */
    private BoundIssuerIdentityReader acquireReader() {
        var existing = reader.get();
        if (existing != null) {
            return existing;
        }
        var created = readerFactory.get();
        synchronized (readerLock) {
            if (!readerClosed) {
                reader.set(created);
                return created;
            }
        }
        closeQuietly(created);
        throw unboundException();
    }

    private static IllegalStateException unboundException() {
        return new IllegalStateException(NAME + ": the issuer identity is not bound yet");
    }

    private void applyAndStop(BoundIssuerIdentity bound) {
        identity.set(bound);
        closeReader();
        stopPolling();
    }

    private void stopPolling() {
        stopRequested = true;
        var current = reloader.get();
        if (current != null) {
            current.close();
        }
    }

    private void closeReader() {
        synchronized (readerLock) {
            readerClosed = true;
            closeQuietly(reader.get());
            reader.set(null);
        }
    }

    private void closeQuietly(BoundIssuerIdentityReader candidate) {
        if (candidate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                monitor.warning(NAME + ": failed to close the bound-identity reader connection", e);
            }
        }
    }
}
