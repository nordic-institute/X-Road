package org.niis.xroad.edc.management.client;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.edc.connector.api.management.policy.PolicyDefinitionApi;

public interface FeignPolicyDefinitionApi extends PolicyDefinitionApi {

    @POST
    @Path("request")
    @Override
    JsonArray queryPolicyDefinitions(JsonObject querySpecJson);

    @GET
    @Path("{id}")
    @Override
    JsonObject getPolicyDefinition(@PathParam("id") String id);

    @POST
    @Override
    JsonObject createPolicyDefinition(JsonObject request);

    @DELETE
    @Path("{id}")
    @Override
    void deletePolicyDefinition(@PathParam("id") String id);

    @PUT
    @Path("{id}")
    @Override
    void updatePolicyDefinition(@PathParam("id") String id, JsonObject input);
}
