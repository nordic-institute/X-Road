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

import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.centralserver.openapi.model.InitialServerConf;
import org.niis.xroad.centralserver.openapi.model.InitializationStatus;
import org.niis.xroad.centralserver.openapi.model.TokenInitStatus;
import org.niis.xroad.centralserver.restapi.entity.SystemParameter;
import org.niis.xroad.centralserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import javax.validation.ConstraintViolationException;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@WithMockUser(authorities = {"INIT_CONFIG"})
public class InitializationApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    InitializationApiController initializationApiController;

    private InitialServerConf okConf;
    private TokenInfo testSWToken;

    @Before
    public void setup() {
        okConf = new InitialServerConf()
                .centralServerAddress("xroad.example.org")
                .instanceIdentifier("TEST")
                .softwareTokenPin("1234")
                .ignoreWarnings(false);

        testSWToken = new TokenTestUtils.TokenInfoBuilder()
                .id(SignerProxy.SSL_TOKEN_ID)
                .build();

    }

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
    public void getInitializationStatusFailingSignerConnection() throws Exception {

        when(signerProxyFacade.getTokens()).thenThrow(RuntimeException.class);
        assertDoesNotThrow(() -> {
            final ResponseEntity<InitializationStatus> response;
            response = initializationApiController.getInitializationStatus();
            assertTrue(response.hasBody());
            InitializationStatus status = response.getBody();
            assertNotNull(status);
            assertEquals(TokenInitStatus.UNKNOWN, status.getSoftwareTokenInitStatus());
        });
    }

    @Test
    public void getInitializationStatusFromSignerProxy() throws Exception {
        when(signerProxyFacade.getTokens()).thenReturn(Collections.singletonList(testSWToken));
        ResponseEntity<InitializationStatus> statusResponseEntity =
                initializationApiController.getInitializationStatus();
        assertTrue(statusResponseEntity.hasBody());
        InitializationStatus status = statusResponseEntity.getBody();
        assertNotNull(status);
        assertEquals(TokenInitStatus.INITIALIZED, status.getSoftwareTokenInitStatus());
    }

    @Test
    public void initCentralServer() {
        ResponseEntity<Void> response = initializationApiController.initCentralServer(okConf);
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

    @Test
    public void initCentralServerAlreadyInitialized() throws Exception {
        SystemParameter validCentralServerAddressParameter = new SystemParameter();
        validCentralServerAddressParameter.setKey(SystemParameter.CENTRAL_SERVER_ADDRESS);
        validCentralServerAddressParameter.setValue("123.123.123.123");

        SystemParameter validInstanceIdentifierParameter = new SystemParameter();
        validInstanceIdentifierParameter.setKey(SystemParameter.CENTRAL_SERVER_ADDRESS);
        validInstanceIdentifierParameter.setValue("VALID_INSTANCE");

        when(signerProxyFacade.getTokens()).thenReturn(Collections.singletonList(testSWToken));

        when(systemParameterRepository.findSystemParameterByKeyAndHaNodeName(
                SystemParameter.CENTRAL_SERVER_ADDRESS,
                "node_0")
        ).thenReturn(Optional.of(validCentralServerAddressParameter));

        when(systemParameterRepository.findSystemParameterByKeyAndHaNodeName(
                SystemParameter.INSTANCE_IDENTIFIER,
                "node_0")
        )
                .thenReturn(Optional.of(validInstanceIdentifierParameter));
        assertThrows(ConflictException.class, () -> initializationApiController.initCentralServer(okConf));
    }
}
