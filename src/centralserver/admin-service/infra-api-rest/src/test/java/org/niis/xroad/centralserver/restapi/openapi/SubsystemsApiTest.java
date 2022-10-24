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
package org.niis.xroad.centralserver.restapi.openapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.ClientDto;
import org.niis.xroad.centralserver.openapi.model.ClientIdDto;
import org.niis.xroad.centralserver.openapi.model.SubsystemDto;
import org.niis.xroad.centralserver.openapi.model.XRoadIdDto;
import org.niis.xroad.centralserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@Disabled("Has to be revorked for new architecture.")
class SubsystemsApiTest extends AbstractApiRestTemplateTestContext {

    private static final String PATH = "/api/v1/subsystems";

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void addApiKeyAuthorizationHeader() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
    }

    @Nested
    @DisplayName("POST " + PATH)
    class AddSubsystem {

        @Test
        @WithMockUser(authorities = {"ADD_MEMBER_SUBSYSTEM"})
        @DisplayName("should add subsystem")
        public void addSubsystem() {
            ClientIdDto subsystemIdDto = (ClientIdDto) new ClientIdDto()
                    .memberClass("GOV")
                    .memberCode("M1")
                    .subsystemCode("MANAGEMENT")
                    .instanceId("TEST")
                    .type(XRoadIdDto.TypeEnum.SUBSYSTEM);
            ClientDto subsystemDto = new ClientDto()
                    .xroadId(subsystemIdDto);

            ResponseEntity<ClientDto> response = restTemplate.postForEntity(PATH, subsystemDto, ClientDto.class);

            assertNotNull(response);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(subsystemIdDto, response.getBody().getXroadId());
        }
    }

    @Nested
    @DisplayName("GET " + "/api/v1/members/{id}/subsystems")
    @WithMockUser(authorities = {"VIEW_MEMBER_DETAILS"})
    class GetSubsystems {

        @Test
        public void getSubsystems() {
            final String subsystemCode = UUID.randomUUID().toString();
            ClientIdDto subsystemIdDto = (ClientIdDto) new ClientIdDto()
                    .memberClass("GOV")
                    .memberCode("M2")
                    .subsystemCode(subsystemCode)
                    .instanceId("TEST")
                    .type(XRoadIdDto.TypeEnum.SUBSYSTEM);
            ClientDto subsystemDto = new ClientDto()
                    .xroadId(subsystemIdDto);

            var createResponse = restTemplate.postForEntity(PATH, subsystemDto, ClientDto.class);
            assertEquals(HttpStatus.CREATED, createResponse.getStatusCode(), "Failed to create susbsystem");

            var response = restTemplate.getForEntity(
                    "/api/v1/members/{id}/subsystems",
                    SubsystemDto[].class,
                    "TEST:GOV:M2");

            assertNotNull(response);
            assertEquals(200, response.getStatusCodeValue());

            SubsystemDto[] subsystemDtos = response.getBody();

            assertNotNull(subsystemDtos);
            assertEquals(1, subsystemDtos.length);
            assertEquals(subsystemCode, subsystemDtos[0].getSubsystemId().getSubsystemCode());
            assertEquals(0,
                    subsystemDtos[0].getUsedSecurityServers().size());
        }
    }

    @Nested
    @DisplayName("POST " + PATH)
    class UnregisterSubsystem {

        @Test
        @WithMockUser(authorities = {"ADD_SECURITY_SERVER_CLIENT_REG_REQUEST"})
        @DisplayName("Should unregister subsystem from security server")
        void unregisterSubsystem() {
            var uriVariables = Map.of(
                    "subsystemId", "TEST:ORG:222:TEST",
                    "serverId", "TEST:ORG:000:SERVICESS2_CODE"
            );
            var resp = restTemplate.exchange(PATH + "/{subsystemId}/servers/{serverId}",
                    HttpMethod.DELETE, HttpEntity.EMPTY, Void.class, uriVariables);
            assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        }
    }

    @Nested
    @DisplayName("DELETE " + PATH + "/{id}")
    @WithMockUser(authorities = {"REMOVE_MEMBER_SUBSYSTEM"})
    class DeleteSubsystem {

        @Test
        void deleteSubsystem() {
            final String subsystemCode = UUID.randomUUID().toString();
            ClientIdDto subsystemIdDto = (ClientIdDto) new ClientIdDto()
                    .subsystemCode(subsystemCode)
                    .memberClass("GOV")
                    .memberCode("M1")
                    .instanceId("TEST")
                    .type(XRoadIdDto.TypeEnum.SUBSYSTEM);
            ClientDto subsystemDto = new ClientDto()
                    .xroadId(subsystemIdDto);

            var createResponse = restTemplate.postForEntity(PATH, subsystemDto, ClientDto.class);
            assertEquals(HttpStatus.CREATED, createResponse.getStatusCode(), "Failed to create susbsystem");

            var resp = restTemplate.exchange(PATH + "/{id}", HttpMethod.DELETE, HttpEntity.EMPTY, Void.class,
                    "TEST:GOV:M1:" + subsystemCode);

            assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        }
    }

}
