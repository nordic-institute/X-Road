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
package org.niis.xroad.cs.admin.application.openapi;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.application.util.TokenTestUtils;
import org.niis.xroad.cs.admin.rest.api.openapi.SystemApiController;
import org.niis.xroad.cs.openapi.model.CentralServerAddressDto;
import org.niis.xroad.cs.openapi.model.SystemStatusDto;
import org.niis.xroad.cs.openapi.model.TokenInitStatusDto;
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
import static org.niis.xroad.cs.admin.api.service.SystemParameterService.CENTRAL_SERVER_ADDRESS;

public class SystemApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    SystemApiController systemApiController;

    private TokenInfo testSWToken;

    @BeforeEach
    public void setup() {
        testSWToken = new TokenTestUtils.TokenInfoBuilder()
                .id(SSL_TOKEN_ID)
                .build();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_VERSION"})
    public void testGetSystemStatusEndpoint() {
        ResponseEntity<SystemStatusDto> response = systemApiController.getSystemStatus();
        assertNotNull(response, "System status response must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "System status response status code must be 200 ");
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getInitializationStatus());
        final var instanceIdentifier = response.getBody().getInitializationStatus().getInstanceIdentifier();
        assertTrue(instanceIdentifier == null || instanceIdentifier.isEmpty());
        final var centralServerAddress = response.getBody().getInitializationStatus().getCentralServerAddress();
        assertTrue(centralServerAddress == null || centralServerAddress.isEmpty());
        assertEquals(TokenInitStatusDto.NOT_INITIALIZED,
                response.getBody().getInitializationStatus().getSoftwareTokenInitStatus());
        assertNotNull(response.getBody().getHighAvailabilityStatus());
        assertEquals(false, response.getBody().getHighAvailabilityStatus().getIsHaConfigured());
        assertEquals("node_0", response.getBody().getHighAvailabilityStatus().getNodeName());
    }

    @Test
    @WithMockUser(authorities = {"EDIT_CENTRAL_SERVER_ADDRESS"})
    public void testUpdateCentralServerAddress() throws Exception {
        when(signerProxyFacade.getToken(SSL_TOKEN_ID)).thenReturn(
                testSWToken); // for the getInitializationStatus
        when(systemParameterService.getInstanceIdentifier()).thenReturn("VALID_CS_ADDRESS_UPDATE_TEST_INSTANCE");
        when(systemParameterService.getCentralServerAddress()).thenReturn(
                "original.server.address.example.com");
        CentralServerAddressDto centralServerAddress = new CentralServerAddressDto()
                .centralServerAddress("updated.server.address.example.com");

        ResponseEntity<SystemStatusDto> response = systemApiController.updateCentralServerAddress(centralServerAddress);
        assertNotNull(response);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getInitializationStatus());
        assertNotNull(response.getBody().getHighAvailabilityStatus());
        assertNotNull(response.getBody().getInitializationStatus().getCentralServerAddress());
        verify(systemParameterService).updateOrCreateParameter(CENTRAL_SERVER_ADDRESS,
                centralServerAddress.getCentralServerAddress());
    }

    @Test
    @WithMockUser(authorities = {"EDIT_CENTRAL_SERVER_ADDRESS"})
    public void testUpdateCentralServerAddressInvalidParam() {
        CentralServerAddressDto centralServerAddress = new CentralServerAddressDto()
                .centralServerAddress("invalid...address.c");

        Exception exception = assertThrows(ConstraintViolationException.class,
                () -> systemApiController.updateCentralServerAddress(centralServerAddress));
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
        assertTrue(
                exception.getMessage().toLowerCase().contains("valid internet domain name or ip address is required"),
                "exception has relevant failure message");
        verify(systemParameterService, times(0)).updateOrCreateParameter(
                eq(CENTRAL_SERVER_ADDRESS),
                any());
    }

}
