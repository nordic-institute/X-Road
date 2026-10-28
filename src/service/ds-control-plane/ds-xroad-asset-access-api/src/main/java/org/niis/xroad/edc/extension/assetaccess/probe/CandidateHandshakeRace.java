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

import jakarta.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest.Candidate;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Orders candidate provider Security Servers by a parallel TLS probe race. Every candidate is probed at once through
 * a client derived from the shared X-Road OkHttp client, so probes use the connector TLS trust and the winner's open
 * connection stays in the shared pool for the catalog call that follows.
 *
 * <p>The first probe to hold an established connection wins and the others are cancelled. The winner's exchange is
 * allowed to finish, without reading its status, so that the connection returns to the pool before the order is
 * handed out. A probe that fails before the race is decided puts its server in cooldown; probes cancelled by the
 * winner are not failures.
 *
 * <p>The order is the winner, then the cancelled candidates, then the failed ones; each of the last two groups
 * is shuffled. It is handed out on a dedicated completion executor, so the acquisition that follows does not run
 * on an OkHttp callback thread.
 */
public class CandidateHandshakeRace {

    static final String COMPLETION_THREAD_NAME = "CandidateHandshakeRace-completion";

    private static final String WELL_KNOWN_SEGMENT = ".well-known";
    private static final String VERSION_SEGMENT = "dspace-version";

    private final OkHttpClient probeClient;
    private final CooldownTracker cooldownTracker;
    private final ExecutorService completionExecutor;
    private final Monitor monitor;

    /**
     * @param client                   the shared X-Road OkHttp client the probe client is derived from
     * @param cooldownTracker          marks servers whose probe failed
     * @param executorInstrumentation  instruments the executor the order is handed out on
     * @param probeDeadline            time limit for one probe, from start until its response completes
     */
    public CandidateHandshakeRace(OkHttpClient client,
                                  CooldownTracker cooldownTracker,
                                  ExecutorInstrumentation executorInstrumentation,
                                  Duration probeDeadline,
                                  Monitor monitor) {
        this.probeClient = client.newBuilder()
                .callTimeout(probeDeadline)
                .connectTimeout(probeDeadline)
                .readTimeout(probeDeadline)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        this.cooldownTracker = cooldownTracker;
        this.completionExecutor = executorInstrumentation.instrument(
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(COMPLETION_THREAD_NAME + "-", 1).factory()),
                COMPLETION_THREAD_NAME);
        this.monitor = monitor;
    }

    /**
     * Shuts down the completion executor; an order decided afterwards is handed out on the deciding thread.
     */
    public void stop() {
        completionExecutor.shutdown();
    }

    /**
     * Probes all candidates and returns them in the order they should be tried.
     */
    public CompletableFuture<List<Candidate>> order(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        var round = new Round(candidates);
        round.start();
        return round.result;
    }

    /**
     * The unauthenticated version endpoint of the participant context a counter-party address points at: the
     * address {@code <DSP base>/<participant context>/<profile>} with its last segment replaced.
     *
     * @return the probe URL, or {@code null} when the address is not an HTTP(S) URL with a participant context
     */
    @Nullable
    static HttpUrl probeUrl(String counterPartyAddress) {
        var address = HttpUrl.parse(counterPartyAddress);
        if (address == null || address.pathSize() < 2) {
            return null;
        }
        return address.newBuilder()
                .removePathSegment(address.pathSize() - 1)
                .addPathSegment(WELL_KNOWN_SEGMENT)
                .addPathSegment(VERSION_SEGMENT)
                .query(null)
                .fragment(null)
                .build();
    }

    private enum ProbeState { PENDING, WINNER, FAILED, CANCELLED }

    private static final class Probe {
        private final Candidate candidate;
        private Call call;
        private ProbeState state = ProbeState.PENDING;

        private Probe(Candidate candidate) {
            this.candidate = candidate;
        }
    }

    private final class Round {
        private final List<Probe> probes;
        private final CompletableFuture<List<Candidate>> result = new CompletableFuture<>();
        private boolean decided;
        private List<Candidate> decidedOrder;

        private Round(List<Candidate> candidates) {
            this.probes = candidates.stream().map(Probe::new).toList();
        }

        private void start() {
            for (var probe : probes) {
                var url = probeUrl(probe.candidate.counterPartyAddress());
                if (url == null) {
                    recordFailure(probe, new IllegalArgumentException(
                            "Not a probeable counter-party address: " + probe.candidate.counterPartyAddress()));
                    continue;
                }
                var listener = new EventListener() {
                    @Override
                    public void connectionAcquired(Call call, Connection connection) {
                        declareWinner(probe);
                    }
                };
                var request = new Request.Builder().url(url).header("Accept", "application/json").get().build();
                probe.call = probeClient.newBuilder().eventListener(listener).build().newCall(request);
            }
            for (var probe : probes) {
                if (probe.call != null && !isDecided()) {
                    probe.call.enqueue(new ProbeCallback(probe));
                }
            }
        }

        private synchronized boolean isDecided() {
            return decided;
        }

        private void declareWinner(Probe probe) {
            List<Probe> cancelled;
            synchronized (this) {
                if (decided || probe.state != ProbeState.PENDING) {
                    return;
                }
                probe.state = ProbeState.WINNER;
                decided = true;
                cancelled = probes.stream().filter(other -> other.state == ProbeState.PENDING).toList();
                cancelled.forEach(other -> other.state = ProbeState.CANCELLED);
                decidedOrder = orderAround(probe);
            }
            monitor.debug("Probe won the race: counterPartyAddress=%s".formatted(probe.candidate.counterPartyAddress()));
            cancelled.forEach(other -> other.call.cancel());
        }

        private void recordFailure(Probe probe, Throwable cause) {
            synchronized (this) {
                if (decided || probe.state != ProbeState.PENDING) {
                    return;
                }
                probe.state = ProbeState.FAILED;
            }
            cooldownTracker.markFailed(probe.candidate.counterPartyAddress());
            monitor.warning("Probe failed: counterPartyAddress=%s".formatted(probe.candidate.counterPartyAddress()), cause);
            completeWhenAllFailed();
        }

        private void completeWhenAllFailed() {
            synchronized (this) {
                if (decided || probes.stream().anyMatch(probe -> probe.state == ProbeState.PENDING)) {
                    return;
                }
                decided = true;
                decidedOrder = orderAround(null);
            }
            handOut(decidedOrder);
        }

        private void exchangeEnded(Probe probe) {
            synchronized (this) {
                if (probe.state != ProbeState.WINNER) {
                    return;
                }
            }
            handOut(decidedOrder);
        }

        private void handOut(List<Candidate> order) {
            try {
                completionExecutor.execute(() -> result.complete(order));
            } catch (RejectedExecutionException _) {
                result.complete(order);
            }
        }

        private List<Candidate> orderAround(@Nullable Probe winner) {
            var ordered = new ArrayList<Candidate>();
            if (winner != null) {
                ordered.add(winner.candidate);
            }
            ordered.addAll(shuffled(ProbeState.CANCELLED));
            ordered.addAll(shuffled(ProbeState.FAILED));
            return List.copyOf(ordered);
        }

        private List<Candidate> shuffled(ProbeState state) {
            var group = new ArrayList<>(probes.stream()
                    .filter(probe -> probe.state == state)
                    .map(probe -> probe.candidate)
                    .toList());
            Collections.shuffle(group);
            return group;
        }

        private final class ProbeCallback implements Callback {
            private final Probe probe;

            private ProbeCallback(Probe probe) {
                this.probe = probe;
            }

            @Override
            public void onFailure(Call call, IOException e) {
                recordFailure(probe, e);
                exchangeEnded(probe);
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
                exchangeEnded(probe);
            }
        }
    }
}
