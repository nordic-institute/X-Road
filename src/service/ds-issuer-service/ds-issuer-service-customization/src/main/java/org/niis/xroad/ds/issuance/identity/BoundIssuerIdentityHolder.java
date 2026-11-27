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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Holds the issuer identity the Central Server operator bound, once bound. {@link #start()} tries {@link
 * BoundIssuerIdentityReader#read()} once synchronously, so a node that starts after binding is ready as soon
 * as {@link #start()} returns; only a failed first attempt falls back to polling, at {@link
 * PeriodicMaterialReloader#DEFAULT_PENDING_INTERVAL}, until the first bound result. Once bound, the holder
 * keeps its reader open and rechecks the binding on its own schedule, every {@link
 * #DEFAULT_POST_BIND_RECHECK_INTERVAL}: a recheck that reads the bound identity sets it, one that finds the
 * row removed clears {@link #identity()} and logs one severe line, and one that fails to read keeps the
 * previously bound identity and logs a warning. The reader opened while pending, and kept open
 * afterwards, is closed only when the holder itself closes.
 *
 * <p>A reader that fails to construct (the Central Server database is unreachable) or fails to read is
 * treated the same as an unbound result during the pending phase: the holder stays unbound and the next poll
 * tries again, reusing the same reader once one has been built successfully.
 *
 * <p>{@link #scheduleRecheck()} stores the recheck executor after it creates it, and {@link #close()} sets
 * its stop request before it reads that executor; each checks the other's volatile state after writing its
 * own, so whichever runs last shuts the executor down. The pending-phase reloader {@link #start()} schedules
 * can call {@link #onFirstLoad(BoundIssuerIdentity)} before {@link #start()} has stored it: {@link
 * #onFirstLoad(BoundIssuerIdentity)} sets a volatile flag and then closes any stored reloader, and {@link
 * #start()} stores the reloader and then closes it if that flag or the stop request is set, so whichever runs
 * last closes it.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class BoundIssuerIdentityHolder implements AutoCloseable {

    static final String NAME = "bound-issuer-identity";

    /** How often the holder rechecks its binding once bound. */
    static final Duration DEFAULT_POST_BIND_RECHECK_INTERVAL = Duration.ofSeconds(60);

    private final Supplier<BoundIssuerIdentityReader> readerFactory;
    private final Monitor monitor;
    private final Duration pendingInterval;
    private final Duration postBindInterval;

    /** Test seam: runs in {@link #start()} between scheduling the pending reloader and storing it. */
    private Runnable betweenScheduleAndStore = () -> { };

    private final Object readerLock = new Object();

    private final AtomicReference<BoundIssuerIdentity> identity = new AtomicReference<>();
    private final AtomicReference<PeriodicMaterialReloader<BoundIssuerIdentity>> reloader = new AtomicReference<>();
    private final AtomicReference<ScheduledExecutorService> recheckExecutor = new AtomicReference<>();
    private final AtomicReference<BoundIssuerIdentityReader> reader = new AtomicReference<>();
    private volatile boolean stopRequested;
    private volatile boolean firstLoadDone;
    private boolean readerClosed;

    public BoundIssuerIdentityHolder(Supplier<BoundIssuerIdentityReader> readerFactory, Monitor monitor) {
        this(readerFactory, monitor, PeriodicMaterialReloader.DEFAULT_PENDING_INTERVAL, DEFAULT_POST_BIND_RECHECK_INTERVAL);
    }

    BoundIssuerIdentityHolder(Supplier<BoundIssuerIdentityReader> readerFactory, Monitor monitor, Duration pendingInterval,
                               Duration postBindInterval, Runnable betweenScheduleAndStore) {
        this(readerFactory, monitor, pendingInterval, postBindInterval);
        this.betweenScheduleAndStore = betweenScheduleAndStore;
    }

    /**
     * Attempts one synchronous load before scheduling any polling, so a node that starts after binding is
     * ready as soon as this call returns, with no poll interval to wait out. Never throws: a first attempt
     * that fails, because the identity is not bound yet or the reader cannot be built or read, is logged and
     * falls back to polling at {@link #pendingInterval} like every later attempt.
     */
    public void start() {
        try {
            onFirstLoad(load().material());
            return;
        } catch (IssuerIdentityNotBoundException _) {
            monitor.warning(NAME + ": no binding yet; starting degraded and polling");
        } catch (Exception e) {
            monitor.warning(NAME + ": first load failed; starting degraded and polling", e);
        }
        var scheduled = PeriodicMaterialReloader.scheduleUntilFirstLoad(NAME, pendingInterval, pendingInterval, 1, Duration.ZERO,
                this::load, this::onFirstLoad, monitor, null);
        betweenScheduleAndStore.run();
        reloader.set(scheduled);
        if (stopRequested || firstLoadDone) {
            scheduled.close();
        }
    }

    /**
     * @return the bound issuer identity, or empty while no binding has been loaded yet, or once a post-bind
     *     recheck has found the binding removed
     */
    public Optional<BoundIssuerIdentity> identity() {
        return Optional.ofNullable(identity.get());
    }

    @Override
    public void close() {
        stopRequested = true;
        closePendingReloader();
        stopRecheck();
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
     * instead of being kept, so every poll or recheck after that builds and discards one.
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

    private static IssuerIdentityNotBoundException unboundException() {
        return new IssuerIdentityNotBoundException(NAME + ": the issuer identity is not bound yet");
    }

    /**
     * Distinguishes "not bound yet" from every other first-load failure, so {@link #start()} can log the two
     * separately while still being an {@link IllegalStateException} for any caller that catches that type.
     */
    static final class IssuerIdentityNotBoundException extends IllegalStateException {
        IssuerIdentityNotBoundException(String message) {
            super(message);
        }
    }

    /**
     * Called whether {@link #start()} resolves synchronously or the pending-phase reloader's first successful
     * poll calls it back, always setting {@code bound} as the identity. The one-time setup that follows it -
     * closing the now-unneeded pending reloader and starting the post-bind recheck schedule that keeps the
     * reader open for reuse - runs only on the first call, guarded by {@link #firstLoadDone}.
     */
    private void onFirstLoad(BoundIssuerIdentity bound) {
        identity.set(bound);
        if (firstLoadDone) {
            return;
        }
        firstLoadDone = true;
        closePendingReloader();
        scheduleRecheck();
    }

    private void closePendingReloader() {
        var current = reloader.get();
        if (current != null) {
            current.close();
        }
    }

    /**
     * Starts the holder's own recheck schedule, a dedicated single-thread executor rather than another {@link
     * PeriodicMaterialReloader}: a post-bind cycle must tell "removed" (clear the identity) apart from
     * "unreadable" (keep it), a distinction the reloader's fingerprint-change model does not make.
     */
    private void scheduleRecheck() {
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "xrd-" + NAME + "-recheck");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::recheck, postBindInterval.toMillis(), postBindInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        recheckExecutor.set(executor);
        if (stopRequested) {
            executor.shutdownNow();
        }
    }

    private void stopRecheck() {
        var current = recheckExecutor.get();
        if (current != null) {
            current.shutdownNow();
        }
    }

    /**
     * One post-bind recheck cycle: a present result confirms or updates the bound identity, an empty result
     * clears it and logs one severe line for the bound-to-empty change, and anything the read throws,
     * including an {@link Error}, leaves the identity as is and logs a warning instead of ending the periodic
     * recheck for good.
     */
    private void recheck() {
        try {
            var result = acquireReader().read();
            if (result.isPresent()) {
                identity.set(result.get());
            } else if (identity.getAndSet(null) != null) {
                monitor.severe(NAME + ": the bound issuer was removed in the Central Server; issuance has stopped");
            }
        } catch (Throwable e) {
            monitor.warning(NAME + ": post-bind recheck failed; keeping the bound identity", e);
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
