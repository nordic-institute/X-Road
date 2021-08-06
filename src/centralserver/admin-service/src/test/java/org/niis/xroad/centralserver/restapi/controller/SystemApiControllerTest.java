package org.niis.xroad.centralserver.restapi.controller;

import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.niis.xroad.centralserver.openapi.model.Version;
import org.niis.xroad.centralserver.restapi.openapi.AbstractApiControllerTestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.jupiter.api.Assertions.*;

public class SystemApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    SystemApiController systemApiController;

    @BeforeEach
    void setUp() {
    }


    @Test
    @WithMockUser(authorities = { "VIEW_VERSION" })
    public void testViewVersionEndpoint () {
        ResponseEntity<Version> response = systemApiController.systemVersion();
        assertNotNull(response, "System Version response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "Version response status code must be 200 ");
        assertNotNull(response.getBody());
        assertEquals(ee.ria.xroad.common.Version.XROAD_VERSION, response.getBody().getInfo());

    }



}
