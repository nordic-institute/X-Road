package org.niis.xroad.edc.management.client;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.edc.connector.api.management.contractdefinition.ContractDefinitionApi;

public interface FeignContractDefinitionApi extends ContractDefinitionApi {

    @POST
    @Path("/request")
    @Override
    JsonArray queryAllContractDefinitions(JsonObject querySpecJson);

    @GET
    @Path("{id}")
    @Override
    JsonObject getContractDefinition(@PathParam("id") String id);

    @POST
    @Override
    JsonObject createContractDefinition(JsonObject createObject);

    @DELETE
    @Path("{id}")
    @Override
    void deleteContractDefinition(@PathParam("id") String id);

    @PUT
    @Override
    void updateContractDefinition(JsonObject updateObject);
}
