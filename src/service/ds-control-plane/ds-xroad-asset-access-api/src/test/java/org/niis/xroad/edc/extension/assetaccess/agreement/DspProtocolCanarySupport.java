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

package org.niis.xroad.edc.extension.assetaccess.agreement;

import com.sun.net.httpserver.HttpServer;
import dev.failsafe.RetryPolicy;
import jakarta.ws.rs.core.Response;
import lombok.experimental.UtilityClass;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.eclipse.edc.controlplane.ProtocolRemoteMessage;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.policy.context.request.spi.RequestScopeExtractorRegistry;
import org.eclipse.edc.protocol.dsp.http.dispatcher.DspHttpRemoteMessageDispatcherImpl;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenDecorator;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Scaffolding shared by the EDC protocol canary tests: a real DSP dispatcher for a given message type and request
 * path, a loopback HTTP server that replays the provider's answer back to that dispatcher, and a validator registry
 * that accepts every incoming request.
 */
@UtilityClass
class DspProtocolCanarySupport {

    static JsonObjectValidatorRegistry acceptingValidators() {
        var validators = mock(JsonObjectValidatorRegistry.class);
        when(validators.validate(any(), any())).thenReturn(ValidationResult.success());
        return validators;
    }

    static <M extends ProtocolRemoteMessage> DspHttpRemoteMessageDispatcherImpl dispatcher(Class<M> messageType, String path) {
        var okHttpClient = new OkHttpClient.Builder().readTimeout(Duration.ofSeconds(5)).build();
        var retryPolicy = RetryPolicy.<okhttp3.Response>builder().withMaxRetries(0).build();
        var httpClient = new EdcHttpClientImpl(okHttpClient, retryPolicy, mock(Monitor.class));
        var identityService = mock(IdentityService.class);
        when(identityService.obtainClientCredentials(anyString(), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));
        var tokenDecorator = mock(TokenDecorator.class);
        when(tokenDecorator.decorate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var scopeExtractors = mock(RequestScopeExtractorRegistry.class);
        when(scopeExtractors.extractScopes(any(), any())).thenReturn(Result.success(Set.of()));
        var dispatcher = new DspHttpRemoteMessageDispatcherImpl(httpClient, identityService, tokenDecorator,
                scopeExtractors, _ -> Result.success("provider-1"));
        dispatcher.registerMessage(messageType,
                message -> new Request.Builder().url(message.getCounterPartyAddress() + path)
                        .post(new FormBody.Builder().build()).build(),
                (_, _) -> new Object());
        return dispatcher;
    }

    static <M extends ProtocolRemoteMessage> String consumerDetail(Response providerResponse,
            DspHttpRemoteMessageDispatcherImpl dispatcher, Function<String, M> messageFactory) throws Exception {
        var body = providerResponse.getEntity().toString().getBytes(StandardCharsets.UTF_8);
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(providerResponse.getStatus(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var message = messageFactory.apply("http://localhost:" + server.getAddress().getPort() + "/dsp");
            var result = dispatcher.dispatch("participant-1", Object.class, message).get(10, TimeUnit.SECONDS);
            assertThat(result.failed()).isTrue();
            return result.getFailureDetail();
        } finally {
            server.stop(0);
        }
    }
}
