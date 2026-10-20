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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.bridge.XRoadTlsOkHttpClientExtension;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Reason;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Resolution;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedConnectorTlsCAInfo;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchDidResolverTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(3);
    private static final int BOUND = BatchDidResolver.MAX_CONCURRENT_RESOLUTIONS;
    private static final int IN_FLIGHT_MILLIS = 500;
    private static final String GARBAGE = "this is not json";
    private static final String MALFORMED = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK";

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private final XRoadTlsOkHttpClientExtension tlsExtension = new XRoadTlsOkHttpClientExtension();
    private final List<AutoCloseable> cleanup = new ArrayList<>();

    private static DidStubServer.Ca sharedCa;

    @BeforeAll
    static void createSharedCa() throws Exception {
        sharedCa = DidStubServer.newCa("Test DS TLS CA");
    }

    @BeforeEach
    void stubRuntime() {
        var info = new ApprovedConnectorTlsCAInfo("ca", sharedCa.certificate(), List.of(), null, null, null);
        lenient().when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        lenient().when(globalConfProvider.getApprovedConnectorTlsCAs(INSTANCE_IDENTIFIER)).thenReturn(List.of(info));
        lenient().when(context.getMonitor()).thenReturn(monitor);
        lenient().when(context.getService(GlobalConfProvider.class)).thenReturn(globalConfProvider);
        lenient().when(context.getSetting(ArgumentMatchers.anyString(), ArgumentMatchers.isNull())).thenReturn(null);
        lenient().when(context.getSetting(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @AfterEach
    void tearDown() throws Exception {
        tlsExtension.shutdown();
        for (var closeable : cleanup) {
            closeable.close();
        }
    }

    @Test
    void aBatchWithMixedOutcomesReturnsOneResultPerDidInRequestOrder() throws Exception {
        var ss1 = start("xrd-ss1");
        var ss2 = start("xrd-ss2");
        var slowResolved = serveDocument(ss1, "xrd-ss1", 1, 300);
        var resolved = serveDocument(ss1, "xrd-ss1", 2, 0);
        var noDocument = didOf("xrd-ss1", ss1, 3);
        var idMismatch = didOf("xrd-ss1", ss1, 4);
        ss1.route(pathOf(4), DidStubServer.respond(200, DidStubServer.minimalDocument(didOf("xrd-ss1", ss1, 99))));
        var fetchFailure = didOf("xrd-ss2", ss2, 5);
        ss2.route(pathOf(5), DidStubServer.respond(200, GARBAGE));
        var dids = List.of(slowResolved, MALFORMED, noDocument, fetchFailure, idMismatch, resolved);

        var resolutions = batchResolver().resolveAll(dids, null);

        assertThat(resolutions).extracting(Resolution::did).containsExactlyElementsOf(dids);
        assertThat(resolutions).extracting(Resolution::reason).containsExactly(
                null, Reason.MALFORMED_DID, Reason.NO_DOCUMENT, Reason.FETCH_FAILURE, Reason.ID_MISMATCH, null);
    }

    @Test
    void atMostTheBoundOfRequestsAreInFlightAtOnce() throws Exception {
        var ss1 = start("xrd-ss1");
        var inFlight = new AtomicInteger();
        var peak = new AtomicInteger();
        var dids = new ArrayList<String>();
        for (var code = 1; code <= 3 * BOUND; code++) {
            var did = didOf("xrd-ss1", ss1, code);
            dids.add(did);
            var document = DidStubServer.respond(200, DidStubServer.minimalDocument(did));
            ss1.route(pathOf(code), exchange -> {
                peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    sleep(IN_FLIGHT_MILLIS);
                    document.handle(exchange);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        }

        var resolutions = batchResolver().resolveAll(dids, null);

        assertThat(resolutions).hasSameSizeAs(dids).allMatch(Resolution::isResolved);
        assertThat(peak.get()).isGreaterThan(1).isLessThanOrEqualTo(BOUND);
    }

    @Test
    void afterTheFirstConnectionFailureTheNotYetStartedEntriesOfThatEndpointAreFetchFailuresWithoutARequest()
            throws Exception {
        var scenario = runBatchWhoseFirstEntryAnswers(HttpExchange::close);

        for (var code = BOUND + 1; code <= scenario.hostACount(); code++) {
            assertThat(scenario.resolutions().get(code - 1).reason()).isEqualTo(Reason.FETCH_FAILURE);
            assertThat(scenario.hostA().requests(pathOf(code))).isZero();
        }
        assertThat(scenario.resolutions().subList(scenario.hostACount(), scenario.resolutions().size()))
                .allMatch(Resolution::isResolved);
        assertThat(scenario.hostB().requests(pathOf(1))).isEqualTo(1);
        assertThat(scenario.hostB().requests(pathOf(2))).isEqualTo(1);
    }

    @Test
    void entriesOfAFailingEndpointAlreadyInFlightWhenTheFirstFailureLandsFinishWithTheirOwnResult() throws Exception {
        var scenario = runBatchWhoseFirstEntryAnswers(HttpExchange::close);

        assertThat(scenario.resolutions().getFirst().reason()).isEqualTo(Reason.FETCH_FAILURE);
        assertThat(scenario.resolutions().subList(1, BOUND)).allMatch(Resolution::isResolved);
        for (var code = 1; code <= BOUND; code++) {
            assertThat(scenario.hostA().requests(pathOf(code))).isEqualTo(1);
        }
    }

    @Test
    void entriesThatCannotFinishWithinTheBudgetAreFetchFailures() throws Exception {
        var ss1 = start("xrd-ss1");
        var fast = serveDocument(ss1, "xrd-ss1", 1, 0);
        var dids = new ArrayList<String>();
        dids.add(fast);
        for (var code = 2; code <= BOUND + 3; code++) {
            dids.add(serveDocument(ss1, "xrd-ss1", code, 10_000));
        }
        var started = System.nanoTime();

        var resolutions = batchResolver().resolveAll(dids, Duration.ofMillis(800));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        assertThat(resolutions.getFirst().isResolved()).isTrue();
        assertThat(resolutions.subList(1, resolutions.size()))
                .allMatch(resolution -> resolution.reason() == Reason.FETCH_FAILURE);
    }

    @Test
    void resolutionsStillInFlightWhenTheBudgetExpiresAreInterrupted() {
        var resolver = mock(SubmittedDidResolver.class);
        var did = unservedDid(1);
        var interrupted = new CountDownLatch(1);
        when(resolver.resolve(did)).thenAnswer(invocation -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException _) {
                interrupted.countDown();
            }
            return Resolution.resolved(did);
        });

        var started = System.nanoTime();

        var resolutions = new BatchDidResolver(resolver, monitor).resolveAll(List.of(did), Duration.ofMillis(200));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(resolutions).extracting(Resolution::reason).containsExactly(Reason.FETCH_FAILURE);
        assertThat(awaitInterruption(interrupted)).isTrue();
    }

    @Test
    void resolutionsStillInFlightWhenAnErrorPropagatesOutOfResolveAllAreInterrupted() {
        var resolver = mock(SubmittedDidResolver.class);
        var dids = List.of(unservedDid(1), unservedDid(2));
        var secondStarted = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        when(resolver.resolve(dids.getFirst())).thenAnswer(invocation -> {
            awaitUninterruptibly(secondStarted);
            throw new NoClassDefFoundError("missing");
        });
        when(resolver.resolve(dids.getLast())).thenAnswer(invocation -> {
            secondStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException _) {
                interrupted.countDown();
            }
            return Resolution.resolved(dids.getLast());
        });
        var batchResolver = new BatchDidResolver(resolver, monitor);

        assertThatThrownBy(() -> batchResolver.resolveAll(dids, null)).isInstanceOf(NoClassDefFoundError.class);

        assertThat(awaitInterruption(interrupted)).isTrue();
    }

    @Test
    void anUnreadableDocumentDoesNotStopTheOtherEntriesOfTheSameEndpoint() throws Exception {
        var scenario = runBatchWhoseFirstEntryAnswers(DidStubServer.respond(200, GARBAGE));

        assertThat(scenario.resolutions().getFirst().reason()).isEqualTo(Reason.FETCH_FAILURE);
        assertThat(scenario.resolutions().subList(1, scenario.resolutions().size())).allMatch(Resolution::isResolved);
        for (var code = 1; code <= scenario.hostACount(); code++) {
            assertThat(scenario.hostA().requests(pathOf(code))).isEqualTo(1);
        }
    }

    @Test
    void aRefusedConnectionDoesNotStopTheEntriesOfAnotherPortOfTheSameHost() throws Exception {
        var served = start("xrd-ss1");
        var refused = "did:web:xrd-ss1%3A" + closedPort() + ":v1:DEV:COM:1";
        var dids = new ArrayList<String>();
        dids.add(refused);
        for (var code = 2; code <= BOUND + 3; code++) {
            dids.add(serveDocument(served, "xrd-ss1", code, IN_FLIGHT_MILLIS));
        }

        var resolutions = batchResolver().resolveAll(dids, null);

        assertThat(resolutions.getFirst().reason()).isEqualTo(Reason.FETCH_FAILURE);
        assertThat(resolutions.subList(1, resolutions.size())).allMatch(Resolution::isResolved);
    }

    @Test
    void malformedDidsAreReportedAsMalformedWhenTheBudgetIsAlreadyUsedUp() throws Exception {
        var ss1 = start("xrd-ss1");
        var wellFormed = serveDocument(ss1, "xrd-ss1", 1, 0);

        var resolutions = batchResolver().resolveAll(List.of(MALFORMED, wellFormed), Duration.ZERO);

        assertThat(resolutions).extracting(Resolution::reason).containsExactly(Reason.MALFORMED_DID, Reason.FETCH_FAILURE);
        assertThat(ss1.requests(pathOf(1))).isZero();
    }

    @Test
    void malformedDidsThatAreNeverAdmittedBeforeTheDeadlineAreReportedAsMalformed() throws Exception {
        var ss1 = start("xrd-ss1");
        var dids = new ArrayList<String>();
        for (var code = 1; code <= BOUND; code++) {
            dids.add(serveDocument(ss1, "xrd-ss1", code, 10_000));
        }
        dids.add(MALFORMED);
        dids.add(serveDocument(ss1, "xrd-ss1", BOUND + 1, 0));

        var resolutions = batchResolver().resolveAll(dids, Duration.ofMillis(800));

        assertThat(resolutions.subList(0, BOUND)).allMatch(resolution -> resolution.reason() == Reason.FETCH_FAILURE);
        assertThat(resolutions.get(BOUND).reason()).isEqualTo(Reason.MALFORMED_DID);
        assertThat(resolutions.getLast().reason()).isEqualTo(Reason.FETCH_FAILURE);
    }

    @Test
    void anUncheckedFailureOfOneEntryIsALoggedFetchFailureAndTheOtherEntriesKeepTheirResults() {
        var resolver = mock(SubmittedDidResolver.class);
        var dids = List.of(unservedDid(1), unservedDid(2), unservedDid(3));
        when(resolver.resolve(dids.getFirst())).thenReturn(Resolution.resolved(dids.getFirst()));
        when(resolver.resolve(dids.get(1))).thenThrow(new IllegalStateException("boom"));
        when(resolver.resolve(dids.get(2))).thenReturn(Resolution.resolved(dids.get(2)));
        var capturingMonitor = new CapturingMonitor();

        var resolutions = new BatchDidResolver(resolver, capturingMonitor).resolveAll(dids, null);

        assertThat(resolutions).extracting(Resolution::reason).containsExactly(null, Reason.FETCH_FAILURE, null);
        assertThat(capturingMonitor.severe).hasSize(1);
        assertThat(capturingMonitor.severe.getFirst()).contains(dids.get(1));
    }

    @Test
    void anErrorThrownWhileResolvingAnEntryPropagatesOutOfResolveAll() {
        var resolver = mock(SubmittedDidResolver.class);
        var dids = List.of(unservedDid(1), unservedDid(2));
        when(resolver.resolve(dids.getFirst())).thenReturn(Resolution.resolved(dids.getFirst()));
        when(resolver.resolve(dids.get(1))).thenThrow(new NoClassDefFoundError("missing"));
        var batchResolver = new BatchDidResolver(resolver, new CapturingMonitor());

        assertThatThrownBy(() -> batchResolver.resolveAll(dids, null)).isInstanceOf(NoClassDefFoundError.class);
    }

    @Test
    void connectionsToOneEndpointAreReusedAcrossEntries() throws Exception {
        var ss1 = start("xrd-ss1");
        var clientPorts = ConcurrentHashMap.<Integer>newKeySet();
        var dids = new ArrayList<String>();
        for (var code = 1; code <= 3 * BOUND; code++) {
            var did = didOf("xrd-ss1", ss1, code);
            dids.add(did);
            var document = DidStubServer.respond(200, DidStubServer.minimalDocument(did));
            ss1.route(pathOf(code), exchange -> {
                clientPorts.add(exchange.getRemoteAddress().getPort());
                document.handle(exchange);
            });
        }

        var resolutions = batchResolver().resolveAll(dids, null);

        assertThat(resolutions).hasSameSizeAs(dids).allMatch(Resolution::isResolved);
        assertThat(clientPorts).hasSizeLessThan(dids.size() / 2);
    }

    private FailingHostBatch runBatchWhoseFirstEntryAnswers(HttpHandler firstEntryAnswer) throws Exception {
        var hostA = start("xrd-ss1");
        var hostB = start("xrd-ss2");
        var hostACount = BOUND + 3;
        var allInFlight = new CountDownLatch(BOUND);
        var dids = new ArrayList<String>();
        for (var code = 1; code <= hostACount; code++) {
            var did = didOf("xrd-ss1", hostA, code);
            dids.add(did);
            var document = DidStubServer.respond(200, DidStubServer.minimalDocument(did));
            HttpHandler handler = code == 1
                    ? exchange -> {
                        allInFlight.countDown();
                        awaitUninterruptibly(allInFlight);
                        firstEntryAnswer.handle(exchange);
                    }
                    : exchange -> {
                        allInFlight.countDown();
                        sleep(IN_FLIGHT_MILLIS);
                        document.handle(exchange);
                    };
            hostA.route(pathOf(code), handler);
        }
        dids.add(serveDocument(hostB, "xrd-ss2", 1, 0));
        dids.add(serveDocument(hostB, "xrd-ss2", 2, 0));

        return new FailingHostBatch(hostA, hostB, hostACount, batchResolver().resolveAll(dids, null));
    }

    private static int closedPort() throws Exception {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private DidStubServer start(String hostName) throws Exception {
        var stub = DidStubServer.startForHost(sharedCa, hostName);
        cleanup.add(stub);
        return stub;
    }

    private String serveDocument(DidStubServer stub, String hostName, int code, long delayMillis) {
        var did = didOf(hostName, stub, code);
        var document = DidStubServer.respond(200, DidStubServer.minimalDocument(did));
        stub.route(pathOf(code), exchange -> {
            sleep(delayMillis);
            document.handle(exchange);
        });
        return did;
    }

    private static String didOf(String hostName, DidStubServer stub, int code) {
        return "did:web:" + hostName + "%3A" + stub.port() + ":v1:DEV:COM:" + code;
    }

    private static String unservedDid(int code) {
        return "did:web:xrd-ss1%3A8443:v1:DEV:COM:" + code;
    }

    private static String pathOf(int code) {
        return "/v1/DEV/COM/" + code + "/did.json";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean awaitInterruption(CountDownLatch interrupted) {
        try {
            return interrupted.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private BatchDidResolver batchResolver() {
        var runtimeClient = tlsExtension.okHttpClient(context).newBuilder()
                .dns(hostName -> List.of(InetAddress.getByName("127.0.0.1")))
                .build();
        return new BatchDidResolver(SubmittedDidResolver.create(
                runtimeClient, new JacksonTypeManager().getMapper(), FETCH_TIMEOUT, monitor), monitor);
    }

    private static final class CapturingMonitor implements Monitor {
        private final List<String> severe = new CopyOnWriteArrayList<>();

        @Override
        public void severe(Supplier<String> supplier, Throwable... errors) {
            severe.add(supplier.get());
        }

        @Override
        public void warning(Supplier<String> supplier, Throwable... errors) {
            // not asserted
        }

        @Override
        public void info(Supplier<String> supplier, Throwable... errors) {
            // not asserted
        }

        @Override
        public void debug(Supplier<String> supplier, Throwable... errors) {
            // not asserted
        }
    }

    private record FailingHostBatch(DidStubServer hostA, DidStubServer hostB, int hostACount,
                                    List<Resolution> resolutions) {
    }
}
