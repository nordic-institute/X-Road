package org.niis.xroad.edc.management.client;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.edc.connector.api.management.asset.v3.AssetApi;

/**
 * TODO: Workaround for missing annotations.. A bug  in EDC.
 */
public interface FeignAssetApi extends AssetApi {

    @POST
    @Override
    JsonObject createAsset(JsonObject assetJson);

    @POST
    @Path("/request")
    @Override
    JsonArray requestAssets(JsonObject querySpecJson);

    @GET
    @Path("{id}")
    @Override
    JsonObject getAsset(@PathParam("id") String id);

    @DELETE
    @Path("{id}")
    @Override
    void removeAsset(@PathParam("id") String id);

    @PUT
    @Override
    void updateAsset(JsonObject assetJson);
}
