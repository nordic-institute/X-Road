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
 * Quarkus startup bean that registers this proxy as an EDC {@code DataPlaneInstance}
 * on the control plane via a JSON-LD {@code POST <controlPlaneEndpoint>/v1/dataplanes}.
 *
 * <p>On startup ({@code @PostConstruct}), the signaling JAX-RS controller is mounted on the
 * embedded {@link DataPlaneServer}, the server is started, and an initial registration attempt
 * is made. If the control plane is unreachable, a {@code @Scheduled} retry fires every 30 s
 * until registration succeeds.
 *
 * <p>The outbound JSON-LD body is produced via EDC's
 * {@code JsonObjectFromDataPlaneInstanceTransformer} (from {@code transform-lib}) registered
 * in {@link XRoadDpsTransformerRegistry}, ensuring compile-time coupling with the EDC wire shape.
 * Transport is JDK {@link HttpClient} — no third-party HTTP client added.
 */
@Slf4j
@Startup
@ApplicationScoped
@RequiredArgsConstructor
public class ProxyDataPlaneRegistry {

    /** Stable instance id — re-register is an idempotent upsert on CP. */
    private static final String INSTANCE_ID_PREFIX = "xroad-proxy-";
    private static final String SOURCE_TYPE = "http";
    private static final String TRANSFER_TYPE = "Xrd-PULL";
    private static final int HTTP_CLIENT_CONNECT_TIMEOUT_SECONDS = 5;
    private static final int HTTP_STATUS_2XX_MIN = 200;
    private static final int HTTP_STATUS_2XX_MAX = 300;

    private final DataPlaneServerProperties dspProperties;
    private final DataPlaneServer dataPlaneServer;
    private final XRoadDataPlaneSignalingApiController signalingApiController;
    private final TypeTransformerRegistry typeTransformerRegistry;

    @SuppressWarnings("java:S5164") // field is intentionally not threadlocal — shared singleton state
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_CLIENT_CONNECT_TIMEOUT_SECONDS))
            .build();

    private volatile boolean registered = false;

    /**
     * Mounts the signaling controller, starts the embedded data-plane server, and attempts
     * an initial registration on the control plane. A failure here does not abort CDI
     * initialization — the {@link #retryControlPlaneRegistration()} scheduler will retry.
     */
    @PostConstruct
    public void initialize() {
        log.info("Registering data-plane controller..");
        dataPlaneServer.registerJaxRsResource("/full/api/", signalingApiController);
        try {
            dataPlaneServer.start();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR, "Failed to start data-plane server", e);
        }
        attemptControlPlaneRegistration();
    }

    /**
     * Re-registers with the control plane every 30 s as an idempotent upsert heartbeat.
     * The CP-side {@code ServerConfBackedDataPlaneInstanceStore} is in-memory, so any CP restart wipes
     * the instance — without a heartbeat the catalog ends up with empty distributions until the next
     * proxy restart. {@link #isRegistered()} stays {@code true} once the first POST succeeds so the
     * Quarkus readiness probe doesn't flap on transient CP unavailability.
     */
    @Scheduled(every = "30s", delayed = "30s")
    void retryControlPlaneRegistration() {
        attemptControlPlaneRegistration();
    }

    /**
     * Returns {@code true} once the proxy has successfully registered itself on the control plane.
     * Read by {@code DataPlaneRegistrationReadinessCheck}.
     */
    public boolean isRegistered() {
        return registered;
    }

    private void attemptControlPlaneRegistration() {
        log.info("Registering X-Road data plane on control plane.");
        try {
            var instance = buildInstance();
            var body = renderJsonLd(instance);
            var endpoint = URI.create(dspProperties.controlPlaneEndpoint() + "/v1/dataplanes");
            var request = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= HTTP_STATUS_2XX_MIN && response.statusCode() < HTTP_STATUS_2XX_MAX) {
                log.info("X-Road data plane registered on control plane.");
                registered = true;
            } else {
                log.warn("Cannot register data-plane on control-plane — HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Cannot register data-plane on control-plane — will retry.", e);
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

    /**
     * Transforms a {@link DataPlaneInstance} to JSON-LD via EDC's transformer chain,
     * then wraps the result with {@code @context: {"@vocab": EDC_NAMESPACE}} —
     * mirroring {@code RemoteDataPlaneSelectorService.register}.
     */
    private String renderJsonLd(DataPlaneInstance instance) {
        JsonObject jo = typeTransformerRegistry.transform(instance, JsonObject.class)
                .orElseThrow(f -> XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR, f.getFailureDetail()));
        JsonObject wrapped = Json.createObjectBuilder(jo)
                .add(CONTEXT, Json.createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .build();
        return wrapped.toString();
    }
}
