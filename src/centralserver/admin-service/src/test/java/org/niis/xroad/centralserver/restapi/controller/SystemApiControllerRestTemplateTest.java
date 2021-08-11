package org.niis.xroad.centralserver.restapi.controller;

import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.niis.xroad.centralserver.openapi.model.Version;
import org.niis.xroad.centralserver.restapi.openapi.AbstractApiControllerTestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.TestPropertySources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.niis.xroad.centralserver.restapi.util.TestUtils.addApiKeyAuthorizationHeader;

public class SystemApiControllerRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        addApiKeyAuthorizationHeader(restTemplate);
    }


    @Test
    @WithMockUser(authorities = { "VIEW_VERSION" })
    public void testViewVersionRestEndpoint () {
        ResponseEntity<Version> response = restTemplate.getForEntity("/api/v1/systemVersion", Version.class);
        assertNotNull(response, "System Version response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "Version response status code must be 200 ");
        assertNotNull(response.getBody());
        assertEquals(ee.ria.xroad.common.Version.XROAD_VERSION, response.getBody().getInfo());

    }



}
