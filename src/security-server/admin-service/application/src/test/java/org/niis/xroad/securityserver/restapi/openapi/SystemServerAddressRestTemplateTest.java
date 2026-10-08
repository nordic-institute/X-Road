/*
 * The MIT License
 *
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

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.securityserver.restapi.cache.MaintenanceModeStatus;
import org.niis.xroad.securityserver.restapi.cache.SecurityServerAddressChangeStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerAddressDto;
import org.niis.xroad.securityserver.restapi.service.ManagementRequestSenderService;
import org.niis.xroad.securityserver.restapi.service.SystemService;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HTTP-level status and error code of the server address endpoints when the own server is not registered,
 * the global configuration is unavailable, or the owner is not initialised.
 */
public class SystemServerAddressRestTemplateTest extends AbstractApiControllerTestContext {

    private static final String SERVER_ADDRESS_PATH = "/api/v1/system/server-address";

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    OwnSecurityServerResolver ownSecurityServerResolver;

    private WebTestClient client;

    private ManagementRequestSenderService managementRequestSenderService;

    @Before
    public void setup() {
        client = TestUtils.addApiKeyAuthorizationHeader(webTestClient);
        managementRequestSenderService = mock(ManagementRequestSenderService.class);
        var realSystemService = new SystemService(globalConfService, serverConfService, currentSecurityServerId,
                managementRequestSenderService, mock(AuditDataHelper.class), new SecurityServerAddressChangeStatus(),
                mock(ConfClientRpcClient.class), new MaintenanceModeStatus(), globalConfProvider, mock(ProxyRpcClient.class),
                ownSecurityServerResolver);
        when(systemService.changeSecurityServerAddress(any()))
                .thenAnswer(invocation -> realSystemService.changeSecurityServerAddress(invocation.getArgument(0)));
    }

    @Test
    @WithMockUser(authorities = "CHANGE_SS_ADDRESS")
    public void changeAddressNotRegisteredReturnsConflict() {
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.NotRegistered(TestUtils.OWNER_SERVER_ID));

        ErrorInfo error = changeAddress();

        assertNotNull(error);
        assertEquals("server_not_registered", error.getError().getCode());
        verifyNoInteractions(managementRequestSenderService);
    }

    @Test
    @WithMockUser(authorities = "CHANGE_SS_ADDRESS")
    public void changeAddressGlobalConfUnavailableReturnsConflict() {
        var cause = XrdRuntimeException.systemException(ErrorCode.MALFORMED_GLOBALCONF, "unreadable");
        when(ownSecurityServerResolver.address())
                .thenReturn(new OwnAddress.GlobalConfUnavailable(TestUtils.OWNER_SERVER_ID, cause));

        ErrorInfo error = changeAddress();

        assertNotNull(error);
        assertEquals("global_conf_unavailable", error.getError().getCode());
        verifyNoInteractions(managementRequestSenderService);
    }

    @Test
    @WithMockUser(authorities = "CHANGE_SS_ADDRESS")
    public void changeAddressOwnerNotInitialisedReturnsConflict() {
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.OwnerNotInitialised());

        ErrorInfo error = changeAddress();

        assertNotNull(error);
        assertEquals("server_not_initialized", error.getError().getCode());
        verifyNoInteractions(managementRequestSenderService);
    }

    @Test
    @WithMockUser(authorities = "CHANGE_SS_ADDRESS")
    public void getAddressGlobalConfUnavailableReturnsConflict() {
        var cause = XrdRuntimeException.systemException(ErrorCode.MALFORMED_GLOBALCONF, "unreadable");
        when(ownSecurityServerResolver.address())
                .thenReturn(new OwnAddress.GlobalConfUnavailable(TestUtils.OWNER_SERVER_ID, cause));

        ErrorInfo error = getAddress();

        assertNotNull(error);
        assertEquals("global_conf_unavailable", error.getError().getCode());
    }

    @Test
    @WithMockUser(authorities = "CHANGE_SS_ADDRESS")
    public void getAddressOwnerNotInitialisedReturnsConflict() {
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.OwnerNotInitialised());

        ErrorInfo error = getAddress();

        assertNotNull(error);
        assertEquals("server_not_initialized", error.getError().getCode());
    }

    private ErrorInfo changeAddress() {
        return client.put().uri(SERVER_ADDRESS_PATH)
                .bodyValue(new SecurityServerAddressDto("new.example.org"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorInfo.class)
                .returnResult()
                .getResponseBody();
    }

    private ErrorInfo getAddress() {
        return client.get().uri(SERVER_ADDRESS_PATH)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ErrorInfo.class)
                .returnResult()
                .getResponseBody();
    }
}
