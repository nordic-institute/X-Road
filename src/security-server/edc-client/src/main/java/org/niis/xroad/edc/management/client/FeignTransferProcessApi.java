package org.niis.xroad.edc.management.client;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.edc.connector.api.management.transferprocess.TransferProcessApi;

public interface FeignTransferProcessApi extends TransferProcessApi {
    @POST
    @Path("request")
    @Override
    JsonArray queryTransferProcesses(JsonObject querySpecJson);

    @GET
    @Path("{id}")
    @Override
    JsonObject getTransferProcess(@PathParam("id") String id);

    @GET
    @Path("/{id}/state")
    @Override
    JsonObject getTransferProcessState(@PathParam("id") String id);

    @POST
    @Override
    JsonObject initiateTransferProcess(JsonObject request);

    @POST
    @Path("/{id}/deprovision")
    @Override
    void deprovisionTransferProcess(@PathParam("id") String id);

    @POST
    @Path("/{id}/terminate")
    @Override
    void terminateTransferProcess(@PathParam("id") String id, JsonObject requestBody);
}
