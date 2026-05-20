/*
 * The MIT License
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
package org.niis.xroad.proxy.dataplane;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Performs the HTTP POST registration of this proxy as an EDC {@code DataPlaneInstance}
 * on the control plane, and schedules a 30 s heartbeat retry until the first success.
 * On success, signals {@link DataPlaneReadinessState#markRegistered()}.
 * No knowledge of the Jetty server.
 */
@Slf4j
@Startup
@ApplicationScoped
@RequiredArgsConstructor
public class ControlPlaneRegistrar {

    private static final String INSTANCE_ID_PREFIX = "xroad-proxy-";
    private static final String SOURCE_TYPE = "http";
    private static final String TRANSFER_TYPE = "Xrd-PULL";
    private static final int HTTP_CLIENT_CONNECT_TIMEOUT_SECONDS = 5;
    private static final int HTTP_STATUS_2XX_MIN = 200;
    private static final int HTTP_STATUS_2XX_MAX = 300;

    private final DataPlaneServerProperties dspProperties;
    private final TypeTransformerRegistry typeTransformerRegistry;
    private final DataPlaneReadinessState readinessState;

    @SuppressWarnings("java:S5164")
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_CLIENT_CONNECT_TIMEOUT_SECONDS))
            .build();

    /**
     * Attempts an initial registration on the control plane. A failure here does not abort CDI
     * initialization — the {@link #retryControlPlaneRegistration()} scheduler will retry.
     */
    @PostConstruct
    public void initialize() {
        attemptControlPlaneRegistration();
    }

    /**
     * Re-registers with the control plane every 30 s as an idempotent upsert heartbeat.
     */
    @Scheduled(every = "30s", delayed = "30s")
    void retryControlPlaneRegistration() {
        attemptControlPlaneRegistration();
    }

    void attemptControlPlaneRegistration() {
        log.info("Registering X-Road data plane on control plane.");
        try {
            var body = buildRegistrationBody();
            var response = sendRegistrationRequest(body);
            handleRegistrationResponse(response);
        } catch (Exception e) {
            log.warn("Cannot register data-plane on control-plane — will retry.", e);
            readinessState.markNotRegistered();
        }
    }

    private String buildRegistrationBody() {
        var instance = buildInstance();
        return renderJsonLd(instance);
    }

    private HttpResponse<String> sendRegistrationRequest(String body) throws Exception {
        var endpoint = URI.create(dspProperties.controlPlaneEndpoint() + "/v1/dataplanes");
        var request = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void handleRegistrationResponse(HttpResponse<String> response) {
        if (response.statusCode() >= HTTP_STATUS_2XX_MIN && response.statusCode() < HTTP_STATUS_2XX_MAX) {
            log.info("X-Road data plane registered on control plane.");
            readinessState.markRegistered();
        } else {
            log.warn("Cannot register data-plane on control-plane — HTTP {}: {}", response.statusCode(), response.body());
            readinessState.markNotRegistered();
        }
    }

    private DataPlaneInstance buildInstance() {
        return DataPlaneInstance.Builder.newInstance()
                .id(INSTANCE_ID_PREFIX + dspProperties.participantContextId())
                .url(dspProperties.dataFlowEndpoint())
                .allowedSourceType(SOURCE_TYPE)
                .allowedTransferType(TRANSFER_TYPE)
                .build();
    }

    private String renderJsonLd(DataPlaneInstance instance) {
        JsonObject jo = typeTransformerRegistry.transform(instance, JsonObject.class)
                .orElseThrow(f -> XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR, f.getFailureDetail()));
        JsonObject wrapped = Json.createObjectBuilder(jo)
                .add(CONTEXT, Json.createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .build();
        return wrapped.toString();
    }
}
