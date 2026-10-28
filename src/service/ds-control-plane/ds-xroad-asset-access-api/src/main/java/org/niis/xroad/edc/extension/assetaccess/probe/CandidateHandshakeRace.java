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
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest.Candidate;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orders candidate provider Security Servers by a TLS probe race. Every candidate is probed through a client derived
 * from the shared X-Road OkHttp client, so probes use the connector TLS trust and the winner's open connection stays
 * in the shared pool for the catalog call that follows. Probes run concurrently: they start together, or one
 * stagger apart when a stagger is set.
 *
 * <p>The first probe to hold an established connection wins and the others are cancelled. The winner's exchange is
 * allowed to finish, without reading its status, so that the connection returns to the pool before the order is
 * handed out. A probe that fails before the race is decided puts its server in cooldown; probes cancelled by the
 * winner are not failures.
 *
 * <p>The order is the winner, then the cancelled candidates, then the failed ones; each of the last two groups
 * is shuffled. It is handed out on a dedicated completion executor, so the acquisition that follows does not run
 * on an OkHttp callback thread.
 *
 * <p>Each completed handshake updates the server's moving average of connection setup time, TCP connect plus TLS
 * handshake ({@link HandshakeTimes}); a failed probe drops the server's average. With a probe stagger of zero all
 * probes start together and the averages do not affect the order. With a positive stagger the probes start one
 * stagger apart, servers with the lowest average first and servers without a recorded average last, so a server that
 * usually answers first is probed before the others and may win before they are started. A probe that fails before
 * the race is decided starts the next waiting probe at once instead of at that probe's slot.
 */
public class CandidateHandshakeRace {

    static final String COMPLETION_THREAD_NAME = "CandidateHandshakeRace-completion";
    static final String STAGGER_THREAD_NAME = "CandidateHandshakeRace-stagger";

    private static final String WELL_KNOWN_SEGMENT = ".well-known";
    private static final String VERSION_SEGMENT = "dspace-version";

    private final OkHttpClient probeClient;
    private final CooldownTracker cooldownTracker;
    private final ExecutorService completionExecutor;
    private final ScheduledExecutorService staggerExecutor;
    private final Duration probeStagger;
    private final HandshakeTimes handshakeTimes;
    private final Monitor monitor;

    /**
     * @param client                   the shared X-Road OkHttp client the probe client is derived from
     * @param cooldownTracker          marks servers whose probe failed
     * @param executorInstrumentation  instruments the completion executor the order is handed out on and the
     *                                 stagger scheduler that starts the delayed probes
     * @param probeDeadline            time limit for one probe, from start until its response completes
     * @param probeStagger             delay between the starts of consecutive probes; zero starts all probes together
     * @param smoothingFactor          weight of the newest sample in a server's average connection setup time, above 0
     *                                 and at most 1
     */
    public CandidateHandshakeRace(OkHttpClient client,
                                  CooldownTracker cooldownTracker,
                                  ExecutorInstrumentation executorInstrumentation,
                                  Duration probeDeadline,
                                  Duration probeStagger,
                                  double smoothingFactor,
                                  Monitor monitor) {
        this(client, cooldownTracker, executorInstrumentation, probeDeadline, probeStagger,
                new HandshakeTimes(smoothingFactor), monitor);
    }

    CandidateHandshakeRace(OkHttpClient client,
                           CooldownTracker cooldownTracker,
                           ExecutorInstrumentation executorInstrumentation,
                           Duration probeDeadline,
                           Duration probeStagger,
                           HandshakeTimes handshakeTimes,
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
        this.staggerExecutor = executorInstrumentation.instrument(
                Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name(STAGGER_THREAD_NAME + "-", 1).daemon().factory()),
                STAGGER_THREAD_NAME);
        this.probeStagger = probeStagger;
        this.handshakeTimes = handshakeTimes;
        this.monitor = monitor;
    }

    /**
     * @return the delay between the starts of consecutive probes; zero when all probes start together
     */
    public Duration probeStagger() {
        return probeStagger;
    }

    /**
     * Shuts down the executors. Probe starts already scheduled still run, so a round in flight completes. A round
     * begun afterwards starts its probes at once, and an order decided afterwards is handed out on the deciding thread.
     */
    public void stop() {
        staggerExecutor.shutdown();
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
        private boolean started;

        private Probe(Candidate candidate) {
            this.candidate = candidate;
        }
    }

    private final class Round {
        private final List<Probe> probes;
        private final CompletableFuture<List<Candidate>> result = new CompletableFuture<>();
        private boolean decided;
        private List<Candidate> decidedOrder;
        private List<Probe> startOrder = List.of();

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
                    private volatile boolean connectStarted;
                    private volatile long connectStartNanos;

                    @Override
                    public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
                        if (!connectStarted) {
                            connectStartNanos = System.nanoTime();
                            connectStarted = true;
                        }
                    }

                    @Override
                    public void secureConnectEnd(Call call, Handshake handshake) {
                        handshakeTimes.record(probe.candidate.counterPartyAddress(),
                                Duration.ofNanos(System.nanoTime() - connectStartNanos));
                    }

                    @Override
                    public void connectionAcquired(Call call, Connection connection) {
                        declareWinner(probe);
                    }
                };
                var request = new Request.Builder().url(url).header("Accept", "application/json").get().build();
                probe.call = probeClient.newBuilder().eventListener(listener).build().newCall(request);
            }
            startProbes();
        }

        private void startProbes() {
            var order = startOrder();
            synchronized (this) {
                startOrder = order;
            }
            for (var position = 0; position < order.size(); position++) {
                var probe = order.get(position);
                if (position == 0 || probeStagger.isZero()) {
                    startProbe(probe);
                } else {
                    startLater(probe, probeStagger.multipliedBy(position).toNanos());
                }
            }
        }

        private void startNextWaiting() {
            Probe next;
            synchronized (this) {
                next = startOrder.stream().filter(probe -> !probe.started).findFirst().orElse(null);
            }
            if (next != null) {
                startProbe(next);
            }
        }

        private void startLater(Probe probe, long delayNanos) {
            try {
                staggerExecutor.schedule(() -> startProbe(probe), delayNanos, TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException _) {
                startProbe(probe);
            }
        }

        private void startProbe(Probe probe) {
            synchronized (this) {
                if (decided || probe.started) {
                    return;
                }
                probe.started = true;
            }
            probe.call.enqueue(new ProbeCallback(probe));
        }

        private List<Probe> startOrder() {
            var startable = probes.stream().filter(probe -> probe.call != null).toList();
            if (probeStagger.isZero()) {
                return startable;
            }
            var averages = new HashMap<Probe, Duration>();
            startable.forEach(probe -> handshakeTimes.average(probe.candidate.counterPartyAddress())
                    .ifPresent(average -> averages.put(probe, average)));
            return startable.stream()
                    .sorted(Comparator.comparing((Probe probe) -> !averages.containsKey(probe))
                            .thenComparing(probe -> averages.getOrDefault(probe, Duration.ZERO)))
                    .toList();
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
            handshakeTimes.forget(probe.candidate.counterPartyAddress());
            monitor.warning("Probe failed: counterPartyAddress=%s".formatted(probe.candidate.counterPartyAddress()), cause);
            startNextWaiting();
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
