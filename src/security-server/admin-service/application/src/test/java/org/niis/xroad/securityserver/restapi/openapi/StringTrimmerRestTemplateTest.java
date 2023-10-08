/*
 * The MIT License
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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.securityserver.restapi.openapi.model.Client;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAdd;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroup;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAdd;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test that user inputted strings are being trimmed correctly.
 *
 * TestRestTemplate requests will not be rolled back so the context will need to be reloaded after this test class
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class StringTrimmerRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    public static final String MEMBER_CODE_WITH_SPACES = "  1234  ";
    public static final String MEMBER_CODE_WITHOUT_SPACES = "1234";
    public static final String SUBSYSTEM_CODE_WITH_SPACES = "  SS1  ";
    public static final String SUBSYSTEM_CODE_WITHOUT_SPACES = "SS1";
    public static final String GROUP_CODE_WITH_SPACES = "  GroupCode  ";
    public static final String GROUP_CODE_WITHOUT_SPACES = "GroupCode";
    public static final String GROUP_DESC_WITH_SPACES = "  Description of a group  ";
    public static final String GROUP_DESC_WITHOUT_SPACES = "Description of a group";

    private static final List<String> MEMBER_CLASSES = Arrays.asList(TestUtils.MEMBER_CLASS_GOV,
            TestUtils.MEMBER_CLASS_PRO);

    @Before
    public void setup() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? TestUtils.NAME_FOR + identifier.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
        });

        when(currentSecurityServerSignCertificates.getSignCertificateInfos()).thenReturn(new ArrayList<>());
        when(serverConfService.getSecurityServerId()).thenReturn(TestUtils.OWNER_SERVER_ID);
        when(currentSecurityServerId.getServerId()).thenReturn(TestUtils.OWNER_SERVER_ID);
        when(globalConfService.getMemberClassesForThisInstance()).thenReturn(new HashSet<>(MEMBER_CLASSES));
    }

    @Test
    @WithMockUser(authorities = "ADD_CLIENT")
    public void testAddClientWithSpaces() {
        ClientAdd clientAdd = createClientAdd(MEMBER_CODE_WITH_SPACES, SUBSYSTEM_CODE_WITH_SPACES);
        ResponseEntity<Client> response = restTemplate.postForEntity("/api/v1/clients", clientAdd, Client.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Client addedClient = response.getBody();
        assertNotNull(addedClient);
        assertEquals(MEMBER_CODE_WITHOUT_SPACES, addedClient.getMemberCode());
        assertEquals(SUBSYSTEM_CODE_WITHOUT_SPACES, addedClient.getSubsystemCode());
    }

    @Test
    @WithMockUser(authorities = "ADD_LOCAL_GROUP")
    public void testAddLocalGroupWithSpaces() {
        LocalGroupAdd localGroupAdd = createLocalGroupAdd(GROUP_CODE_WITH_SPACES, GROUP_DESC_WITH_SPACES);
        ResponseEntity<LocalGroup> response = restTemplate.postForEntity("/api/v1/clients/" + TestUtils.CLIENT_ID_SS1
                + "/local-groups", localGroupAdd, LocalGroup.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        LocalGroup addedGroup = response.getBody();
        assertNotNull(addedGroup);
        assertEquals(GROUP_CODE_WITHOUT_SPACES, addedGroup.getCode());
        assertEquals(GROUP_DESC_WITHOUT_SPACES, addedGroup.getDescription());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void testFindClientsWithSpaces() {
        String findClientsApiPath = UriComponentsBuilder.fromPath("/api/v1/clients")
                .queryParam("subsystem_code", SUBSYSTEM_CODE_WITH_SPACES)
                .build(false)
                .toString();
        ParameterizedTypeReference<List<Client>> typeRef = new ParameterizedTypeReference<List<Client>>() {
        };
        ResponseEntity<List<Client>> response = restTemplate.exchange(findClientsApiPath, HttpMethod.GET, null,
                typeRef);
        List<Client> foundClients = response.getBody();
        assertNotNull(foundClients);
        foundClients.forEach(client -> assertEquals(SUBSYSTEM_CODE_WITHOUT_SPACES, client.getSubsystemCode()));
    }

    private ClientAdd createClientAdd(String memberCode, String subsystemCode) {
        Client client = new Client()
                .memberClass("GOV")
                .memberCode(memberCode)
                .subsystemCode(subsystemCode)
                .status(ClientStatus.SAVED);
        return new ClientAdd().client(client);
    }

    private LocalGroupAdd createLocalGroupAdd(String code, String description) {
        return new LocalGroupAdd().code(code).description(description);
    }
}
