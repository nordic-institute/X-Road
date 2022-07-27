/**
 * The MIT License
 *
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

import ee.ria.xroad.common.TestCertUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.centralserver.openapi.model.ClientIdDto;
import org.niis.xroad.centralserver.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestStatusDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.centralserver.openapi.model.SecurityServerIdDto;
import org.niis.xroad.centralserver.openapi.model.XRoadIdDto;
import org.niis.xroad.centralserver.restapi.entity.MemberClass;
import org.niis.xroad.centralserver.restapi.entity.MemberId;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.centralserver.restapi.repository.MemberClassRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementRequestsApiControllerTest extends AbstractApiControllerTestContext {
    @Autowired
    private ManagementRequestsApiController controller;

    @Autowired
    private XRoadMemberRepository members;

    @Autowired
    private SecurityServerRepository servers;

    @Autowired
    private IdentifierRepository<MemberId> memberIds;

    @Autowired
    private MemberClassRepository memberClasses;

    @BeforeEach
    private void setup() {
        MemberClass memberClass = memberClasses.save(new MemberClass("CLASS", "CLASS"));
        MemberId memberId = memberIds.save(MemberId.create("TEST", "CLASS", "MEMBER"));
        XRoadMember member = members.save(new XRoadMember("MEMBER_NAME", memberId, memberClass));

        SecurityServer server = new SecurityServer(member, "TESTSERVER");
        server.setAddress("server.example.org");
        servers.save(server);
    }

    @Test
    @WithMockUser(authorities = {
            "VIEW_MANAGEMENT_REQUESTS",
            "VIEW_MANAGEMENT_REQUEST_DETAILS",
            "ADD_AUTH_CERT_REGISTRATION_REQUEST",
            "REVOKE_AUTH_CERT_REGISTRATION_REQUEST"})
    void testAddRequest() throws Exception {
        SecurityServerIdDto sid = new SecurityServerIdDto();
        sid.setType(XRoadIdDto.TypeEnum.SERVER);
        sid.setInstanceId("TEST");
        sid.setMemberClass("CLASS");
        sid.setMemberCode("MEMBER");
        sid.setServerCode("SERVERCODE");

        AuthenticationCertificateRegistrationRequestDto req = new AuthenticationCertificateRegistrationRequestDto();
        //redundant, but openapi-generator generates a property for the type
        req.setType(ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST);
        req.setSecurityServerId(sid);
        req.setAuthenticationCertificate(TestCertUtil.generateAuthCert());
        req.setOrigin(ManagementRequestOriginDto.CENTER);

        ResponseEntity<ManagementRequestDto> r1 = controller.addManagementRequest(req);
        assertTrue(r1.getStatusCode().is2xxSuccessful());
        assertEquals(ManagementRequestStatusDto.WAITING, r1.getBody().getStatus());

        ResponseEntity<ManagementRequestDto> r2 = controller.getManagementRequest(r1.getBody().getId());
        assertEquals(r2.getBody().getSecurityServerId(), r1.getBody().getSecurityServerId());

        controller.revokeManagementRequest(r1.getBody().getId());
        ResponseEntity<ManagementRequestDto> r3 = controller.getManagementRequest(r2.getBody().getId());
        assertEquals(ManagementRequestStatusDto.REVOKED, r3.getBody().getStatus());
    }

    @Test
    @WithMockUser(authorities = {
            "VIEW_MANAGEMENT_REQUESTS",
            "VIEW_MANAGEMENT_REQUEST_DETAILS",
            "ADD_CLIENT_REGISTRATION_REQUEST",
            "REVOKE_CLIENT_REGISTRATION_REQUEST"})
    public void testAddClientRegRequest() {
        SecurityServerIdDto sid = new SecurityServerIdDto();
        sid.setType(XRoadIdDto.TypeEnum.SERVER);
        sid.setInstanceId("TEST");
        sid.setMemberClass("CLASS");
        sid.setMemberCode("MEMBER");
        sid.setServerCode("TESTSERVER");

        ClientIdDto cid = new ClientIdDto();
        cid.setType(XRoadIdDto.TypeEnum.SUBSYSTEM);
        cid.setInstanceId("TEST");
        cid.setMemberClass("CLASS");
        cid.setMemberCode("MEMBER");
        cid.setSubsystemCode("SUB");

        ClientRegistrationRequestDto req = new ClientRegistrationRequestDto();
        //redundant, but openapi-generator generates a property for the type
        req.setType(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
        req.setSecurityServerId(sid);
        req.setClientId(cid);
        req.setOrigin(ManagementRequestOriginDto.CENTER);

        ResponseEntity<ManagementRequestDto> r1 = controller.addManagementRequest(req);
        assertTrue(r1.getStatusCode().is2xxSuccessful());
        assertEquals(ManagementRequestStatusDto.WAITING, r1.getBody().getStatus());

        controller.revokeManagementRequest(r1.getBody().getId());
        ResponseEntity<ManagementRequestDto> r3 = controller.getManagementRequest(r1.getBody().getId());
        assertEquals(ManagementRequestStatusDto.REVOKED, r3.getBody().getStatus());
    }

}
