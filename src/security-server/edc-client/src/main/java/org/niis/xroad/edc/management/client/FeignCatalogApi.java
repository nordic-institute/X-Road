package org.niis.xroad.edc.management.client;

import jakarta.json.JsonObject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.eclipse.edc.connector.api.management.catalog.CatalogApi;

public interface FeignCatalogApi extends CatalogApi {

    /**
     * TODO: @Suspended is not supported by feign.
     * @param requestBody
     * @return
     */
    @POST
    @Path("/request")
    JsonObject requestCatalogExt(JsonObject requestBody);

    @POST
    @Path("/request")
    @Override
    void requestCatalog(JsonObject requestBody, @Suspended AsyncResponse response);

    @POST
    @Path("dataset/request")
    @Override
    void getDataset(JsonObject requestBody, @Suspended AsyncResponse response);
}
