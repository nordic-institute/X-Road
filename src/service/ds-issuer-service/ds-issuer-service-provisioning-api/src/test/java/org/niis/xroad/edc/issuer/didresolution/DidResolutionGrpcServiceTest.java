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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Reason;
import org.niis.xroad.edc.issuer.didresolution.SubmittedDidResolver.Resolution;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionOutcome;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionResult;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsReq;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DidResolutionGrpcServiceTest {

    private static final Duration CALL_DEADLINE = Duration.ofSeconds(2);

    private final SubmittedDidResolver resolver = mock(SubmittedDidResolver.class);

    private Server server;
    private ManagedChannel channel;
    private DidResolutionServiceGrpc.DidResolutionServiceBlockingStub client;

    @BeforeEach
    void startServer() throws Exception {
        var service = new DidResolutionGrpcService(new BatchDidResolver(resolver, mock(Monitor.class)), new RpcResponseHandler());
        server = ServerBuilder.forPort(0).addService(service).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        client = DidResolutionServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void everyOutcomeAndReasonCrossesTheWireInRequestOrder() {
        var resolved = didOn("host-a", 1);
        var malformed = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK";
        var noDocument = didOn("host-b", 2);
        var idMismatch = didOn("host-c", 3);
        var fetchFailure = didOn("host-d", 4);
        when(resolver.resolve(resolved)).thenReturn(Resolution.resolved(resolved));
        when(resolver.resolve(malformed)).thenReturn(Resolution.notResolved(malformed, Reason.MALFORMED_DID));
        when(resolver.resolve(noDocument)).thenReturn(Resolution.notResolved(noDocument, Reason.NO_DOCUMENT));
        when(resolver.resolve(idMismatch)).thenReturn(Resolution.notResolved(idMismatch, Reason.ID_MISMATCH));
        when(resolver.resolve(fetchFailure)).thenReturn(Resolution.notResolved(fetchFailure, Reason.FETCH_FAILURE));
        var request = ResolveDidsReq.newBuilder()
                .addAllDids(List.of(fetchFailure, resolved, idMismatch, malformed, noDocument))
                .build();

        var results = client.resolveDids(request).getResultsList();

        assertThat(results).extracting(DidResolutionResult::getDid)
                .containsExactly(fetchFailure, resolved, idMismatch, malformed, noDocument);
        assertThat(results).extracting(DidResolutionResult::getOutcome).containsExactly(
                DidResolutionOutcome.NOT_RESOLVED, DidResolutionOutcome.RESOLVED, DidResolutionOutcome.NOT_RESOLVED,
                DidResolutionOutcome.NOT_RESOLVED, DidResolutionOutcome.NOT_RESOLVED);
        assertThat(results).extracting(DidResolutionResult::getReason).containsExactly(
                DidResolutionReason.FETCH_FAILURE, DidResolutionReason.DID_RESOLUTION_REASON_NONE,
                DidResolutionReason.ID_MISMATCH, DidResolutionReason.MALFORMED_DID, DidResolutionReason.NO_DOCUMENT);
    }

    @Test
    void anEmptyRequestReturnsAnEmptyResponse() {
        var response = client.resolveDids(ResolveDidsReq.getDefaultInstance());

        assertThat(response.getResultsList()).isEmpty();
        verifyNoInteractions(resolver);
    }

    @Test
    void theResponseReturnsBeforeTheCallDeadlineWithUnfinishedEntriesAsFetchFailures() {
        var fast = didOn("host-a", 1);
        var slow = didOn("host-b", 2);
        when(resolver.resolve(fast)).thenReturn(Resolution.resolved(fast));
        when(resolver.resolve(slow)).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofSeconds(30));
            return Resolution.resolved(slow);
        });
        var request = ResolveDidsReq.newBuilder().addAllDids(List.of(slow, fast)).build();

        var results = client.withDeadlineAfter(CALL_DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
                .resolveDids(request).getResultsList();

        assertThat(results).extracting(DidResolutionResult::getDid).containsExactly(slow, fast);
        assertThat(results).extracting(DidResolutionResult::getReason)
                .containsExactly(DidResolutionReason.FETCH_FAILURE, DidResolutionReason.DID_RESOLUTION_REASON_NONE);
        assertThat(results.getFirst().getOutcome()).isEqualTo(DidResolutionOutcome.NOT_RESOLVED);
    }

    private static String didOn(String host, int code) {
        return "did:web:" + host + "%3A7183:v1:DEV:COM:" + code;
    }
}
