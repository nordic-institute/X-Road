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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MembersDto;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.model.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.API_KEY_HEADER_VALUE;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.OWNER_SERVER_ID;

/**
 * Test live clients api controller with rest template.
 * Test exists to check proper loading of lazy collections, and
 * open-session-in-view configuration.
 * <p>
 * If data source is altered with TestRestTemplate (e.g. POST, PUT or DELETE) in this test class,
 * please remember to mark the context dirty with the following annotation:
 * <code>@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)</code>
 */
@Slf4j
public class TransactionHandlingRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    @Before
    public void setup() {
        restTemplate.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add("Authorization", API_KEY_HEADER_VALUE);
                    return execution.execute(request, body);
                }));

        doAnswer(invocation -> {
            List<String> encodedClientIds = Arrays.asList("FI:GOV:M1:SS1",
                    "FI:GOV:M1:SS2",
                    "FI:GOV:M1");
            List<MemberInfo> members = new ArrayList<>();
            for (String encodedId : encodedClientIds) {
                ClientId.Conf clientId = clientIdConverter.convertId(encodedId);
                members.add(new MemberInfo(
                        clientId,
                        "mock-name-for-" + encodedId,
                        clientId.isSubsystem() ? ("subsystem-name-for" + encodedId) : null
                ));
            }
            return members;
        }).when(globalConfProvider).getMembers();

        when(currentSecurityServerSignCertificates.getSignCertificateInfos()).thenReturn(new ArrayList<>());
        when(serverConfService.getSecurityServerId()).thenReturn(OWNER_SERVER_ID);
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void localGroupMembersAreFetched() {
        ResponseEntity<Object> response = restTemplate.getForEntity("/api/v1/local-groups/"
                        + 1L,
                Object.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void localGroupMemberDeleteWorks() {
        String localGroupEndpointUrl = "/api/v1/local-groups/" + 1L;
        ResponseEntity<Object> response = restTemplate.getForEntity(localGroupEndpointUrl,
                Object.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // add a new member, and delete it. Delete fails if lazy collections are not handled ok
        ResponseEntity<LocalGroupDto> groupResponse = restTemplate.getForEntity(
                localGroupEndpointUrl,
                LocalGroupDto.class);
        assertEquals(HttpStatus.OK, groupResponse.getStatusCode());
        assertTrue(groupResponse.getBody().getMembers().isEmpty());

        // add member
        MembersDto members = new MembersDto().addItemsItem(TestUtils.CLIENT_ID_SS1);
        response = restTemplate.postForEntity(
                localGroupEndpointUrl + "/members", members, Object.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        groupResponse = restTemplate.getForEntity(localGroupEndpointUrl,
                LocalGroupDto.class);
        assertEquals(HttpStatus.OK, groupResponse.getStatusCode());
        assertEquals(1, groupResponse.getBody().getMembers().size());

        // delete member
        response = restTemplate.postForEntity(
                localGroupEndpointUrl + "/members/delete", members, Object.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        groupResponse = restTemplate.getForEntity(localGroupEndpointUrl,
                LocalGroupDto.class);
        assertEquals(HttpStatus.OK, groupResponse.getStatusCode());
        assertEquals(0, groupResponse.getBody().getMembers().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientLocalGroupsAreFetched() {
        ResponseEntity<Object> response = restTemplate.getForEntity("/api/v1/clients/"
                        + TestUtils.CLIENT_ID_SS1
                        + "/local-groups",
                Object.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientTlsCertsAreFetched() {
        ResponseEntity<Object> response = restTemplate.getForEntity("/api/v1/clients/"
                        + TestUtils.CLIENT_ID_SS1
                        + "/tls-certificates",
                Object.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientServiceDescriptionsAreFetched() {
        ResponseEntity<Object> response = restTemplate.getForEntity("/api/v1/clients/"
                        + TestUtils.CLIENT_ID_SS1
                        + "/service-descriptions",
                Object.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void serviceDescriptionServicesAreFetched() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                "/api/v1/service-descriptions/1",
                Object.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void normalClientConverterWorks() {
        ResponseEntity<ClientDto> clientResponse = restTemplate.getForEntity("/api/v1/clients/" + TestUtils.CLIENT_ID_SS1,
                ClientDto.class);
        assertEquals(HttpStatus.OK, clientResponse.getStatusCode());
        assertEquals("M1", clientResponse.getBody().getMemberCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientConverterCannotLazyLoadPropertiesSinceOsivIsNotUsed() {
        doAnswer((Answer<String>) invocation -> {
            ClientEntity clientEntity = (ClientEntity) invocation.getArguments()[0];
            // cause a lazy loading exception
            clientEntity.getServiceDescriptions().size();
            log.info("lazy loaded server code=" + clientEntity.getConf().getServerCode());
            return null;
        }).when(clientConverter).convert(any(Client.class));

        ResponseEntity<Object> response = restTemplate.getForEntity("/api/v1/clients/" + TestUtils.CLIENT_ID_SS1,
                Object.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
