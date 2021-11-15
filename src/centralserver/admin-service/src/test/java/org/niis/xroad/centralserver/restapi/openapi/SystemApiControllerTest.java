/**
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.openapi;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.centralserver.openapi.model.ServerAddressUpdateBody;
import org.niis.xroad.centralserver.openapi.model.SystemStatus;
import org.niis.xroad.centralserver.openapi.model.TokenInitStatus;
import org.niis.xroad.centralserver.openapi.model.Version;
import org.niis.xroad.centralserver.restapi.service.SystemParameterService;
import org.niis.xroad.centralserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import javax.validation.ConstraintViolationException;

import static ee.ria.xroad.commonui.SignerProxy.SSL_TOKEN_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.CENTRAL_SERVER_ADDRESS;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.INSTANCE_IDENTIFIER;

public class SystemApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    SystemApiController systemApiController;

    private TokenInfo testSWToken;

    @Before
    public void setup() {
        testSWToken = new TokenTestUtils.TokenInfoBuilder()
                .id(SSL_TOKEN_ID)
                .build();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_VERSION"})
    public void testGetVersionEndpoint() {
        ResponseEntity<Version> response = systemApiController.systemVersion();
        assertNotNull(response, "System Version response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "Version response status code must be 200 ");
        assertNotNull(response.getBody());
        assertEquals(ee.ria.xroad.common.Version.XROAD_VERSION, response.getBody().getInfo());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM_STATUS"})
    public void testGetSystemStatusEndpoint() {
        ResponseEntity<SystemStatus> response = systemApiController.systemStatus();
        assertNotNull(response, "System status response must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "System status response status code must be 200 ");
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getInitializationStatus());
        final var instanceIdentifier = response.getBody().getInitializationStatus().getInstanceIdentifier();
        assertTrue(instanceIdentifier == null || instanceIdentifier.isEmpty());
        final var centralServerAddress = response.getBody().getInitializationStatus().getCentralServerAddress();
        assertTrue(centralServerAddress == null || centralServerAddress.isEmpty());
        assertEquals(TokenInitStatus.NOT_INITIALIZED,
                response.getBody().getInitializationStatus().getSoftwareTokenInitStatus());
        assertNotNull(response.getBody().getHighAvailabilityStatus());
        assertEquals(false, response.getBody().getHighAvailabilityStatus().getIsHaConfigured());
        assertEquals("node_0", response.getBody().getHighAvailabilityStatus().getNodeName());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM_STATUS"})
    public void testUpdateCentralServerAddress() throws Exception {
        when(signerProxyFacade.getToken(SSL_TOKEN_ID)).thenReturn(
                testSWToken); // for the getInitializationStatus
        when(systemParameterService.getParameterValue(
                eq(INSTANCE_IDENTIFIER),
                any()
        )).thenReturn("VALID_CS_ADDRESS_UPDATE_TEST_INSTANCE");
        when(systemParameterService.getParameterValue(eq(CENTRAL_SERVER_ADDRESS), any())).thenReturn(
                "original.server.address.example.com");
        ServerAddressUpdateBody updateBody = new ServerAddressUpdateBody();
        updateBody.setCentralServerAddress("updated.server.address.example.com");

        ResponseEntity<SystemStatus> response = systemApiController.updateCentralServerAddress(updateBody);
        assertNotNull(response);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getInitializationStatus());
        assertNotNull(response.getBody().getHighAvailabilityStatus());
        assertNotNull(response.getBody().getInitializationStatus().getCentralServerAddress());
        verify(systemParameterService).updateOrCreateParameter(SystemParameterService.CENTRAL_SERVER_ADDRESS,
                updateBody.getCentralServerAddress());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM_STATUS"})
    public void testUpdateCentralServerAddressInvalidParam() {
        ServerAddressUpdateBody updateBody = new ServerAddressUpdateBody();
        updateBody.setCentralServerAddress("invalid...address.c");

        Exception exception = assertThrows(ConstraintViolationException.class,
                () -> systemApiController.updateCentralServerAddress(updateBody));
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(
                exception.getMessage().toLowerCase().contains("valid internet domain name or ip address is required"),
                "exception has relevant failure message");
        verify(systemParameterService, times(0)).updateOrCreateParameter(
                eq(SystemParameterService.CENTRAL_SERVER_ADDRESS),
                any());
    }


}
