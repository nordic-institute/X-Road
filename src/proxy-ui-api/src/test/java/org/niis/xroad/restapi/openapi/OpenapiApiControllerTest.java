package org.niis.xroad.restapi.openapi;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class OpenapiApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    OpenapiApiController openapiApiController;

    @Test
    @WithMockUser(authorities = { "DOWNLOAD_OPENAPI" })
    public void testDownloadOpenApi() throws IOException {
        ResponseEntity<Resource> response = openapiApiController.downloadOpenApi();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        File file = new File(getClass().getClassLoader().getResource("openapi-definition.yaml").getFile());
        assertEquals(file.length(), response.getBody().contentLength());
    }
}
