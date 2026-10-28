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

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest.Candidate;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class CandidateHandshakeRaceTest {

    private static final String CONTEXT = "DEV:COM:222";
    private static final String DSP_PATH = "/api/dsp/" + CONTEXT + "/http-dsp-profile-2025-1";
    private static final String VERSION_PATH = "/api/dsp/" + CONTEXT + "/.well-known/dspace-version";
    private static final String FAST_HOST = "fast.xroad.test";
    private static final String SLOW_HOST = "slow.xroad.test";
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final long SLOW_HANDSHAKE_MILLIS = 3000;
    private static final double SMOOTHING_FACTOR = 0.3;
    private static final String RETRY_HOST = "retry.xroad.test";
    private static final InetAddress UNREACHABLE_ADDRESS = InetAddress.ofLiteral("127.0.0.2");
    private static final long FAILED_CONNECT_ATTEMPT_MILLIS = 600;
    private static final Duration MINIMUM_RETRIED_SAMPLE = Duration.ofMillis(200);

    private static TestCa trustedCa;
    private static TestCa untrustedCa;

    private final List<TlsServer> servers = new ArrayList<>();
    private final List<CandidateHandshakeRace> races = new ArrayList<>();
    private final CooldownTracker cooldownTracker = new CooldownTracker(Duration.ofSeconds(180));
    private OkHttpClient client;

    @BeforeAll
    static void createCas() throws Exception {
        trustedCa = selfSignedCa("Connector TLS CA");
        untrustedCa = selfSignedCa("Unapproved CA");
    }

    @AfterEach
    void tearDown() {
        races.forEach(CandidateHandshakeRace::stop);
        servers.forEach(TlsServer::close);
        if (client != null) {
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
        }
    }

    @Test
    void theFasterHandshakeIsOrderedFirst() throws Exception {
        var slow = startServer(SLOW_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var fast = startServer(FAST_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("slow", slow), candidate("fast", fast))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).first().isEqualTo("fast");
        assertThat(fast.handshakesCompleted()).hasValue(1);
        assertThat(slow.handshakesCompleted()).hasValue(0);
    }

    @Test
    void losingProbesAreCancelledOnceTheWinnerCompletesItsHandshake() throws Exception {
        var slow = startServer(SLOW_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var fast = startServer(FAST_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("slow", slow), candidate("fast", fast))).get(10, TimeUnit.SECONDS);

        await().atMost(8, TimeUnit.SECONDS).until(() -> slow.handshakesAbandoned().get() == 1);
        assertThat(slow.handshakesCompleted()).hasValue(0);
        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("fast", "slow");
        assertThat(cooldownTracker.isCoolingDown(slow.address())).isFalse();
    }

    @Test
    void theCatalogCallReusesTheWinnersPooledConnection() throws Exception {
        var fast = startServer(FAST_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        race.order(List.of(candidate("fast", fast))).get(10, TimeUnit.SECONDS);
        var catalogRequest = new Request.Builder().url("https://" + FAST_HOST + ":" + fast.port() + "/api/dsp/catalog/request").build();
        try (var response = client.newCall(catalogRequest).execute()) {
            assertThat(response.code()).isEqualTo(404);
        }

        assertThat(fast.handshakesCompleted()).hasValue(1);
        assertThat(fast.requests()).hasSize(2);
    }

    @Test
    void aProbeFailureMarksTheServerInCooldownAndFailedServersComeAfterAllWinners() throws Exception {
        var fast = startServer(FAST_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var refused = unusedAddress(FAST_HOST);
        var unapproved = startServer("unapproved.xroad.test", untrustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(
                new Candidate("refused", refused),
                candidate("unapproved", unapproved),
                candidate("fast", fast))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).first().isEqualTo("fast");
        assertThat(order).extracting(Candidate::counterPartyId).containsExactlyInAnyOrder("fast", "refused", "unapproved");
        assertThat(cooldownTracker.isCoolingDown(refused)).isTrue();
        assertThat(cooldownTracker.isCoolingDown(unapproved.address())).isTrue();
        assertThat(cooldownTracker.isCoolingDown(fast.address())).isFalse();
    }

    @Test
    void probeFailedServersAreShuffled() throws Exception {
        var winner = startServer(FAST_HOST, trustedCa, 300);
        var failed = List.of(unusedAddress(FAST_HOST), unusedAddress(FAST_HOST), unusedAddress(FAST_HOST),
                unusedAddress(FAST_HOST));
        var candidates = new ArrayList<Candidate>();
        candidates.add(candidate("winner", winner));
        for (var i = 0; i < failed.size(); i++) {
            candidates.add(new Candidate("failed-" + i, failed.get(i)));
        }
        var race = race(DEADLINE);

        Set<List<String>> failedOrders = new HashSet<>();
        for (var run = 0; run < 10; run++) {
            client.connectionPool().evictAll();
            var order = race.order(candidates).get(10, TimeUnit.SECONDS);
            assertThat(order.getFirst().counterPartyId()).isEqualTo("winner");
            failedOrders.add(order.stream().skip(1).map(Candidate::counterPartyId).toList());
        }

        assertThat(failed).allMatch(cooldownTracker::isCoolingDown);
        assertThat(winner.handshakesCompleted()).hasValue(10);
        assertThat(failedOrders.size()).isGreaterThan(1);
    }

    @Test
    void aProbeThatMissesTheDeadlineIsAFailure() throws Exception {
        var stalled = startServer(SLOW_HOST, trustedCa, 5000);
        var race = race(Duration.ofMillis(400));

        var order = race.order(List.of(candidate("stalled", stalled))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("stalled");
        assertThat(cooldownTracker.isCoolingDown(stalled.address())).isTrue();
    }

    @Test
    void whenEveryProbeFailsEveryServerIsInCooldownAndStillReturned() throws Exception {
        var first = unusedAddress(FAST_HOST);
        var second = unusedAddress(SLOW_HOST);
        var race = race(DEADLINE);

        var order = race.order(List.of(new Candidate("first", first), new Candidate("second", second)))
                .get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactlyInAnyOrder("first", "second");
        assertThat(cooldownTracker.isCoolingDown(first)).isTrue();
        assertThat(cooldownTracker.isCoolingDown(second)).isTrue();
    }

    @Test
    void anEmptyCandidateListGivesAnEmptyOrderAtOnce() throws Exception {
        var order = race(DEADLINE).order(List.of());

        assertThat(order).isCompletedWithValue(List.of());
    }

    @Test
    void probesTrustOnlyTheConnectorCaAndDialUnderTheExactHostName() throws Exception {
        var trusted = startServer(FAST_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("trusted", trusted))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("trusted");
        assertThat(trusted.hostHeaders()).containsExactly(FAST_HOST + ":" + trusted.port());
        assertThat(cooldownTracker.isCoolingDown(trusted.address())).isFalse();
    }

    @Test
    void aCertificateForAnotherHostNameFailsTheProbe() throws Exception {
        var wrongName = startServer("other.xroad.test", trustedCa, 0, "unrelated.xroad.test");
        var race = race(DEADLINE);

        var order = race.order(List.of(new Candidate("wrong-name", wrongName.address()))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("wrong-name");
        assertThat(cooldownTracker.isCoolingDown(wrongName.address())).isTrue();
    }

    @Test
    void theOrderIsHandedOutOnTheCompletionExecutor() throws Exception {
        var provider = startServer(FAST_HOST, trustedCa, 0);
        provider.holdHandshakes();
        var race = race(DEADLINE);

        var completingThread = race.order(List.of(candidate("provider", provider)))
                .thenApply(_ -> Thread.currentThread().getName());
        provider.releaseHandshakes();

        assertThat(completingThread.get(10, TimeUnit.SECONDS)).startsWith(CandidateHandshakeRace.COMPLETION_THREAD_NAME);
    }

    @Test
    void everyCompletedHandshakeUpdatesTheServersAverage() throws Exception {
        var fast = startServer(FAST_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);

        race.order(List.of(candidate("fast", fast))).get(10, TimeUnit.SECONDS);

        assertThat(handshakeTimes.average(fast.address()).orElseThrow()).isPositive().isLessThan(DEADLINE);
    }

    @Test
    void aServerWhoseProbeFailedStartsAmongTheServersWithoutAnAverageOnceItIsProbedAgain() throws Exception {
        var stagger = Duration.ofMillis(250);
        var flaky = startServer("flaky.xroad.test", untrustedCa, 0);
        var known = startServer("known.xroad.test", trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(flaky.address(), Duration.ofMillis(1));
        handshakeTimes.record(known.address(), Duration.ofMillis(400));
        var race = race(DEADLINE, stagger, handshakeTimes);
        var candidates = List.of(candidate("flaky", flaky), candidate("known", known));
        known.holdHandshakes();

        var firstOrder = race.order(candidates);
        await().atMost(8, TimeUnit.SECONDS).until(() -> handshakeTimes.average(flaky.address()).isEmpty());
        known.releaseHandshakes();

        assertThat(firstOrder.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId)
                .containsExactly("known", "flaky");
        assertThat(cooldownTracker.isCoolingDown(flaky.address())).isTrue();
        client.connectionPool().evictAll();
        flaky.holdHandshakes();
        known.holdHandshakes();
        var secondOrder = race.order(candidates);
        await().atMost(8, TimeUnit.SECONDS).until(() -> flaky.acceptNanos().size() == 2 && known.acceptNanos().size() == 2);
        flaky.releaseHandshakes();
        known.releaseHandshakes();

        assertThat(secondOrder.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).first().isEqualTo("known");
        assertThat(flaky.acceptNanos().get(1) - known.acceptNanos().get(1)).isGreaterThanOrEqualTo(minimumGapNanos(stagger));
    }

    @Test
    void aStaggeredRaceStoppedMidRoundStillStartsItsPendingProbesAndCompletes() throws Exception {
        var failing = startServer("failing.xroad.test", untrustedCa, 0);
        var healthy = startServer("healthy.xroad.test", trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(failing.address(), Duration.ofMillis(1));
        var race = race(DEADLINE, Duration.ofMillis(250), handshakeTimes);

        var order = race.order(List.of(candidate("healthy", healthy), candidate("failing", failing)));
        race.stop();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("healthy", "failing");
        assertThat(healthy.handshakesCompleted()).hasValue(1);
    }

    @Test
    void aStaggeredRaceBegunAfterStopStillCompletes() throws Exception {
        var first = startServer("first.xroad.test", trustedCa, 0);
        var second = startServer("second.xroad.test", trustedCa, 0);
        var race = race(DEADLINE, Duration.ofMillis(250), new HandshakeTimes(SMOOTHING_FACTOR));
        race.stop();

        var order = race.order(List.of(candidate("first", first), candidate("second", second)));

        assertThat(order.get(10, TimeUnit.SECONDS)).hasSize(2);
    }

    @Test
    void aStaggeredRaceBegunAfterStopStartsAllProbesAtOnceAndCompletesWhenTheFirstStartedProbeFails() throws Exception {
        var failing = startServer("failing.xroad.test", untrustedCa, 0);
        var healthy = startServer("healthy.xroad.test", trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(failing.address(), Duration.ofMillis(1));
        failing.holdHandshakes();
        healthy.holdHandshakes();
        var race = race(Duration.ofSeconds(30), Duration.ofSeconds(4), handshakeTimes);
        race.stop();

        var order = race.order(List.of(candidate("healthy", healthy), candidate("failing", failing)));
        await().atMost(8, TimeUnit.SECONDS).until(() -> !failing.acceptNanos().isEmpty() && !healthy.acceptNanos().isEmpty());
        failing.releaseHandshakes();
        await().atMost(8, TimeUnit.SECONDS).until(() -> cooldownTracker.isCoolingDown(failing.address()));
        healthy.releaseHandshakes();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("healthy", "failing");
    }

    @Test
    void aProbeThatFailsBeforeAWinnerStartsTheNextWaitingProbeAtOnce() throws Exception {
        var stagger = Duration.ofSeconds(1);
        var failing = startServer("failing.xroad.test", untrustedCa, 0);
        var next = startServer("next.xroad.test", trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(failing.address(), Duration.ofMillis(1));
        handshakeTimes.record(next.address(), Duration.ofMillis(400));
        next.holdHandshakes();
        var race = race(Duration.ofSeconds(30), stagger, handshakeTimes);
        var begin = System.nanoTime();

        var order = race.order(List.of(candidate("next", next), candidate("failing", failing)));
        await().atMost(8, TimeUnit.SECONDS).until(() -> !next.acceptNanos().isEmpty());

        assertThat(next.acceptNanos().getFirst() - begin).isLessThan(stagger.toNanos());
        await().during(stagger.plusMillis(250)).atMost(stagger.plusSeconds(5)).until(() -> next.acceptNanos().size() == 1);
        assertThat(order).isNotDone();
        next.releaseHandshakes();
        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("next", "failing");
        assertThat(next.acceptNanos()).hasSize(1);
        assertThat(failing.acceptNanos()).hasSize(1);
    }

    @Test
    void aProbeThatFailsBeforeTheStartLoopReachesTheNextProbeStartsThatProbeOnce() throws Exception {
        var firstStartRunsInline = new AtomicBoolean(true);
        var laterStartsWait = new CountDownLatch(1);
        var dispatched = new AtomicInteger();
        var executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 10, TimeUnit.SECONDS, new SynchronousQueue<>()) {
            @Override
            public void execute(Runnable command) {
                dispatched.incrementAndGet();
                if (firstStartRunsInline.getAndSet(false)) {
                    command.run();
                } else {
                    super.execute(() -> {
                        try {
                            laterStartsWait.await();
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                        }
                        command.run();
                    });
                }
            }
        };
        client = new OkHttpClient.Builder().dispatcher(new Dispatcher(executor)).build();
        var race = new CandidateHandshakeRace(client, cooldownTracker, ExecutorInstrumentation.noop(), DEADLINE, Duration.ZERO,
                new HandshakeTimes(SMOOTHING_FACTOR), mock(Monitor.class));
        races.add(race);
        String firstAddress;
        String secondAddress;
        try (var first = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                var second = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            firstAddress = "https://127.0.0.1:" + first.getLocalPort() + DSP_PATH;
            secondAddress = "https://127.0.0.1:" + second.getLocalPort() + DSP_PATH;
        }
        CompletableFuture<List<Candidate>> order;
        try {
            order = race.order(List.of(new Candidate("first", firstAddress), new Candidate("second", secondAddress)));
        } finally {
            laterStartsWait.countDown();
        }
        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactlyInAnyOrder("first", "second");
        assertThat(dispatched).hasValue(2);
        assertThat(cooldownTracker.isCoolingDown(firstAddress)).isTrue();
        assertThat(cooldownTracker.isCoolingDown(secondAddress)).isTrue();
    }

    @Test
    void aProbeCancelledByTheWinnerKeepsItsAverage() throws Exception {
        var fast = startServer(FAST_HOST, trustedCa, 0);
        var slow = startServer(SLOW_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        var seeded = Duration.ofMillis(700);
        handshakeTimes.record(slow.address(), seeded);
        fast.holdHandshakes();
        slow.holdHandshakes();
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);

        var order = race.order(List.of(candidate("slow", slow), candidate("fast", fast)));
        await().atMost(8, TimeUnit.SECONDS).until(() -> !slow.acceptNanos().isEmpty());
        fast.releaseHandshakes();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("fast", "slow");
        slow.releaseHandshakes();
        await().atMost(8, TimeUnit.SECONDS).until(() -> slow.handshakesAbandoned().get() == 1);
        await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(5))
                .until(() -> handshakeTimes.average(slow.address()).equals(Optional.of(seeded)));
        assertThat(cooldownTracker.isCoolingDown(slow.address())).isFalse();
    }

    @Test
    void aSampleSpansFromTheFirstConnectAttemptSoRetriedConnectsCount() throws Exception {
        var retried = startServer(RETRY_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);

        race.order(List.of(candidate("retried", retried))).get(10, TimeUnit.SECONDS);

        assertThat(handshakeTimes.average(retried.address()).orElseThrow())
                .isGreaterThanOrEqualTo(MINIMUM_RETRIED_SAMPLE);
    }

    @Test
    void withoutStaggerAllProbesStartTogetherAndTheAveragesDoNotChangeTheOrder() throws Exception {
        var fast = startServer(FAST_HOST, trustedCa, 0);
        var slow = startServer(SLOW_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(fast.address(), Duration.ofSeconds(1));
        handshakeTimes.record(slow.address(), Duration.ofMillis(1));
        fast.holdHandshakes();
        slow.holdHandshakes();
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);
        var order = race.order(List.of(candidate("slow", slow), candidate("fast", fast)));

        await().atMost(8, TimeUnit.SECONDS).until(() -> !fast.acceptNanos().isEmpty() && !slow.acceptNanos().isEmpty());
        assertThat(order).isNotDone();
        fast.releaseHandshakes();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("fast", "slow");
        slow.releaseHandshakes();
    }

    @Test
    void withStaggerProbesStartOneStaggerApartInAverageOrderAndAnUnknownServerStartsLast() throws Exception {
        var stagger = Duration.ofMillis(250);
        var quick = startServer("quick.xroad.test", trustedCa, 0);
        var middling = startServer("middling.xroad.test", trustedCa, 0);
        var unknown = startServer("unknown.xroad.test", trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(middling.address(), Duration.ofMillis(400));
        handshakeTimes.record(quick.address(), Duration.ofMillis(50));
        var probedServers = List.of(quick, middling, unknown);
        probedServers.forEach(TlsServer::holdHandshakes);
        var race = race(DEADLINE, stagger, handshakeTimes);
        var order = race.order(List.of(candidate("unknown", unknown), candidate("middling", middling), candidate("quick", quick)));

        await().atMost(8, TimeUnit.SECONDS).until(() -> probedServers.stream().noneMatch(server -> server.acceptNanos().isEmpty()));
        probedServers.forEach(TlsServer::releaseHandshakes);

        assertThat(order.get(10, TimeUnit.SECONDS)).hasSize(3);
        var quickStart = quick.acceptNanos().getFirst();
        var middlingStart = middling.acceptNanos().getFirst();
        var unknownStart = unknown.acceptNanos().getFirst();

        assertThat(middlingStart - quickStart).isGreaterThanOrEqualTo(minimumGapNanos(stagger));
        assertThat(unknownStart - middlingStart).isGreaterThanOrEqualTo(minimumGapNanos(stagger));
    }

    @Test
    void theProbePathIsTheRegisteredParticipantContextVersionEndpoint() throws Exception {
        var provider = startServer(FAST_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        race.order(List.of(candidate("fast", provider))).get(10, TimeUnit.SECONDS);

        assertThat(provider.requests()).containsExactly("GET " + VERSION_PATH + " -> 200");
    }

    @Test
    void theProbeUrlReplacesTheProfileSegmentAndKeepsAnEncodedPercentEncoded() {
        var url = CandidateHandshakeRace.probeUrl("https://ss0.example.org:8183/api/dsp/DEV:COM:a%25b/http-dsp-profile-2025-1");

        assertThat(url).isNotNull();
        assertThat(url.host()).isEqualTo("ss0.example.org");
        assertThat(url.port()).isEqualTo(8183);
        assertThat(url.encodedPath()).isEqualTo("/api/dsp/DEV:COM:a%25b/.well-known/dspace-version");
    }

    @Test
    void anAddressWithoutAParticipantContextIsNotProbeable() {
        assertThat(CandidateHandshakeRace.probeUrl("https://ss0.example.org:8183/dsp")).isNull();
        assertThat(CandidateHandshakeRace.probeUrl("not a url")).isNull();
    }

    private CandidateHandshakeRace race(Duration deadline) throws Exception {
        return race(deadline, Duration.ZERO, new HandshakeTimes(SMOOTHING_FACTOR));
    }

    private CandidateHandshakeRace race(Duration deadline, Duration stagger, HandshakeTimes handshakeTimes) throws Exception {
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", trustedCa.certificate());
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        var trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new X509TrustManager[] {trustManager}, null);

        client = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                .socketFactory(new SlowFailingConnectSocketFactory())
                .dns(hostname -> {
                    if (RETRY_HOST.equals(hostname)) {
                        return List.of(UNREACHABLE_ADDRESS, InetAddress.getLoopbackAddress());
                    }
                    if (hostname.endsWith(".xroad.test")) {
                        return List.of(InetAddress.getLoopbackAddress());
                    }
                    throw new java.net.UnknownHostException(hostname);
                })
                .build();
        var race = new CandidateHandshakeRace(client, cooldownTracker, ExecutorInstrumentation.noop(), deadline, stagger,
                handshakeTimes, mock(Monitor.class));
        races.add(race);
        return race;
    }

    private static long minimumGapNanos(Duration stagger) {
        return stagger.dividedBy(2).toNanos();
    }

    private TlsServer startServer(String host, TestCa ca, long handshakeDelayMillis) throws Exception {
        return startServer(host, ca, handshakeDelayMillis, host);
    }

    private TlsServer startServer(String host, TestCa ca, long handshakeDelayMillis, String certificateHost) throws Exception {
        var server = new TlsServer(host, issueLeaf(ca, certificateHost), handshakeDelayMillis);
        servers.add(server);
        return server;
    }

    private static Candidate candidate(String id, TlsServer server) {
        return new Candidate(id, server.address());
    }

    private static String unusedAddress(String host) throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "https://" + host + ":" + socket.getLocalPort() + DSP_PATH;
        }
    }

    private static final class SlowFailingConnectSocketFactory extends SocketFactory {
        @Override
        public Socket createSocket() {
            return new Socket() {
                @Override
                public void connect(SocketAddress endpoint, int timeout) throws IOException {
                    if (endpoint instanceof InetSocketAddress address && UNREACHABLE_ADDRESS.equals(address.getAddress())) {
                        try {
                            Thread.sleep(FAILED_CONNECT_ATTEMPT_MILLIS);
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                        }
                        throw new ConnectException("unreachable test address");
                    }
                    super.connect(endpoint, timeout);
                }
            };
        }

        @Override
        public Socket createSocket(String host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TlsServer {
        private final String host;
        private final SSLServerSocket serverSocket;
        private final long handshakeDelayMillis;
        private final AtomicInteger handshakesCompleted = new AtomicInteger();
        private final AtomicInteger handshakesAbandoned = new AtomicInteger();
        private final List<String> hostHeaders = new CopyOnWriteArrayList<>();
        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final List<Long> acceptNanos = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch handshakeGate = new CountDownLatch(0);

        private TlsServer(String host, TestLeaf leaf, long handshakeDelayMillis) throws Exception {
            this.host = host;
            this.handshakeDelayMillis = handshakeDelayMillis;
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry("server", leaf.privateKey(), new char[0], leaf.chain());
            var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, new char[0]);
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            this.serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory()
                    .createServerSocket(0, 50, InetAddress.getLoopbackAddress());
            var acceptor = new Thread(this::acceptLoop, "tls-test-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String address() {
            return "https://" + host + ":" + port() + DSP_PATH;
        }

        AtomicInteger handshakesCompleted() {
            return handshakesCompleted;
        }

        AtomicInteger handshakesAbandoned() {
            return handshakesAbandoned;
        }

        List<String> hostHeaders() {
            return hostHeaders;
        }

        List<String> requests() {
            return requests;
        }

        List<Long> acceptNanos() {
            return acceptNanos;
        }

        void holdHandshakes() {
            handshakeGate = new CountDownLatch(1);
        }

        void releaseHandshakes() {
            handshakeGate.countDown();
        }

        void close() {
            try {
                serverSocket.close();
            } catch (IOException _) {
                // already closed
            }
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    var socket = (SSLSocket) serverSocket.accept();
                    acceptNanos.add(System.nanoTime());
                    var handler = new Thread(() -> serve(socket), "tls-test-connection");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException _) {
                    return;
                }
            }
        }

        private void serve(SSLSocket socket) {
            try (socket) {
                Thread.sleep(handshakeDelayMillis);
                handshakeGate.await();
                socket.startHandshake();
                handshakesCompleted.incrementAndGet();
                var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                var out = socket.getOutputStream();
                String requestLine;
                while ((requestLine = in.readLine()) != null) {
                    String header;
                    while ((header = in.readLine()) != null && !header.isEmpty()) {
                        if (header.regionMatches(true, 0, "Host:", 0, "Host:".length())) {
                            hostHeaders.add(header.substring("Host:".length()).trim());
                        }
                    }
                    var path = requestLine.split(" ")[1];
                    var status = VERSION_PATH.equals(path) ? 200 : 404;
                    requests.add("GET " + path + " -> " + status);
                    out.write(("HTTP/1.1 " + status + " X\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                }
            } catch (IOException e) {
                if (handshakesCompleted.get() == 0 || e instanceof javax.net.ssl.SSLHandshakeException) {
                    handshakesAbandoned.incrementAndGet();
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static TestCa selfSignedCa(String commonName) throws Exception {
        var keyPair = generateKeyPair();
        var subject = new X500Name("CN=" + commonName);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(), subject,
                keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new TestCa(keyPair, new JcaX509CertificateConverter().getCertificate(builder.build(signer)));
    }

    private static TestLeaf issueLeaf(TestCa ca, String dnsName) throws Exception {
        var leafKeyPair = generateKeyPair();
        var issuer = new X500Name(ca.certificate().getSubjectX500Principal().getName());
        var builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(),
                new X500Name("CN=" + dnsName), leafKeyPair.getPublic())
                .addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.dNSName, dnsName)));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(ca.keyPair().getPrivate());
        var leafCertificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        return new TestLeaf(leafKeyPair.getPrivate(), new X509Certificate[] {leafCertificate, ca.certificate()});
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Date notBefore() {
        return Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private static Date notAfter() {
        return Date.from(Instant.now().plus(365, ChronoUnit.DAYS));
    }

    private record TestCa(KeyPair keyPair, X509Certificate certificate) {
    }

    private record TestLeaf(PrivateKey privateKey, X509Certificate[] chain) {
    }
}
