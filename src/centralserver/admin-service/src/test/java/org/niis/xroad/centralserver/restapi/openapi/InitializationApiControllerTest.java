package org.niis.xroad.centralserver.restapi.openapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.InitialServerConf;
import org.niis.xroad.centralserver.openapi.model.InitializationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class InitializationApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    InitializationApiController initializationApiController;

    @BeforeEach
    void setUp() {
    }

    @Test
    void getInitializationStatus() {
        ResponseEntity<InitializationStatus> response = initializationApiController.getInitializationStatus();
        assertNotNull(response, "status should be always available");
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.hasBody());

    }

    @Test
    void initCentralServer() {
        InitialServerConf testConf = new InitialServerConf();

        ResponseEntity<Void> response = initializationApiController.initCentralServer(testConf);
    }
}
