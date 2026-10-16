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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.bridge.XRoadTlsOkHttpClientExtension;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Reason;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedConnectorTlsCAInfo;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmittedDidResolverTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration RELOAD_WAIT = Duration.ofSeconds(20);
    private static final String TRUST_RELOAD_INTERVAL_SETTING = "xroad.edc.web.https.trust.reload-interval-seconds";

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor runtimeMonitor;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private final CapturingMonitor resolverMonitor = new CapturingMonitor();
    private final XRoadTlsOkHttpClientExtension tlsExtension = new XRoadTlsOkHttpClientExtension();
    private final List<AutoCloseable> cleanup = new ArrayList<>();

    private static DidStubServer.Ca sharedCa;

    private volatile List<ApprovedConnectorTlsCAInfo> approvedCas = List.of();

    @BeforeAll
    static void createSharedCa() throws Exception {
        sharedCa = DidStubServer.newCa("Test DS TLS CA");
    }

    @BeforeEach
    void stubRuntime() {
        lenient().when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        lenient().when(globalConfProvider.getApprovedConnectorTlsCAs(INSTANCE_IDENTIFIER)).thenAnswer(invocation -> approvedCas);
        lenient().when(context.getMonitor()).thenReturn(runtimeMonitor);
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
    void aDidWhoseDocumentNamesItselfIsResolved() throws Exception {
        var stub = start(sharedCa);
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.did()).isEqualTo(stub.did());
        assertThat(resolution.reason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1:TEST:GOV:1234", "v1:system"})
    void aDidNamingASecurityServerHostAndItsDidPortIsResolved(String didPath) throws Exception {
        var stub = startForHost(sharedCa, "xrd-ss1");
        var did = "did:web:xrd-ss1%3A" + stub.port() + ":" + didPath;
        var documentPath = "/" + didPath.replace(':', '/') + "/did.json";
        stub.route(documentPath, DidStubServer.respond(200, DidStubServer.minimalDocument(did)));
        trust(sharedCa);

        var resolution = resolverDiallingTheLoopbackForEveryHost().resolve(did);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(stub.requests(documentPath)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
        "did:web:xrd-ss1%3A7183:v1:TEST:GOV:1234, https://xrd-ss1:7183/v1/TEST/GOV/1234/did.json",
        "did:web:xrd-ss1%3A7183:v1:system, https://xrd-ss1:7183/v1/system/did.json",
        "did:web:xrd-ss1:v1:TEST:GOV:1234, https://xrd-ss1/v1/TEST/GOV/1234/did.json",
        "did:web:%5B2001%3Adb8%3A%3A8%5D%3A7183:v1:DEV:COM:222, https://[2001:db8::8]:7183/v1/DEV/COM/222/did.json",
        "did:web:%5B2001%3A0db8%3A%3A0008%5D%3A7183:v1:DEV:COM:222, https://[2001:db8::8]:7183/v1/DEV/COM/222/did.json",
        "did:web:xrd-ss1%3A7183:v1:DEV%20X:COM:222, https://xrd-ss1:7183/v1/DEV%20X/COM/222/did.json",
        "did:web:xrd-ss1%3A7183:v1:DEV%C3%A9:COM:222, https://xrd-ss1:7183/v1/DEV%C3%A9/COM/222/did.json"})
    void anXRoadDidIsFetchedFromItsHostPortAndPath(String did, String expectedUrl) throws Exception {
        var delegate = mock(EdcHttpClient.class);
        var requests = new ArrayList<Request>();
        when(delegate.execute(ArgumentMatchers.any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            requests.add(request);
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(DidStubServer.minimalDocument(did), MediaType.get("application/json")))
                    .build();
        });

        var resolution = resolverOver(delegate).resolve(did);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(requests).extracting(request -> request.url().toString()).containsExactly(expectedUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
        "did:web:",
        "did:web::v1:DEV",
        "did:web:%",
        "did:web:%3A",
        "did:web:host%20name",
        "did:web:host%3A99999",
        "web:127.0.0.1",
        "",
        "did:web:host%40evil.org",
        "did:web:user%3Apw%40host",
        "did:web:host%2Fpath",
        "did:web:host%3F",
        "did:web:host%23x",
        "did:web:evil.org%40127.0.0.1%3A7183:v1:DEV:COM:222",
        "did:web:127.0.0.1%3A7183%2Fv1:DEV:COM:222",
        "did:web:%C5%BFs1.example:v1:DEV:COM:222",
        "did:web:%E2%84%AAs1.example:v1:DEV:COM:222",
        "did:web:xrd-ss1%252Eexample%3A7183:v1:DEV:COM:222",
        "did:web:%2541%3A7183:v1:DEV:COM:222",
        "did:web:b\u00fccher.example:v1:DEV:COM:222",
        "did:web:xrd-cs%3A6183:issuer",
        "did:web:xrd-ss1%3A7183",
        "did:web:xrd-ss1%3A7183:v2:TEST:GOV:1234",
        "did:web:xrd-ss1%3A7183:v1:TEST:GOV",
        "did:web:xrd-ss1%3A7183:v1:TEST:GOV:1234:SUB",
        "did:web:xrd-ss1%3A7183:v1:system:extra",
        "did:web:xrd-ss1%3A7183:v1:DEV%2d:COM:222",
        "did:web:xrd-ss1%3A7183:v1:%2e%2E:COM:222",
        "did:web:xrd-ss1%3A7183:v1:DEV+X:COM:222"})
    void aDidThatDoesNotMapToItsOwnDocumentUrlIsMalformedAndSendsNoRequest(String did) throws Exception {
        var delegate = mock(EdcHttpClient.class);

        var resolution = resolverOver(delegate).resolve(did);

        assertThat(resolution.reason()).isEqualTo(Reason.MALFORMED_DID);
        verifyNoInteractions(delegate);
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 404, 500})
    void aStatusOtherThan200IsNoDocument(int status) throws Exception {
        var stub = start(sharedCa);
        stub.route(DidStubServer.DID_PATH, exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.NO_DOCUMENT);
    }

    @Test
    void aDocumentNamingAnotherDidIsAnIdMismatch() throws Exception {
        var stub = start(sharedCa);
        stub.route(DidStubServer.DID_PATH,
                DidStubServer.respond(200, DidStubServer.minimalDocument("did:web:other.example.org:v1:DEV:COM:222")));
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.ID_MISMATCH);
    }

    @Test
    void anUnparsableBodyIsAFetchFailure() throws Exception {
        var stub = start(sharedCa);
        stub.route(DidStubServer.DID_PATH, DidStubServer.respond(200, "this is not json"));
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
        assertThat(resolution.requestFailed()).isFalse();
    }

    @Test
    void aRefusedConnectionIsAFetchFailure() throws Exception {
        var loopback = InetAddress.getByName("127.0.0.1");
        try (var boundButNotListening = new Socket()) {
            boundButNotListening.bind(new InetSocketAddress(loopback, 0));
            var port = boundButNotListening.getLocalPort();
            assertThatThrownBy(() -> new Socket(loopback, port)).isInstanceOf(ConnectException.class);

            var resolution = resolver().resolve(DidStubServer.didOfPort(port));

            assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
            assertThat(resolution.requestFailed()).isTrue();
        }
    }

    @Test
    void aStatusOtherThan200IsNotARequestFailure() throws Exception {
        var stub = start(sharedCa);
        stub.route(DidStubServer.DID_PATH, DidStubServer.respond(500, "{}"));
        trust(sharedCa);

        assertThat(resolver().resolve(stub.did()).requestFailed()).isFalse();
    }

    @Test
    void anExplicitDefaultPortAndNoPortAreTheSameEndpoint() {
        var withoutPort = SubmittedDidResolver.fetchEndpoint("did:web:xrd-ss1:v1:DEV:COM:1");
        var withPort = SubmittedDidResolver.fetchEndpoint("did:web:xrd-ss1%3A443:v1:DEV:COM:1");
        var otherPort = SubmittedDidResolver.fetchEndpoint("did:web:xrd-ss1%3A9183:v1:DEV:COM:1");

        assertThat(withoutPort).isPresent().isEqualTo(withPort).isNotEqualTo(otherPort);
        assertThat(withoutPort.orElseThrow().port()).isEqualTo(443);
    }

    @Test
    void aTlsHandshakeFailureIsAFetchFailure() throws Exception {
        var stub = start(sharedCa, "127.0.0.2");
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
    }

    @Test
    void aFailingEndpointIsCalledExactlyOnce() throws Exception {
        var stub = start(sharedCa);
        stub.route(DidStubServer.DID_PATH, DidStubServer.respond(500, "{}"));
        trust(sharedCa);

        resolver().resolve(stub.did());

        assertThat(stub.requests(DidStubServer.DID_PATH)).isEqualTo(1);
    }

    @Test
    void anEndpointThatDropsTheConnectionIsCalledExactlyOnce() throws Exception {
        var stub = start(sharedCa);
        stub.route(DidStubServer.DID_PATH, HttpExchange::close);
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
        assertThat(resolution.requestFailed()).isTrue();
        assertThat(stub.requests(DidStubServer.DID_PATH)).isEqualTo(1);
    }

    @Test
    void aRedirectIsNotFollowed() throws Exception {
        var stub = start(sharedCa);
        var target = "/redirected/did.json";
        stub.route(DidStubServer.DID_PATH, exchange -> {
            exchange.getResponseHeaders().add("Location", "https://127.0.0.1:" + stub.port() + target);
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        stub.route(target, DidStubServer.respond(200, DidStubServer.minimalDocument(stub.did())));
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.NO_DOCUMENT);
        assertThat(stub.requests(target)).isZero();
    }

    @Test
    void aPortServingPlainHttpIsNeverSentAPlainRequest() throws Exception {
        var requests = new AtomicInteger();
        var plain = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cleanup.add(() -> plain.stop(0));
        var did = DidStubServer.didOfPort(plain.getAddress().getPort());
        plain.createContext("/", exchange -> {
            requests.incrementAndGet();
            var bytes = DidStubServer.minimalDocument(did).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        plain.start();
        trust(sharedCa);

        var resolution = resolver().resolve(did);

        assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
        assertThat(requests).hasValue(0);
    }

    @Test
    void aCertificateFromACaOutsideTheApprovedListIsRejected() throws Exception {
        var approvedCa = DidStubServer.newCa("Approved CA");
        var stub = start(sharedCa);
        trust(approvedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
    }

    @Test
    void anEmptyApprovedListRejectsAnOtherwiseValidEndpoint() throws Exception {
        var stub = start(sharedCa);
        trust();

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
    }

    @Test
    void aChangedApprovedListIsPickedUpAfterTheReloadCycleWithoutARestart() throws Exception {
        var otherCa = DidStubServer.newCa("Other CA");
        var stub = start(sharedCa);
        trust(otherCa);
        lenient().when(context.getSetting(ArgumentMatchers.eq(TRUST_RELOAD_INTERVAL_SETTING), ArgumentMatchers.anyLong()))
                .thenReturn(1L);
        var resolver = resolver();
        assertThat(resolver.resolve(stub.did()).reason()).isEqualTo(Reason.FETCH_FAILURE);

        trust(otherCa, sharedCa);

        var deadline = Instant.now().plus(RELOAD_WAIT);
        var resolution = resolver.resolve(stub.did());
        while (!resolution.isResolved() && Instant.now().isBefore(deadline)) {
            Thread.sleep(200);
            resolution = resolver.resolve(stub.did());
        }
        assertThat(resolution.isResolved()).isTrue();
    }

    @Test
    void aDocumentWithFieldsUnknownToEdcStillResolves() throws Exception {
        var stub = start(sharedCa);
        var document = identityHubDocument(stub.did());
        assertThatThrownBy(() -> new ObjectMapper().readValue(document, DidDocument.class))
                .hasMessageContaining("Unrecognized field");
        stub.route(DidStubServer.DID_PATH, DidStubServer.respond(200, document));
        trust(sharedCa);

        var resolution = resolver().resolve(stub.did());

        assertThat(resolution.isResolved()).isTrue();
    }

    @Test
    void anUncheckedExceptionFromTheEdcResolverIsAFetchFailure() throws Exception {
        var delegate = mock(EdcHttpClient.class);
        when(delegate.execute(ArgumentMatchers.any(Request.class))).thenThrow(new IllegalStateException("boom"));

        var resolution = resolverOver(delegate).resolve(DidStubServer.didOfPort(7183));

        assertThat(resolution.reason()).isEqualTo(Reason.FETCH_FAILURE);
    }

    @Test
    void aFailedDidProducesNoSevereMonitorEntry() throws Exception {
        var stub = start(sharedCa);
        stub.route(DidStubServer.DID_PATH, DidStubServer.respond(200, "this is not json"));
        trust(sharedCa);

        resolver().resolve(stub.did());

        assertThat(resolverMonitor.severe).isEmpty();
        assertThat(resolverMonitor.debug).contains("Error resolving DID: " + stub.did());
    }

    private DidStubServer start(DidStubServer.Ca ca) throws Exception {
        return start(ca, "127.0.0.1");
    }

    private DidStubServer start(DidStubServer.Ca ca, String certificateAddress) throws Exception {
        var stub = DidStubServer.start(ca, certificateAddress);
        cleanup.add(stub);
        return stub;
    }

    private DidStubServer startForHost(DidStubServer.Ca ca, String hostName) throws Exception {
        var stub = DidStubServer.startForHost(ca, hostName);
        cleanup.add(stub);
        return stub;
    }

    private void trust(DidStubServer.Ca... cas) {
        var infos = new ArrayList<ApprovedConnectorTlsCAInfo>();
        for (var ca : cas) {
            infos.add(new ApprovedConnectorTlsCAInfo("ca-" + infos.size(), ca.certificate(), List.of(), null, null, null));
        }
        approvedCas = List.copyOf(infos);
    }

    private SubmittedDidResolver resolver() {
        return resolver(FETCH_TIMEOUT);
    }

    private SubmittedDidResolver resolver(Duration fetchTimeout) {
        var runtimeClient = tlsExtension.okHttpClient(context);
        return SubmittedDidResolver.create(runtimeClient, new JacksonTypeManager().getMapper(), fetchTimeout, resolverMonitor);
    }

    private SubmittedDidResolver resolverDiallingTheLoopbackForEveryHost() {
        var runtimeClient = tlsExtension.okHttpClient(context).newBuilder()
                .dns(hostName -> List.of(InetAddress.getByName("127.0.0.1")))
                .build();
        return SubmittedDidResolver.create(runtimeClient, new JacksonTypeManager().getMapper(), FETCH_TIMEOUT, resolverMonitor);
    }

    private SubmittedDidResolver resolverOver(EdcHttpClient delegate) {
        var recordingClient = new RecordingEdcHttpClient(delegate);
        var mapper = new JacksonTypeManager().getMapper();
        return new SubmittedDidResolver(new WebDidResolver(recordingClient, true, mapper, resolverMonitor),
                recordingClient, resolverMonitor);
    }

    private static String identityHubDocument(String did) throws IOException {
        try (var stream = SubmittedDidResolverTest.class.getResourceAsStream("/did-document-identity-hub.json")) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8).replace("%DID%", did);
        }
    }

    private static final class CapturingMonitor implements Monitor {
        private final List<String> severe = new CopyOnWriteArrayList<>();
        private final List<String> debug = new CopyOnWriteArrayList<>();

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
            debug.add(supplier.get());
        }
    }
}
