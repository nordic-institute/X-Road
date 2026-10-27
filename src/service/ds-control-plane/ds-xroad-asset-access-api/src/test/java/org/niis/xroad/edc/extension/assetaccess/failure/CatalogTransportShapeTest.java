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

package org.niis.xroad.edc.extension.assetaccess.failure;

import com.sun.net.httpserver.HttpServer;
import dev.failsafe.RetryPolicy;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.protocol.dsp.http.dispatcher.DspHttpRemoteMessageDispatcherImpl;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenDecorator;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives EDC's own DSP dispatcher, the one behind every catalog request, at a refused connection, at a server answering
 * 503 and at a socket that never answers.
 */
class CatalogTransportShapeTest {
    @Test
    void catalogRequestAgainstAClosedPortFailsTheFutureAndIsATransportFault() throws Exception {
        var future = dispatcher().dispatch("participant-1", byte[].class,
                catalogRequest("http://" + StubbedNetwork.REFUSING_HOST + "/dsp"));

        var failure = failureOf(future);
        assertThat(failure).isInstanceOfSatisfying(EdcHttpClientException.class,
                httpFailure -> assertThat(httpFailure.getStatusCode()).isZero());
        assertThat(FailureClassifier.classify(failure)).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void catalogRequestAnsweredWith503FailsTheFutureWithTheStatusAndIsOther() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            var future = dispatcher().dispatch("participant-1", byte[].class,
                    catalogRequest("http://localhost:" + server.getAddress().getPort() + "/dsp"));

            var failure = failureOf(future);

            assertThat(failure).isInstanceOfSatisfying(EdcHttpClientException.class,
                    httpFailure -> assertThat(httpFailure.getStatusCode()).isEqualTo(503));
            assertThat(FailureClassifier.classify(failure)).isEqualTo(FailureKind.OTHER);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void catalogRequestNotAnsweredInTimeFailsTheFutureAndIsATransportFault() throws Exception {
        try (var silentServer = new ServerSocket(0)) {
            var future = dispatcher(Duration.ofMillis(200))
                    .dispatch("participant-1", byte[].class, catalogRequest("http://localhost:" + silentServer.getLocalPort() + "/dsp"));

            var failure = failureOf(future);

            assertThat(FailureClassifier.classify(failure)).isEqualTo(FailureKind.TRANSPORT_FAULT);
        }
    }

    private static Throwable failureOf(CompletableFuture<?> future) {
        var failure = catchThrowable(() -> future.get(10, TimeUnit.SECONDS));
        assertThat(failure).isInstanceOf(ExecutionException.class);
        return failure.getCause();
    }

    private static DspHttpRemoteMessageDispatcherImpl dispatcher() {
        return dispatcher(Duration.ofSeconds(5));
    }

    private static DspHttpRemoteMessageDispatcherImpl dispatcher(Duration readTimeout) {
        var okHttpClient = StubbedNetwork.httpClientBuilder().readTimeout(readTimeout).connectTimeout(Duration.ofSeconds(2)).build();
        var retryPolicy = RetryPolicy.<Response>builder().withMaxRetries(0).build();
        var httpClient = new EdcHttpClientImpl(okHttpClient, retryPolicy, mock(Monitor.class));
        var identityService = mock(IdentityService.class);
        when(identityService.obtainClientCredentials(anyString(), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));
        var tokenDecorator = mock(TokenDecorator.class);
        when(tokenDecorator.decorate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var dispatcher = new DspHttpRemoteMessageDispatcherImpl(httpClient, identityService, tokenDecorator,
                mock(PolicyEngine.class), _ -> Result.success("provider-1"));
        dispatcher.registerMessage(CatalogRequestMessage.class,
                message -> new Request.Builder().url(message.getCounterPartyAddress() + "/catalog/request").get().build(),
                (_, _) -> new byte[0]);
        return dispatcher;
    }

    private static CatalogRequestMessage catalogRequest(String counterPartyAddress) {
        return CatalogRequestMessage.Builder.newInstance()
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyAddress(counterPartyAddress)
                .counterPartyId("provider-1")
                .build();
    }
}
