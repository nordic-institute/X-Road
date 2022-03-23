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
import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Test;
import org.niis.xroad.centralserver.openapi.model.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.openapi.model.ClientRegistrationRequest;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestOrigin;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestStatus;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestType;
import org.niis.xroad.centralserver.openapi.model.SecurityServerId;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.niis.xroad.centralserver.restapi.entity.MemberClass;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.centralserver.restapi.repository.MemberClassRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ManagementRequestControllerTest extends AbstractApiControllerTestContext {
    @Autowired
    private ManagementRequestController controller;

    @Autowired
    private XRoadMemberRepository members;

    @Autowired
    private SecurityServerRepository servers;

    @Autowired
    private IdentifierRepository<ClientId> clientIds;

    @Autowired
    private MemberClassRepository memberClasses;

    @Test
    @WithMockUser(authorities = {
            "VIEW_MANAGEMENT_REQUESTS",
            "VIEW_MANAGEMENT_REQUEST_DETAILS",
            "ADD_AUTH_CERT_REGISTRATION_REQUEST",
            "REVOKE_AUTH_CERT_REGISTRATION_REQUEST"})
    public void testAddRequest() throws Exception {
        //setup test data
        setup();

        var sid = new SecurityServerId();
        sid.setType(XRoadId.TypeEnum.SERVER);
        sid.setInstanceId("TEST");
        sid.setMemberClass("CLASS");
        sid.setMemberCode("MEMBER");
        sid.setServerCode("SERVERCODE");

        var req = new AuthenticationCertificateRegistrationRequest();
        //redundant, but openapi-generator generates a property for the type
        req.setType(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST);
        req.setSecurityserverId(sid);
        req.setAuthenticationCertificate(TestCertUtil.generateAuthCert());
        req.setOrigin(ManagementRequestOrigin.CENTER);

        var r1 = controller.addManagementRequest(req);
        assertTrue(r1.getStatusCode().is2xxSuccessful());
        assertEquals(ManagementRequestStatus.WAITING, r1.getBody().getStatus());

        var r2 = controller.getManagementRequest(r1.getBody().getId());
        assertEquals(r2.getBody().getSecurityserverId(), r1.getBody().getSecurityserverId());

        controller.revokeManagementRequest(r1.getBody().getId());
        var r3 = controller.getManagementRequest(r2.getBody().getId());
        assertEquals(ManagementRequestStatus.REVOKED, r3.getBody().getStatus());
    }

    @Test
    @WithMockUser(authorities = {
            "VIEW_MANAGEMENT_REQUESTS",
            "VIEW_MANAGEMENT_REQUEST_DETAILS",
            "ADD_CLIENT_REGISTRATION_REQUEST",
            "REVOKE_CLIENT_REGISTRATION_REQUEST"})
    public void testAddClientRegRequest() throws Exception {
        //setup test data
        setup();

        var sid = new SecurityServerId();
        sid.setType(XRoadId.TypeEnum.SERVER);
        sid.setInstanceId("TEST");
        sid.setMemberClass("CLASS");
        sid.setMemberCode("MEMBER");
        sid.setServerCode("TESTSERVER");

        var cid = new org.niis.xroad.centralserver.openapi.model.ClientId();
        cid.setType(XRoadId.TypeEnum.SUBSYSTEM);
        cid.setInstanceId("TEST");
        cid.setMemberClass("CLASS");
        cid.setMemberCode("MEMBER");
        cid.setSubsystemCode("SUB");

        var req = new ClientRegistrationRequest();
        //redundant, but openapi-generator generates a property for the type
        req.setType(ManagementRequestType.CLIENT_REGISTRATION_REQUEST);
        req.setSecurityserverId(sid);
        req.setClientId(cid);
        req.setOrigin(ManagementRequestOrigin.CENTER);

        var r1 = controller.addManagementRequest(req);
        assertTrue(r1.getStatusCode().is2xxSuccessful());
        assertEquals(ManagementRequestStatus.WAITING, r1.getBody().getStatus());

        controller.revokeManagementRequest(r1.getBody().getId());
        var r3 = controller.getManagementRequest(r1.getBody().getId());
        assertEquals(ManagementRequestStatus.REVOKED, r3.getBody().getStatus());
    }

    private void setup() {
        var memberClass = memberClasses.findByCode("CLASS")
                .orElseGet(() -> memberClasses.save(new MemberClass("CLASS", "CLASS")));
        var memberId = clientIds.merge(ClientId.create("TEST", "CLASS", "MEMBER"));
        var member = new XRoadMember(memberId, memberClass);
        members.save(member);

        var server = new SecurityServer(member, "TESTSERVER");
        server.setAddress("server.example.org");
        servers.save(server);
    }

}
