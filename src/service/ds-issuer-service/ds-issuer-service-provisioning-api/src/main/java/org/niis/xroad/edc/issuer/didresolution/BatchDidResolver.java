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
package org.niis.xroad.edc.issuer.didresolution;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Endpoint;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Reason;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Resolution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * Resolves a batch of DIDs with bounded concurrency. Each DID is resolved entirely on one worker thread, which the
 * resolver's per-thread record of the last request depends on.
 */
@RequiredArgsConstructor
class BatchDidResolver {

    static final int MAX_CONCURRENT_RESOLUTIONS = 16;

    private final SubmittedDidResolver resolver;
    private final Monitor monitor;

    /**
     * @param dids   the DIDs to resolve
     * @param budget the time allowed for the whole batch, {@code null} for no limit
     * @return one resolution per DID, in the order of {@code dids}; a well-formed DID that did not finish within the
     * budget, or whose endpoint already failed to answer, is a fetch failure
     */
    List<Resolution> resolveAll(List<String> dids, @Nullable Duration budget) {
        if (dids.isEmpty()) {
            return List.of();
        }
        if (budget != null && !budget.isPositive()) {
            return dids.stream().map(BatchDidResolver::unfinished).toList();
        }
        var deadline = budget == null ? null : System.nanoTime() + budget.toNanos();
        Set<Endpoint> failedEndpoints = ConcurrentHashMap.newKeySet();
        var inFlight = new Semaphore(MAX_CONCURRENT_RESOLUTIONS);
        var futures = new ArrayList<Future<Resolution>>(dids.size());
        var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("did-resolution-", 0).factory());
        try {
            for (var did : dids) {
                if (!admit(inFlight, deadline)) {
                    break;
                }
                futures.add(executor.submit(() -> resolveUnlessEndpointFailed(did, failedEndpoints, inFlight)));
            }
            return IntStream.range(0, dids.size())
                    .mapToObj(i -> i < futures.size() ? resultOf(futures.get(i), dids.get(i), deadline) : unfinished(dids.get(i)))
                    .toList();
        } finally {
            executor.shutdownNow();
            executor.close();
        }
    }

    private static boolean admit(Semaphore inFlight, @Nullable Long deadline) {
        try {
            if (deadline == null) {
                inFlight.acquire();
                return true;
            }
            return inFlight.tryAcquire(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Resolution resolveUnlessEndpointFailed(String did, Set<Endpoint> failedEndpoints, Semaphore inFlight) {
        try {
            var endpoint = SubmittedDidResolver.fetchEndpoint(did);
            if (endpoint.isPresent() && failedEndpoints.contains(endpoint.get())) {
                return fetchFailure(did);
            }
            var resolution = resolver.resolve(did);
            if (endpoint.isPresent() && resolution.requestFailed()) {
                failedEndpoints.add(endpoint.get());
            }
            return resolution;
        } finally {
            inFlight.release();
        }
    }

    private Resolution resultOf(Future<Resolution> future, String did, @Nullable Long deadline) {
        try {
            return deadline == null
                    ? future.get()
                    : future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return unfinished(did);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            monitor.severe("Resolving DID %s failed unexpectedly".formatted(did), e.getCause());
            return fetchFailure(did);
        } catch (TimeoutException | CancellationException _) {
            future.cancel(true);
            return unfinished(did);
        }
    }

    private static Resolution unfinished(String did) {
        return SubmittedDidResolver.fetchEndpoint(did).isEmpty()
                ? Resolution.notResolved(did, Reason.MALFORMED_DID)
                : fetchFailure(did);
    }

    private static Resolution fetchFailure(String did) {
        return Resolution.notResolved(did, Reason.FETCH_FAILURE);
    }
}
