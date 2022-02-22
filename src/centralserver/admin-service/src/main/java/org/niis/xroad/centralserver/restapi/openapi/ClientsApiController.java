package org.niis.xroad.centralserver.restapi.openapi;

import org.niis.xroad.centralserver.openapi.ClientsApi;
import org.niis.xroad.centralserver.openapi.model.ClientType;
import org.niis.xroad.centralserver.openapi.model.PagedClients;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParameters;
import org.springframework.http.ResponseEntity;

public class ClientsApiController implements ClientsApi {
    @Override
    public ResponseEntity<PagedClients> findClients(String q, PagingSortingParameters pagingSorting, String name,
            String instance, String memberClass, String memberCode, String subsystemCode, ClientType clientType,
            String securityServer) {
        throw new RuntimeException("not implemented yet");
    }
}
