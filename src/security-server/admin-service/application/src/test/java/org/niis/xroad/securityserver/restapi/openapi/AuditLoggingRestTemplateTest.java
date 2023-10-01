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

import ee.ria.xroad.common.conf.serverconf.model.ClientType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionType;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeWrapper;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.CLIENT_ID_SS1;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.OWNER_SERVER_ID;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.addApiKeyAuthorizationHeader;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.getClientId;

/**
 * Simple tests to validate that basic audit logging works
 */
@ActiveProfiles({ "test", "audit-test" }) // profile change forces to load a new application context
public class AuditLoggingRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ClientService clientService;

    @Before
    public void setup() {
        addApiKeyAuthorizationHeader(restTemplate);
        when(serverConfService.getSecurityServerId()).thenReturn(OWNER_SERVER_ID);
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void testSuccessAuditLog() {
        ConnectionTypeWrapper connectionTypeWrapper = new ConnectionTypeWrapper();
        connectionTypeWrapper.setConnectionType(ConnectionType.HTTP);
        restTemplate.patchForObject("/api/v1/clients/" + CLIENT_ID_SS1, connectionTypeWrapper, Object.class);
        ClientType clientType = clientService.getLocalClient(getClientId(CLIENT_ID_SS1));
        assertEquals("NOSSL", clientType.getIsAuthentication());

        // verify mock audit log
        verify(auditEventLoggingFacade, times(1)).auditLogSuccess();
        ArgumentCaptor<RestApiAuditEvent> eventCaptor = ArgumentCaptor.forClass(RestApiAuditEvent.class);
        ArgumentCaptor<String> userNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipAddressCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditEventLoggingFacade, times(1)).callAuditLoggerLogSuccess(
                eventCaptor.capture(),
                userNameCaptor.capture(),
                ipAddressCaptor.capture(),
                dataCaptor.capture(),
                authCaptor.capture(), urlCaptor.capture());
        Assert.assertEquals(RestApiAuditEvent.SET_CONNECTION_TYPE, eventCaptor.getValue());
        assertEquals("api-key-1", userNameCaptor.getValue());
        Map<String, Object> data = dataCaptor.getValue();
        assertEquals(2, data.size());
        assertTrue(data.containsKey("clientIdentifier"));
        assertTrue(data.containsKey("isAuthentication"));
        assertEquals("HTTP", data.get("isAuthentication"));
        assertEquals("ApiKey", authCaptor.getValue());
        Assert.assertEquals("/api/v1/clients/" + CLIENT_ID_SS1, urlCaptor.getValue());
        verifyNoMoreInteractions(auditEventLoggingFacade);
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void testUnloggedEndpoint() {
        restTemplate.getForObject("/api/v1/clients/" + CLIENT_ID_SS1, Object.class);
        // auditLogSuccess will be called, but no actual calls to AuditLogger.log
        verify(auditEventLoggingFacade, times(1)).auditLogSuccess();
        verifyNoMoreInteractions(auditEventLoggingFacade);
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void testFailureAuditLog() {
        ConnectionTypeWrapper connectionTypeWrapper = new ConnectionTypeWrapper();
        connectionTypeWrapper.setConnectionType(ConnectionType.HTTP);
        String missingClientId = "FI:GOV:MFOOBAR:SS555";

        restTemplate.patchForObject("/api/v1/clients/" + missingClientId, connectionTypeWrapper, Object.class);

        // verify mock audit log
        verify(auditEventLoggingFacade, times(1)).auditLogFail(any());
        ArgumentCaptor<RestApiAuditEvent> eventCaptor = ArgumentCaptor.forClass(RestApiAuditEvent.class);
        ArgumentCaptor<String> userNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipAddressCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditEventLoggingFacade, times(1)).callAuditLoggerLogFailure(
                eventCaptor.capture(),
                userNameCaptor.capture(),
                ipAddressCaptor.capture(),
                reasonCaptor.capture(),
                dataCaptor.capture(),
                authCaptor.capture(), urlCaptor.capture());
        Assert.assertEquals(RestApiAuditEvent.SET_CONNECTION_TYPE, eventCaptor.getValue());
        assertEquals("api-key-1", userNameCaptor.getValue());
        assertTrue(reasonCaptor.getValue().startsWith(
                "org.niis.xroad.securityserver.restapi.service.ClientNotFoundException"));
        // in 7.x this has to be, for some reason:
        //        assertTrue(reasonCaptor.getValue().startsWith("ClientNotFoundException"));
        Map<String, Object> data = dataCaptor.getValue();
        assertEquals(1, data.size());
        assertTrue(data.containsKey("clientIdentifier"));
        assertEquals("ApiKey", authCaptor.getValue());
        assertEquals("/api/v1/clients/" + missingClientId, urlCaptor.getValue());
        verifyNoMoreInteractions(auditEventLoggingFacade);
    }

}
