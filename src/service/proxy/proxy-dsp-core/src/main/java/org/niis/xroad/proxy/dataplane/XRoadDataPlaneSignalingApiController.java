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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.dataplane.api.controller.v1.DataPlaneSignalingApi;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowProvisionMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowSuspendMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowTerminateMessage;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * JAX-RS controller implementing EDC's {@link DataPlaneSignalingApi} interface for the X-Road proxy.
 * <p>
 * Mounted at {@code /full/api/} context path (via {@link DataPlaneServer}) so that the full
 * URL matches the {@code ProxyDspProperties.dataFlowEndpoint()} default
 * {@code http://127.0.0.1:5590/full/api/v1/dataflows}.
 * <p>
 * All wire-shape POJOs come from {@code org.eclipse.edc:core-spi}. JSON-LD ↔ POJO conversion
 * is delegated to the {@link TypeTransformerRegistry} produced by {@link XRoadDpsTransformerRegistry}.
 * Business logic is delegated to {@link XRoadDataPlaneManager}.
 * <p>
 * Implements {@code DataPlaneSignalingApi} for compile-time coupling: any signature change
 * in the EDC SPI interface breaks this class immediately.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
@Path("/v1/dataflows")
public class XRoadDataPlaneSignalingApiController implements DataPlaneSignalingApi {

    private final TypeTransformerRegistry typeTransformerRegistry;
    private final JsonLd jsonLd;
    private final XRoadDataPlaneManager manager;

    /**
     * Prepares a data flow (consumer-side provision).
     * For Xrd-PULL, preparation is immediate — responds with the proxy endpoint address.
     *
     * @param message JSON-LD encoded {@code DataFlowProvisionMessage}
     * @return JSON-LD encoded {@code DataFlowResponseMessage}
     */
    @POST
    @Path("/prepare")
    @Override
    public JsonObject prepare(JsonObject message) {
        var provisionMessage = transformIn(message, DataFlowProvisionMessage.class);
        var response = manager.prepare(provisionMessage);
        return transformOut(response);
    }

    /**
     * Starts a new data flow (provider-side).
     *
     * @param message JSON-LD encoded {@code DataFlowStartMessage}
     * @return JSON-LD encoded {@code DataFlowResponseMessage} with {@code dataAddress.endpoint}
     */
    @POST
    @Path("/start")
    @Override
    public JsonObject start(JsonObject message) {
        return startFlow(message);
    }

    /**
     * Starts an existing data flow identified by {@code dataFlowId} (provider-side).
     * The path parameter is advisory; the process ID from the message body takes precedence.
     *
     * @param dataFlowId flow identifier from the URL path
     * @param message    JSON-LD encoded {@code DataFlowStartMessage}
     * @return JSON-LD encoded {@code DataFlowResponseMessage}
     */
    @POST
    @Path("/{id}/start")
    @Override
    public JsonObject start(@PathParam("id") String dataFlowId, JsonObject message) {
        return startFlow(message);
    }

    /**
     * Returns the current state of a data flow.
     *
     * @param dataFlowId process ID of the flow to query
     * @return JSON-LD object with {@code DataFlowState} type and {@code state} property
     */
    @GET
    @Path("/{id}/state")
    @Override
    public JsonObject getTransferState(@PathParam("id") String dataFlowId) {
        DataFlowStates state = manager.state(dataFlowId);
        return buildStateResponse(state);
    }

    /**
     * Terminates a data flow.
     *
     * @param dataFlowId         process ID of the flow to terminate
     * @param terminationMessage JSON-LD encoded {@code DataFlowTerminateMessage}
     */
    @POST
    @Path("/{id}/terminate")
    @Override
    public void terminate(@PathParam("id") String dataFlowId, JsonObject terminationMessage) {
        var msg = transformIn(terminationMessage, DataFlowTerminateMessage.class);
        manager.terminate(dataFlowId, msg);
    }

    /**
     * Suspends a data flow.
     *
     * @param dataFlowId   process ID of the flow to suspend
     * @param suspendMessage JSON-LD encoded {@code DataFlowSuspendMessage}
     */
    @POST
    @Path("/{id}/suspend")
    @Override
    public void suspend(@PathParam("id") String dataFlowId, JsonObject suspendMessage) {
        var msg = transformIn(suspendMessage, DataFlowSuspendMessage.class);
        manager.suspend(dataFlowId, msg);
    }

    /**
     * Health-check endpoint. Returns 204 if the data plane is available.
     */
    @GET
    @Path("/check")
    @Override
    public void checkAvailability() {
        // no-op — Jetty returns 204 by default for void methods
    }

    private JsonObject startFlow(JsonObject message) {
        var startMessage = transformIn(message, DataFlowStartMessage.class);
        var response = manager.start(startMessage);
        return transformOut(response);
    }

    private <T> T transformIn(JsonObject message, Class<T> type) {
        // EDC's signaling client compacts JSON-LD before serializing; the to-transformers expect
        // the expanded form (full IRI keys). EDC's own runtime auto-expands via JerseyJsonLdInterceptor,
        // which we don't register on the proxy data-plane Jetty. Expand inline to keep parity.
        log.info("transformIn target={} body={}", type.getSimpleName(), message);
        return jsonLd.expand(message)
                .compose(expanded -> typeTransformerRegistry.transform(expanded, type))
                .orElseThrow(f -> XrdRuntimeException.systemException(ErrorCode.INVALID_REQUEST,
                        "Failed to transform incoming %s: %s".formatted(type.getSimpleName(), f.getFailureDetail())));
    }

    private JsonObject transformOut(Object value) {
        return typeTransformerRegistry.transform(value, JsonObject.class)
                .orElseThrow(f -> XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR,
                        "Failed to transform outgoing response: %s".formatted(f.getFailureDetail())));
    }

    private JsonObject buildStateResponse(DataFlowStates state) {
        return Json.createObjectBuilder()
                .add(TYPE, "DataFlowState")
                .add(EDC_NAMESPACE + "state", state.toString())
                .build();
    }
}
