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
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.signaling.domain.DataFlowPrepareMessage;
import org.eclipse.edc.signaling.domain.DataFlowStartMessage;
import org.eclipse.edc.signaling.domain.DataFlowStartedNotificationMessage;
import org.eclipse.edc.signaling.domain.DataFlowStatusMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowSuspendMessage;

import java.util.Map;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * JAX-RS controller for the X-Road proxy data-plane signaling API.
 * <p>
 * Mounted at {@code /full/api/} context path (via {@link DataPlaneServer}) so that the full
 * URL is reachable at {@code http://<listen-address>:<listen-port>/full/api/v1/dataflows}.
 * <p>
 * Accepts plain Jackson-serialized POJOs from EDC's {@code DataPlaneSignalingClient}
 * ({@code data-plane-signaling-core}). No JSON-LD expansion is performed — Jersey + Jackson
 * deserialize request bodies directly into the new {@code org.eclipse.edc.signaling.domain.*}
 * types. Business logic is delegated to {@link XRoadDataPlaneManager}.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
@Path("/v1/dataflows")
public class XRoadDataPlaneSignalingApiController {

    private final XRoadDataPlaneManager manager;

    /**
     * Prepares a data flow (consumer-side provision).
     * For Xrd-PULL, preparation is immediate — responds with the proxy endpoint address.
     *
     * @param message plain-JSON {@code DataFlowPrepareMessage}
     * @return {@code DataFlowStatusMessage} with {@code dataAddress.endpoint}
     */
    @POST
    @Path("/prepare")
    public DataFlowStatusMessage prepare(DataFlowPrepareMessage message) {
        return manager.prepare(message);
    }

    /**
     * Starts a new data flow (provider-side).
     *
     * @param message plain-JSON {@code DataFlowStartMessage}
     * @return {@code DataFlowStatusMessage} with {@code dataAddress.endpoint}
     */
    @POST
    @Path("/start")
    public DataFlowStatusMessage start(DataFlowStartMessage message) {
        return manager.start(message);
    }

    /**
     * Starts an existing data flow identified by {@code dataFlowId}.
     * The path parameter is advisory; the process ID from the message body takes precedence.
     *
     * @param dataFlowId flow identifier from the URL path
     * @param message    plain-JSON {@code DataFlowStartMessage}
     * @return {@code DataFlowStatusMessage} with {@code dataAddress.endpoint}
     */
    @POST
    @Path("/{id}/start")
    public DataFlowStatusMessage start(@PathParam("id") String dataFlowId, DataFlowStartMessage message) {
        return manager.start(message);
    }

    /**
     * Consumer-side notification that the provider has started the transfer.
     * Required for consumer pull transfers — the control plane treats a non-2xx
     * response as a fatal transfer failure.
     *
     * @param dataFlowId flow identifier from the URL path
     * @param message    plain-JSON {@code DataFlowStartedNotificationMessage} (may carry the provider data address)
     * @return {@code DataFlowStatusMessage} with state {@code STARTED}
     */
    @POST
    @Path("/{id}/started")
    public DataFlowStatusMessage started(@PathParam("id") String dataFlowId, DataFlowStartedNotificationMessage message) {
        return manager.started(dataFlowId);
    }

    /**
     * Completes a data flow. The {@code DataPlaneSignalingClient} sends an empty map body.
     *
     * @param dataFlowId process ID of the flow to complete
     * @param body       ignored — empty map on the wire
     */
    @POST
    @Path("/{id}/completed")
    public void completed(@PathParam("id") String dataFlowId, Map<String, Object> body) {
        manager.completed(dataFlowId);
    }

    /**
     * Returns the current state of a data flow.
     * Debug endpoint — returns a minimal JSON-LD-shaped state object.
     *
     * @param dataFlowId process ID of the flow to query
     * @return JSON object with {@code DataFlowState} type and {@code state} property
     */
    @GET
    @Path("/{id}/state")
    public JsonObject getTransferState(@PathParam("id") String dataFlowId) {
        DataFlowStates state = manager.state(dataFlowId);
        return buildStateResponse(state);
    }

    /**
     * Terminates a data flow. The new {@code DataPlaneSignalingClient} sends an empty map body.
     *
     * @param dataFlowId process ID of the flow to terminate
     * @param body       ignored — empty map on the wire
     */
    @POST
    @Path("/{id}/terminate")
    public void terminate(@PathParam("id") String dataFlowId, Map<String, Object> body) {
        manager.terminate(dataFlowId);
    }

    /**
     * Suspends a data flow. The new {@code DataPlaneSignalingClient} sends a built
     * {@code DataFlowSuspendMessage} (legacy type, may carry an optional reason).
     *
     * @param dataFlowId    process ID of the flow to suspend
     * @param suspendMessage suspend message (reason is extracted if present)
     */
    @POST
    @Path("/{id}/suspend")
    public void suspend(@PathParam("id") String dataFlowId, DataFlowSuspendMessage suspendMessage) {
        manager.suspend(dataFlowId, suspendMessage != null ? suspendMessage.getReason() : null);
    }

    private JsonObject buildStateResponse(DataFlowStates state) {
        return Json.createObjectBuilder()
                .add(TYPE, "DataFlowState")
                .add(EDC_NAMESPACE + "state", state.toString())
                .build();
    }
}
