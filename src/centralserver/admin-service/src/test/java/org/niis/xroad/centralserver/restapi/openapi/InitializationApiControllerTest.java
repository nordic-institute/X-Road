/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.openapi;

import org.junit.Test;
import org.niis.xroad.centralserver.openapi.model.InitialServerConf;
import org.niis.xroad.centralserver.openapi.model.InitializationStatus;
import org.niis.xroad.centralserver.openapi.model.TokenInitStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import javax.validation.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithMockUser(authorities = {"INIT_CONFIG"})
public class InitializationApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    InitializationApiController initializationApiController;

    @Test
    public void getInitializationStatus() {
        ResponseEntity<InitializationStatus> response = initializationApiController.getInitializationStatus();
        assertNotNull(response, "status should be always available");
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.hasBody());
        assertNotNull(response.getBody());
        assertEquals(
                TokenInitStatus.NOT_INITIALIZED,
                response.getBody().getSoftwareTokenInitStatus(),
                "TokenInit status should be NOT_INITIALIZED before initialization"
        );
        assertTrue(
                response.getBody().getCentralServerAddress().isEmpty(),
                "No Server Address should be initialized yet."
        );

    }

    @Test
    public void initCentralServer() {
        InitialServerConf testConf = new InitialServerConf();
        testConf.centralServerAddress("xroad.example.org")
                .instanceIdentifier("TEST")
                .softwareTokenPin("1234")
                .ignoreWarnings(false);

        ResponseEntity<Void> response = initializationApiController.initCentralServer(testConf);
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());

        ResponseEntity<InitializationStatus> statusResponseEntity =
                initializationApiController.getInitializationStatus();
        assertNotNull(statusResponseEntity.getBody());
        assertEquals(
                TokenInitStatus.INITIALIZED,
                statusResponseEntity.getBody().getSoftwareTokenInitStatus()
        );
        assertEquals("TEST", statusResponseEntity.getBody().getInstanceIdentifier());
        assertEquals("xroad.example.org", statusResponseEntity.getBody().getCentralServerAddress());
    }

    @Test
    public void initCentralServerMissingParams() {
        InitialServerConf testConf = new InitialServerConf();
        testConf.instanceIdentifier("TEST").centralServerAddress("xroad.example.org").softwareTokenPin(null)
                .ignoreWarnings(false);
        ConstraintViolationException constraintViolationException = assertThrows(ConstraintViolationException.class,
                () -> initializationApiController.initCentralServer(testConf));
        assertEquals(1, constraintViolationException.getConstraintViolations().size());

    }
}
