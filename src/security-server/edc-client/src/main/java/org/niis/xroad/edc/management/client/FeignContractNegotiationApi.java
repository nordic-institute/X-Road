package org.niis.xroad.edc.management.client;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.edc.connector.api.management.contractnegotiation.ContractNegotiationApi;

public interface FeignContractNegotiationApi extends ContractNegotiationApi {
    @POST
    @Path("/request")
    @Override
    JsonArray queryNegotiations(JsonObject querySpecJson);

    @GET
    @Path("/{id}")
    @Override
    JsonObject getNegotiation(@PathParam("id") String id);

    @GET
    @Path("/{id}/state")
    @Override
    JsonObject getNegotiationState(@PathParam("id") String id);

    @GET
    @Path("/{id}/agreement")
    @Override
    JsonObject getAgreementForNegotiation(@PathParam("id") String negotiationId);

    @POST
    @Override
    JsonObject initiateContractNegotiation(JsonObject requestObject);

    @POST
    @Path("/{id}/terminate")
    @Override
    void terminateNegotiation(@PathParam("id") String id, JsonObject terminateNegotiation);
}
