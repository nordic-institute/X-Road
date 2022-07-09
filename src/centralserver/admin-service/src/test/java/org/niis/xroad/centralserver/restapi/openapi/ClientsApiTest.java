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
package org.niis.xroad.centralserver.restapi.openapi;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.niis.xroad.centralserver.openapi.model.ClientDto;
import org.niis.xroad.centralserver.openapi.model.ClientIdDto;
import org.niis.xroad.centralserver.openapi.model.PagedClientsDto;
import org.niis.xroad.centralserver.openapi.model.XRoadIdDto;
import org.niis.xroad.centralserver.restapi.repository.FlattenedSecurityServerClientViewRepositoryTest;
import org.niis.xroad.centralserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientsApiTest extends AbstractApiRestTemplateTestContext {

    private static final String PATH = "/api/v1/clients";

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    public void addApiKeyAuthorizationHeader() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
    }

    @Nested
    @DisplayName("POST " + PATH)
    public class AddClient {

        @Test
        @WithMockUser(authorities = {"ADD_NEW_MEMBER"})
        @DisplayName("should add client")
        public void addClient(TestInfo testInfo) {
            String memberCode = composeMemberName(testInfo);
            ClientIdDto memberIdDto =  (ClientIdDto) new ClientIdDto()
                    .memberClass("GOV")
                    .memberCode(memberCode)
                    .instanceId("TEST")
                    .type(XRoadIdDto.TypeEnum.MEMBER);
            ClientDto memberDto = new ClientDto()
                    .id("TEST:GOV:" + memberCode)
                    .memberName("memberName")
                    .xroadId(memberIdDto);

            ResponseEntity<ClientDto> response = restTemplate.postForEntity(PATH, memberDto, ClientDto.class);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("memberName", response.getBody().getMemberName());
            assertEquals(memberIdDto, response.getBody().getXroadId()); // fixme: XRDDEV-
        }
    }

    @Nested
    @DisplayName("GET " + PATH)
    public class FindClients {

        @Test
        public void searchUsingSecurityServerId() {
            var uriVariables = new HashMap<String, String>();
            uriVariables.put("securityServerId", "TEST:GOV:M1:server1");

            ResponseEntity<PagedClientsDto> response = restTemplate.getForEntity(
                    "/api/v1/clients?security_server={securityServerId}",
                    PagedClientsDto.class,
                    uriVariables);

            assertNotNull(response);
            assertEquals(200, response.getStatusCodeValue());
            assertNotNull(response.getBody());
            assertEquals(3, response.getBody().getClients().size());
        }

        @Test
        public void searchAll() {
            ResponseEntity<PagedClientsDto> response = restTemplate.getForEntity(
                    "/api/v1/clients", PagedClientsDto.class);

            assertNotNull(response);
            assertEquals(200, response.getStatusCodeValue());
            assertNotNull(response.getBody());
            assertEquals(FlattenedSecurityServerClientViewRepositoryTest.CLIENTS_TOTAL_COUNT,
                    response.getBody().getClients().size());
        }

        @Test
        public void sortByInstanceId() {
            var uriVariables = new HashMap<String, String>();
            uriVariables.put("sort", "xroad_id.instance_id");
            uriVariables.put("desc", "false");
            ResponseEntity<PagedClientsDto> response = restTemplate.getForEntity(
                    "/api/v1/clients?sort={sort}&desc={desc}", PagedClientsDto.class, uriVariables);
            assertEquals("Instance2", response.getBody().getClients().get(0).getXroadId().getInstanceId());

            uriVariables.put("desc", "true");
            response = restTemplate.getForEntity(
                    "/api/v1/clients?sort={sort}&desc={desc}", PagedClientsDto.class, uriVariables);
            assertEquals("TEST", response.getBody().getClients().get(0).getXroadId().getInstanceId());
        }

        @Test
        public void stableSort() {
            // check that we always have secondary sort by id, to guarantee e.g. stable paging
            String clientSearchUrl = "/api/v1/clients?sort={sort}&desc={desc}&instance={instance}";
            var uriVariables = new HashMap<String, String>();
            uriVariables.put("sort", "xroad_id.instance_id");
            uriVariables.put("desc", "false");
            uriVariables.put("instance", "test");

            // clients for instance test, sorted by instance id
            ResponseEntity<PagedClientsDto> response = restTemplate.getForEntity(
                    clientSearchUrl, PagedClientsDto.class, uriVariables);
            assertEquals(200, response.getStatusCodeValue());
            assertAscendingClientIds(response.getBody().getClients());

            uriVariables.put("desc", "true");
            // clients for instance test, sorted by instance id desc
            response = restTemplate.getForEntity(clientSearchUrl, PagedClientsDto.class, uriVariables);
            assertEquals(200, response.getStatusCodeValue());
            assertAscendingClientIds(response.getBody().getClients());

            // all clients, no specified sorting
            response = restTemplate.getForEntity("/api/v1/clients", PagedClientsDto.class);
            assertEquals(200, response.getStatusCodeValue());
            assertAscendingClientIds(response.getBody().getClients());

            uriVariables.put("sort", "id");
            uriVariables.put("desc", "false");
            uriVariables.put("instance", "test");
            // when "primary-sorting" for id, secondary id sort should not change results
            response = restTemplate.getForEntity(clientSearchUrl, PagedClientsDto.class, uriVariables);
            assertEquals(200, response.getStatusCodeValue());
            assertAscendingClientIds(response.getBody().getClients());

            uriVariables.put("desc", "true");
            response = restTemplate.getForEntity(clientSearchUrl, PagedClientsDto.class, uriVariables);
            assertEquals(200, response.getStatusCodeValue());
            assertDescendingClientIds(response.getBody().getClients());
        }

        private void assertAscendingClientIds(List<ClientDto> clients) {
            assertClientIdOrdering(clients, true);
        }

        private void assertDescendingClientIds(List<ClientDto> clients) {
            assertClientIdOrdering(clients, false);
        }

        private void assertClientIdOrdering(List<ClientDto> clients, boolean ascending) {
            Integer previousClientId = null;
            for (ClientDto client : clients) {
                int clientId = Integer.parseInt(client.getId());
                if (previousClientId != null) {
                    boolean orderIsCorrect = false;
                    if (ascending) {
                        orderIsCorrect = clientId > previousClientId;
                    } else {
                        orderIsCorrect = clientId < previousClientId;
                    }
                    assertTrue(orderIsCorrect, "clientIds should be in "
                            + (ascending ? "ascending" : "descending")
                            + " order");
                }
                previousClientId = clientId;
            }
        }
    }

    private static String composeMemberName(TestInfo testInfo) {
        String testClassSimpleName = StringUtils.substringAfterLast(testInfo.getTestClass().get().getName(), ".");
        String testMethodName = testInfo.getTestMethod().get().getName();
        return testClassSimpleName + "." + testMethodName;
    }

}
