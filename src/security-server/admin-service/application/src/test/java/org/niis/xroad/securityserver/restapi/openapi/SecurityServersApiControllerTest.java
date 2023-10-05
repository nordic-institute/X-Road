/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * test securityservers api controller
 */
public class SecurityServersApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    SecurityServersApiController securityServersApiController;

    // our global configuration has only this security server
    public static final SecurityServerId.Conf EXISTING_SERVER_ID = SecurityServerId.Conf.create(
            "XRD2", "GOV", "M4", "server1");
    public static final SecurityServerId.Conf OWNER_SERVER_ID = SecurityServerId.Conf.create(
            "XRD2", "GOV", "M4", "owner");
    private static final String SERVER_ADDRESS = "foo.bar.baz";

    @Before
    public void setup() {
        // securityServerExists = true when parameter = EXISTING_SERVER_ID
        doAnswer(invocation -> invocation.getArguments()[0].equals(EXISTING_SERVER_ID))
                .when(globalConfService).securityServerExists(any());
        when(globalConfFacade.getSecurityServerAddress(any())).thenReturn(SERVER_ADDRESS);
        when(globalConfFacade.getSecurityServers())
                .thenReturn(Arrays.asList(EXISTING_SERVER_ID, OWNER_SERVER_ID));
        when(serverConfService.getSecurityServerId()).thenReturn(OWNER_SERVER_ID);
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
    }

    @Test
    @WithMockUser(authorities = { "INIT_CONFIG" })
    public void getSecurityServerFindsOne() {
        ResponseEntity<SecurityServer> response = securityServersApiController.getSecurityServer(
                "XRD2:GOV:M4:server1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        SecurityServer securityServer = response.getBody();
        assertEquals("XRD2:GOV:M4:server1", securityServer.getId());
        assertEquals("XRD2", securityServer.getInstanceId());
        assertEquals("GOV", securityServer.getMemberClass());
        assertEquals("M4", securityServer.getMemberCode());
        assertEquals("server1", securityServer.getServerCode());
        assertEquals(SERVER_ADDRESS, securityServer.getServerAddress());
    }

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = { "INIT_CONFIG" })
    public void getSecurityServerNoMatch() {
        securityServersApiController.getSecurityServer("XRD2:GOV:M4:server-does-not-exist");
    }

    @Test(expected = ValidationFailureException.class)
    @WithMockUser(authorities = { "INIT_CONFIG" })
    public void getSecurityServerBadRequest() {
        securityServersApiController.getSecurityServer("XRD2:GOV:M4:server:somethingExtra");
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SECURITY_SERVERS" })
    public void getAllSecurityServers() {
        ResponseEntity<Set<SecurityServer>> response = securityServersApiController
                .getSecurityServers(false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<SecurityServer> securityServers = response.getBody();
        assertEquals(2, securityServers.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SECURITY_SERVERS" })
    public void getCurrentSecurityServer() {
        ResponseEntity<Set<SecurityServer>> response = securityServersApiController
                .getSecurityServers(true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<SecurityServer> securityServers = response.getBody();
        assertEquals(1, securityServers.size());
    }
}
