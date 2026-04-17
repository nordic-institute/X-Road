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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.API_KEY_HEADER_VALUE;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.OWNER_SERVER_ID;

/**
 * Test live clients api controller with web test client.
 * Test exists to check proper loading of lazy collections, and
 * open-session-in-view configuration.
 * <p>
 * If data source is altered with WebTestClient (e.g. POST, PUT or DELETE) in this test class,
 * please remember to mark the context dirty with the following annotation:
 * <code>@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)</code>
 */
@Slf4j
public class TransactionHandlingRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    WebTestClient webTestClient;

    private WebTestClient client;

    @Before
    public void setup() {
        client = webTestClient.mutate()
                .defaultHeader("Authorization", API_KEY_HEADER_VALUE)
                .build();

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
        client.get().uri("/api/v1/local-groups/" + 1L)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void localGroupMemberDeleteWorks() {
        String localGroupEndpointUrl = "/api/v1/local-groups/" + 1L;
        client.get().uri(localGroupEndpointUrl)
                .exchange()
                .expectStatus().isOk();

        // add a new member, and delete it. Delete fails if lazy collections are not handled ok
        LocalGroupDto group = client.get().uri(localGroupEndpointUrl)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LocalGroupDto.class)
                .returnResult()
                .getResponseBody();
        assert group != null;
        assertTrue(group.getMembers().isEmpty());

        // add member
        MembersDto members = new MembersDto().addItemsItem(TestUtils.CLIENT_ID_SS1);
        client.post().uri(localGroupEndpointUrl + "/members")
                .bodyValue(members)
                .exchange()
                .expectStatus().isCreated();

        group = client.get().uri(localGroupEndpointUrl)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LocalGroupDto.class)
                .returnResult()
                .getResponseBody();
        assert group != null;
        assertEquals(1, group.getMembers().size());

        // delete member
        client.post().uri(localGroupEndpointUrl + "/members/delete")
                .bodyValue(members)
                .exchange()
                .expectStatus().isNoContent();

        group = client.get().uri(localGroupEndpointUrl)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LocalGroupDto.class)
                .returnResult()
                .getResponseBody();
        assert group != null;
        assertEquals(0, group.getMembers().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientLocalGroupsAreFetched() {
        client.get().uri("/api/v1/clients/"
                        + TestUtils.CLIENT_ID_SS1
                        + "/local-groups")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientTlsCertsAreFetched() {
        client.get().uri("/api/v1/clients/"
                        + TestUtils.CLIENT_ID_SS1
                        + "/tls-certificates")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientServiceDescriptionsAreFetched() {
        client.get().uri("/api/v1/clients/"
                        + TestUtils.CLIENT_ID_SS1
                        + "/service-descriptions")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void serviceDescriptionServicesAreFetched() {
        client.get().uri("/api/v1/service-descriptions/1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void normalClientConverterWorks() {
        ClientDto clientDto = client.get().uri("/api/v1/clients/" + TestUtils.CLIENT_ID_SS1)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ClientDto.class)
                .returnResult()
                .getResponseBody();
        assert clientDto != null;
        assertEquals("M1", clientDto.getMemberCode());
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

        client.get().uri("/api/v1/clients/" + TestUtils.CLIENT_ID_SS1)
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
