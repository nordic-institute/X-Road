/**
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ConnectionType;
import org.niis.xroad.restapi.openapi.model.ConnectionTypeWrapper;
import org.niis.xroad.restapi.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.niis.xroad.restapi.util.TestUtils.CLIENT_ID_SS1;
import static org.niis.xroad.restapi.util.TestUtils.addApiKeyAuthorizationHeader;
import static org.niis.xroad.restapi.util.TestUtils.getClientId;

/**
 * Simple tests to validate that basic audit logging works
 */
@ActiveProfiles({ "test", "audit-test" })
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
public class AuditLoggingRestTemplateTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientService clientService;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private AuditEventLoggingFacade auditEventLoggingFacade;

    @Before
    public void setup() {
        addApiKeyAuthorizationHeader(restTemplate);
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void testSuccessAuditLog() {
        ConnectionTypeWrapper connectionTypeWrapper = new ConnectionTypeWrapper();
        connectionTypeWrapper.setConnectionType(ConnectionType.HTTP);
        restTemplate.patchForObject("/api/clients/" + CLIENT_ID_SS1, connectionTypeWrapper, Client.class);
        ClientType clientType = clientService.getLocalClient(getClientId(CLIENT_ID_SS1));
        assertEquals("NOSSL", clientType.getIsAuthentication());

        // verify mock audit log
        verify(auditEventLoggingFacade, times(1)).auditLogSuccess();
    }
}
