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

import com.sun.net.httpserver.HttpServer;
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
import java.security.GeneralSecurityException;
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
    private static final String SS1_HOST = "ss1.example.org";
    private static final String SS0_HOST = "ss0.example.org";
    private static final String SS2_HOST = "ss2.example.org";
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private static final long SLOW_HANDSHAKE_MILLIS = 3000;
    private static final double SMOOTHING_FACTOR = 0.3;
    private static final InetAddress UNREACHABLE_ADDRESS = InetAddress.ofLiteral("127.0.0.2");
    private static final long FAILED_CONNECT_ATTEMPT_MILLIS = 600;
    private static final Duration MINIMUM_RETRIED_SAMPLE = Duration.ofMillis(200);

    private static TestCa trustedCa;
    private static TestCa untrustedCa;

    private final List<TlsServer> servers = new ArrayList<>();
    private final List<HttpServer> plainServers = new ArrayList<>();
    private final List<CandidateHandshakeRace> races = new ArrayList<>();
    private final CooldownTracker cooldownTracker = new CooldownTracker(Duration.ofSeconds(180));
    private volatile boolean unreachableAddressFirst;
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
        plainServers.forEach(server -> server.stop(0));
        if (client != null) {
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
        }
    }

    @Test
    void theFasterHandshakeIsOrderedFirst() throws Exception {
        var slow = startServer(SS0_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var fast = startServer(SS1_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("ss0", slow), candidate("ss1", fast))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).first().isEqualTo("ss1");
        await().atMost(8, TimeUnit.SECONDS).untilAsserted(() -> assertThat(fast.handshakesCompleted()).hasValue(1));
        assertThat(slow.handshakesCompleted()).hasValue(0);
    }

    @Test
    void losingProbesAreCancelledOnceTheWinnerCompletesItsHandshake() throws Exception {
        var slow = startServer(SS0_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var fast = startServer(SS1_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("ss0", slow), candidate("ss1", fast))).get(10, TimeUnit.SECONDS);

        await().atMost(8, TimeUnit.SECONDS).until(() -> slow.handshakesAbandoned().get() == 1);
        assertThat(slow.handshakesCompleted()).hasValue(0);
        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("ss1", "ss0");
        assertThat(cooldownTracker.isCoolingDown(slow.address())).isFalse();
    }

    @Test
    void everyProbeCallIsCancelledOnceTheRaceIsDecidedTheWinnersIncluded() throws Exception {
        var ss0 = startServer(SS0_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var ss1 = startServer(SS1_HOST, trustedCa, 0);
        ss1.holdResponses();
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("ss0", ss0), candidate("ss1", ss1))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("ss1", "ss0");
        await().atMost(DEADLINE.dividedBy(2)).until(() -> client.dispatcher().runningCallsCount() == 0);
        assertThat(cooldownTracker.isCoolingDown(ss1.address())).isFalse();
        assertThat(cooldownTracker.isCoolingDown(ss0.address())).isFalse();
        ss1.releaseResponses();
    }

    @Test
    void aSlowHttpReplyAfterTheHandshakeChangesNeitherTheOrderNorTheCooldown() throws Exception {
        var ss0 = startServer(SS0_HOST, trustedCa, 0);
        var ss1 = startServer(SS1_HOST, trustedCa, 0);
        ss0.holdHandshakes();
        ss1.holdResponses();
        var race = race(Duration.ofSeconds(3));

        var order = race.order(List.of(candidate("ss0", ss0), candidate("ss1", ss1)));

        assertThat(order.get(2, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("ss1", "ss0");
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .until(() -> !cooldownTracker.isCoolingDown(ss1.address()) && !cooldownTracker.isCoolingDown(ss0.address()));
        ss0.releaseHandshakes();
        ss1.releaseResponses();
    }

    @Test
    void aFailedHttpReplyAfterTheHandshakeChangesNeitherTheOrderNorTheCooldown() throws Exception {
        var ss0 = startServer(SS0_HOST, trustedCa, 0);
        var ss1 = startServer(SS1_HOST, trustedCa, 0);
        ss0.holdHandshakes();
        ss1.dropConnectionsAfterTheHandshake();
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("ss0", ss0), candidate("ss1", ss1)));

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("ss1", "ss0");
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .until(() -> !cooldownTracker.isCoolingDown(ss1.address()) && !cooldownTracker.isCoolingDown(ss0.address()));
        ss0.releaseHandshakes();
    }

    @Test
    void aConnectionPooledByTheEdcClientDoesNotWinWithoutAHandshake() throws Exception {
        var warm = startServer(SS0_HOST, trustedCa, 0);
        var fresh = startServer(SS1_HOST, trustedCa, 0);
        var race = race(DEADLINE);
        var warmUpRequest = new Request.Builder().url("https://" + SS0_HOST + ":" + warm.port() + VERSION_PATH).build();
        try (var response = client.newCall(warmUpRequest).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
        await().atMost(8, TimeUnit.SECONDS).untilAsserted(() -> assertThat(warm.handshakesCompleted()).hasValue(1));
        warm.holdHandshakes();
        fresh.holdHandshakes();

        var order = race.order(List.of(candidate("warm", warm), candidate("fresh", fresh)));
        await().atMost(8, TimeUnit.SECONDS).until(() -> warm.acceptNanos().size() == 2 && !fresh.acceptNanos().isEmpty());
        assertThat(order).isNotDone();
        fresh.releaseHandshakes();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("fresh", "warm");
        warm.releaseHandshakes();
        assertThat(cooldownTracker.isCoolingDown(warm.address())).isFalse();
    }

    @Test
    void aProbeThatGetsAResponseWithoutATlsHandshakeIsAFailureSoTheRoundCompletes() throws Exception {
        var plainAddress = startPlainServer();
        var unapproved = startServer(SS2_HOST, untrustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(plainAddress, Duration.ofMillis(5));
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);
        var order = race.order(List.of(new Candidate("plain", plainAddress), candidate("unapproved", unapproved)));
        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactlyInAnyOrder("plain", "unapproved");
        assertThat(cooldownTracker.isCoolingDown(plainAddress)).isTrue();
        assertThat(cooldownTracker.isCoolingDown(unapproved.address())).isTrue();
        assertThat(handshakeTimes.average(plainAddress)).isEmpty();
    }

    @Test
    void aProbeFailureMarksTheServerInCooldownAndFailedServersComeAfterAllWinners() throws Exception {
        var fast = startServer(SS1_HOST, trustedCa, 300);
        var refused = unusedAddress(SS1_HOST);
        var unapproved = startServer("ss2.example.org", untrustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(
                new Candidate("refused", refused),
                candidate("unapproved", unapproved),
                candidate("ss1", fast))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).first().isEqualTo("ss1");
        assertThat(order).extracting(Candidate::counterPartyId).containsExactlyInAnyOrder("ss1", "refused", "unapproved");
        assertThat(cooldownTracker.isCoolingDown(refused)).isTrue();
        assertThat(cooldownTracker.isCoolingDown(unapproved.address())).isTrue();
        assertThat(cooldownTracker.isCoolingDown(fast.address())).isFalse();
    }

    @Test
    void probeFailedServersAreShuffled() throws Exception {
        var winner = startServer(SS1_HOST, trustedCa, 300);
        var failed = List.of(unusedAddress(SS1_HOST), unusedAddress(SS1_HOST), unusedAddress(SS1_HOST),
                unusedAddress(SS1_HOST));
        var candidates = new ArrayList<Candidate>();
        candidates.add(candidate("winner", winner));
        for (var i = 0; i < failed.size(); i++) {
            candidates.add(new Candidate("failed-" + i, failed.get(i)));
        }
        var race = race(DEADLINE);

        Set<List<String>> failedOrders = new HashSet<>();
        for (var run = 0; run < 10; run++) {
            var order = race.order(candidates).get(10, TimeUnit.SECONDS);
            assertThat(order.getFirst().counterPartyId()).isEqualTo("winner");
            failedOrders.add(order.stream().skip(1).map(Candidate::counterPartyId).toList());
        }

        assertThat(failed).allMatch(cooldownTracker::isCoolingDown);
        await().atMost(8, TimeUnit.SECONDS).untilAsserted(() -> assertThat(winner.handshakesCompleted()).hasValue(10));
        assertThat(failedOrders.size()).isGreaterThan(1);
    }

    @Test
    void aProbeThatMissesTheDeadlineIsAFailure() throws Exception {
        var stalled = startServer(SS0_HOST, trustedCa, 5000);
        var race = race(Duration.ofMillis(400));

        var order = race.order(List.of(candidate("stalled", stalled))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("stalled");
        assertThat(cooldownTracker.isCoolingDown(stalled.address())).isTrue();
    }

    @Test
    void whenEveryProbeFailsEveryServerIsInCooldownAndStillReturned() throws Exception {
        var first = unusedAddress(SS1_HOST);
        var second = unusedAddress(SS0_HOST);
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
        var trusted = startServer(SS1_HOST, trustedCa, 0);
        var race = race(DEADLINE);

        var order = race.order(List.of(candidate("trusted", trusted))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("trusted");
        await().atMost(8, TimeUnit.SECONDS).untilAsserted(() -> assertThat(trusted.handshakesCompleted()).hasValue(1));
        assertThat(cooldownTracker.isCoolingDown(trusted.address())).isFalse();
    }

    @Test
    void aCertificateForAnotherHostNameFailsTheProbe() throws Exception {
        var wrongName = startServer("ss2.example.org", trustedCa, 0, "ss0.example.org");
        var race = race(DEADLINE);

        var order = race.order(List.of(new Candidate("wrong-name", wrongName.address()))).get(10, TimeUnit.SECONDS);

        assertThat(order).extracting(Candidate::counterPartyId).containsExactly("wrong-name");
        assertThat(cooldownTracker.isCoolingDown(wrongName.address())).isTrue();
    }

    @Test
    void theOrderIsHandedOutOnTheCompletionExecutor() throws Exception {
        var provider = startServer(SS1_HOST, trustedCa, 0);
        provider.holdHandshakes();
        var race = race(DEADLINE);

        var completingThread = race.order(List.of(candidate("provider", provider)))
                .thenApply(_ -> Thread.currentThread().getName());
        provider.releaseHandshakes();

        assertThat(completingThread.get(10, TimeUnit.SECONDS)).startsWith(CandidateHandshakeRace.COMPLETION_THREAD_NAME);
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
    void everyCompletedHandshakeUpdatesTheServersAverage() throws Exception {
        var fast = startServer(SS1_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);

        race.order(List.of(candidate("ss1", fast))).get(10, TimeUnit.SECONDS);

        assertThat(handshakeTimes.average(fast.address()).orElseThrow()).isPositive().isLessThan(DEADLINE);
    }

    @Test
    void aServerWhoseProbeFailedStartsAmongTheServersWithoutAnAverageOnceItIsProbedAgain() throws Exception {
        var stagger = Duration.ofMillis(250);
        var flaky = startServer(SS2_HOST, untrustedCa, 0);
        var known = startServer(SS1_HOST, trustedCa, 0);
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
        var failing = startServer(SS2_HOST, untrustedCa, 0);
        var healthy = startServer(SS1_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(failing.address(), Duration.ofMillis(1));
        var race = race(DEADLINE, Duration.ofMillis(250), handshakeTimes);

        var order = race.order(List.of(candidate("healthy", healthy), candidate("failing", failing)));
        race.stop();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("healthy", "failing");
        assertThat(cooldownTracker.isCoolingDown(failing.address())).isTrue();
        assertThat(cooldownTracker.isCoolingDown(healthy.address())).isFalse();
    }

    @Test
    void cancellingTheOrderCancelsTheRunningProbeAndTheProbeStartsStillScheduled() throws Exception {
        var stagger = Duration.ofMillis(800);
        var started = startServer(SS0_HOST, trustedCa, 0);
        var scheduled = startServer(SS1_HOST, trustedCa, 0);
        var lastScheduled = startServer(SS2_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(started.address(), Duration.ofMillis(10));
        handshakeTimes.record(scheduled.address(), Duration.ofMillis(20));
        handshakeTimes.record(lastScheduled.address(), Duration.ofMillis(30));
        List.of(started, scheduled, lastScheduled).forEach(TlsServer::holdHandshakes);
        var race = race(DEADLINE, stagger, handshakeTimes);
        var order = race.order(List.of(candidate("ss2", lastScheduled), candidate("ss1", scheduled), candidate("ss0", started)));
        await().atMost(8, TimeUnit.SECONDS).until(() -> !started.acceptNanos().isEmpty());

        order.cancel(true);

        await().atMost(DEADLINE.dividedBy(2)).until(() -> client.dispatcher().runningCallsCount() == 0);
        started.releaseHandshakes();
        await().atMost(8, TimeUnit.SECONDS).until(() -> started.handshakesAbandoned().get() == 1);
        await().during(stagger.multipliedBy(2).plusMillis(100)).atMost(stagger.multipliedBy(2).plusSeconds(5))
                .until(() -> scheduled.acceptNanos().isEmpty() && lastScheduled.acceptNanos().isEmpty());
        assertThat(started.handshakesCompleted()).hasValue(0);
        assertThat(handshakeTimes.average(started.address())).hasValue(Duration.ofMillis(10));
        assertThat(handshakeTimes.average(scheduled.address())).hasValue(Duration.ofMillis(20));
        assertThat(handshakeTimes.average(lastScheduled.address())).hasValue(Duration.ofMillis(30));
        assertThat(List.of(started, scheduled, lastScheduled)).noneMatch(server -> cooldownTracker.isCoolingDown(server.address()));
        scheduled.releaseHandshakes();
        lastScheduled.releaseHandshakes();
    }

    @Test
    void aProbeNotStartedWhenTheRaceIsDecidedIsCancelledAndNeverStarts() throws Exception {
        var stagger = Duration.ofMillis(500);
        var quick = startServer(SS1_HOST, trustedCa, 0);
        var waiting = startServer(SS0_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(quick.address(), Duration.ofMillis(10));
        handshakeTimes.record(waiting.address(), Duration.ofMillis(20));
        var dispatched = new AtomicInteger();
        var executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 10, TimeUnit.SECONDS, new SynchronousQueue<>()) {
            @Override
            public void execute(Runnable command) {
                dispatched.incrementAndGet();
                super.execute(command);
            }
        };
        var race = race(DEADLINE, stagger, handshakeTimes, new Dispatcher(executor));

        var order = race.order(List.of(candidate("ss0", waiting), candidate("ss1", quick)));

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("ss1", "ss0");
        await().during(stagger.plusMillis(200)).atMost(stagger.plusSeconds(5)).until(() -> waiting.acceptNanos().isEmpty());
        assertThat(dispatched).hasValue(1);
        assertThat(cooldownTracker.isCoolingDown(waiting.address())).isFalse();
        assertThat(handshakeTimes.average(waiting.address())).hasValue(Duration.ofMillis(20));
    }

    @Test
    void aStaggeredRaceBegunAfterStopStillCompletes() throws Exception {
        var first = startServer(SS0_HOST, trustedCa, 0);
        var second = startServer(SS1_HOST, trustedCa, 0);
        var race = race(DEADLINE, Duration.ofMillis(250), new HandshakeTimes(SMOOTHING_FACTOR));
        race.stop();

        var order = race.order(List.of(candidate("ss0", first), candidate("ss1", second)));

        assertThat(order.get(10, TimeUnit.SECONDS)).hasSize(2);
    }

    @Test
    void aStaggeredRaceBegunAfterStopStartsAllProbesAtOnceAndCompletesWhenTheFirstStartedProbeFails() throws Exception {
        var failing = startServer(SS2_HOST, untrustedCa, 0);
        var healthy = startServer(SS1_HOST, trustedCa, 0);
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
        var failing = startServer(SS2_HOST, untrustedCa, 0);
        var next = startServer(SS1_HOST, trustedCa, 0);
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
    void aProbeCancelledByTheWinnerKeepsItsAverage() throws Exception {
        var fast = startServer(SS1_HOST, trustedCa, 0);
        var slow = startServer(SS0_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        var seeded = Duration.ofMillis(700);
        handshakeTimes.record(slow.address(), seeded);
        fast.holdHandshakes();
        slow.holdHandshakes();
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);

        var order = race.order(List.of(candidate("ss0", slow), candidate("ss1", fast)));
        await().atMost(8, TimeUnit.SECONDS).until(() -> !slow.acceptNanos().isEmpty());
        fast.releaseHandshakes();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("ss1", "ss0");
        slow.releaseHandshakes();
        await().atMost(8, TimeUnit.SECONDS).until(() -> slow.handshakesAbandoned().get() == 1);
        await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(5))
                .until(() -> handshakeTimes.average(slow.address()).equals(Optional.of(seeded)));
        assertThat(cooldownTracker.isCoolingDown(slow.address())).isFalse();
    }

    @Test
    void aSampleSpansFromTheFirstConnectAttemptSoRetriedConnectsCount() throws Exception {
        var retried = startServer(SS1_HOST, trustedCa, 0);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        unreachableAddressFirst = true;
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);

        race.order(List.of(candidate("retried", retried))).get(10, TimeUnit.SECONDS);

        assertThat(handshakeTimes.average(retried.address()).orElseThrow())
                .isGreaterThanOrEqualTo(MINIMUM_RETRIED_SAMPLE);
    }

    @Test
    void withoutStaggerAllProbesStartTogetherAndTheAveragesDoNotChangeTheOrder() throws Exception {
        var fast = startServer(SS1_HOST, trustedCa, 0);
        var slow = startServer(SS0_HOST, trustedCa, SLOW_HANDSHAKE_MILLIS);
        var handshakeTimes = new HandshakeTimes(SMOOTHING_FACTOR);
        handshakeTimes.record(fast.address(), Duration.ofSeconds(1));
        handshakeTimes.record(slow.address(), Duration.ofMillis(1));
        fast.holdHandshakes();
        slow.holdHandshakes();
        var race = race(DEADLINE, Duration.ZERO, handshakeTimes);
        var order = race.order(List.of(candidate("ss0", slow), candidate("ss1", fast)));

        await().atMost(8, TimeUnit.SECONDS).until(() -> !fast.acceptNanos().isEmpty() && !slow.acceptNanos().isEmpty());
        assertThat(order).isNotDone();
        fast.releaseHandshakes();

        assertThat(order.get(10, TimeUnit.SECONDS)).extracting(Candidate::counterPartyId).containsExactly("ss1", "ss0");
        slow.releaseHandshakes();
    }

    @Test
    void withStaggerProbesStartOneStaggerApartInAverageOrderAndAnUnknownServerStartsLast() throws Exception {
        var stagger = Duration.ofMillis(250);
        var quick = startServer(SS0_HOST, trustedCa, 0);
        var middling = startServer(SS1_HOST, trustedCa, 0);
        var unknown = startServer(SS2_HOST, trustedCa, 0);
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
        return race(deadline, stagger, handshakeTimes, new Dispatcher());
    }

    private CandidateHandshakeRace race(Duration deadline, Duration stagger, HandshakeTimes handshakeTimes, Dispatcher dispatcher)
            throws GeneralSecurityException, IOException {
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", trustedCa.certificate());
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        var trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new X509TrustManager[] {trustManager}, null);

        client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                .socketFactory(new SlowFailingConnectSocketFactory())
                .dns(hostname -> {
                    if (hostname.endsWith(".example.org")) {
                        return unreachableAddressFirst
                                ? List.of(UNREACHABLE_ADDRESS, InetAddress.getLoopbackAddress())
                                : List.of(InetAddress.getLoopbackAddress());
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

    private String startPlainServer() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        plainServers.add(server);
        return "http://ss2.example.org:" + server.getAddress().getPort() + DSP_PATH;
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
        private final List<Long> acceptNanos = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch handshakeGate = new CountDownLatch(0);
        private volatile CountDownLatch responseGate = new CountDownLatch(0);
        private volatile boolean dropAfterHandshake;

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

        List<Long> acceptNanos() {
            return acceptNanos;
        }

        void holdHandshakes() {
            handshakeGate = new CountDownLatch(1);
        }

        void releaseHandshakes() {
            handshakeGate.countDown();
        }

        void dropConnectionsAfterTheHandshake() {
            dropAfterHandshake = true;
        }

        void holdResponses() {
            responseGate = new CountDownLatch(1);
        }

        void releaseResponses() {
            responseGate.countDown();
        }

        void close() {
            handshakeGate.countDown();
            responseGate.countDown();
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
                if (dropAfterHandshake) {
                    return;
                }
                var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                var out = socket.getOutputStream();
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        responseGate.await();
                        out.write("HTTP/1.1 200 X\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                        out.flush();
                    }
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
