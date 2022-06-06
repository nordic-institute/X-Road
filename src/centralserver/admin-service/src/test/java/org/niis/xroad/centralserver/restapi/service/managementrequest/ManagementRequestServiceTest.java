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
package org.niis.xroad.centralserver.restapi.service.managementrequest;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestType;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.dto.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ClientRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.entity.MemberClass;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.centralserver.restapi.repository.MemberClassRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import javax.transaction.Transactional;

import java.security.cert.CertificateEncodingException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
public class ManagementRequestServiceTest {

    @Autowired
    private ManagementRequestService service;

    @Autowired
    private XRoadMemberRepository members;

    @Autowired
    private IdentifierRepository<ClientId> identifiers;

    @Autowired
    private MemberClassRepository memberClasses;

    @BeforeEach
    public void setup() {
        var memberClass = memberClasses.findByCode("CLASS")
                .orElseGet(() -> memberClasses.save(new MemberClass("CLASS", "CLASS")));
        var member = new XRoadMember(identifiers.merge(ClientId.create("TEST", "CLASS", "MEMBER")), memberClass);
        members.save(member);
    }

    @Test
    public void testAddRequest() {
        var id = SecurityServerId.create("TEST", "CLASS", "MEMBER", "SERVER");
        addServer(id);

        var page = PageRequest.of(0, 10, Sort.by("origin", "securityServerId"));
        var pagedResponse = service.findRequests(
                Origin.SECURITY_SERVER,
                ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST,
                ManagementRequestStatus.APPROVED,
                id,
                page);
        Assert.assertEquals(1, pagedResponse.getTotalElements());
    }

    @Test
    public void testAddRequestAutoApprove() throws CertificateEncodingException {
        var certificate = TestCertUtil.getProducer().certChain[0];
        var id = SecurityServerId.create("TEST", "CLASS", "MEMBER", "SERVER");

        //"pre-approve" request
        service.add(new AuthenticationCertificateRegistrationRequestDto(
                Origin.CENTER,
                id,
                certificate.getEncoded(),
                "server.example.org"));

        var dto = new AuthenticationCertificateRegistrationRequestDto(
                Origin.SECURITY_SERVER,
                id,
                certificate.getEncoded(),
                "server.example.org");

        var response = service.add(dto);
        Assert.assertEquals(ManagementRequestStatus.APPROVED, response.getStatus());
    }

    @Test
    public void testAddClientRegRequest() {
        var serverId = SecurityServerId.create("TEST", "CLASS", "MEMBER", "SERVER");
        addServer(serverId);

        //"Pre-approve"
        service.add(new ClientRegistrationRequestDto(
                Origin.CENTER,
                serverId,
                ClientId.create("TEST", "CLASS", "MEMBER", "SUB")));

        var dto = new ClientRegistrationRequestDto(
                Origin.SECURITY_SERVER,
                serverId,
                ClientId.create("TEST", "CLASS", "MEMBER", "SUB"));

        var response = service.add(dto);
        Assert.assertEquals(ManagementRequestStatus.APPROVED, response.getStatus());
    }

    @Test
    public void testAddClientRegRequestShouldFailIfServerDoesNotExist() {
        var serverId = SecurityServerId.create("TEST", "CLASS", "MEMBER", "SERVER_NOT_EXISTS");
        var dto = new ClientRegistrationRequestDto(
                Origin.CENTER,
                serverId,
                ClientId.create("TEST", "CLASS", "MEMBER", "SUB"));

        assertThrows(DataIntegrityException.class, () -> service.add(dto));
    }

    @Test
    public void testShouldFailIfSameOrigin() throws CertificateEncodingException {
        final var certificate = TestCertUtil.getProducer().certChain[0];
        var id = SecurityServerId.create("TEST", "CLASS", "MEMBER", "CODE");
        var dto = new AuthenticationCertificateRegistrationRequestDto(
                Origin.SECURITY_SERVER,
                id,
                certificate.getEncoded(),
                "server.example.org");
        service.add(dto);
        var dto2 = new AuthenticationCertificateRegistrationRequestDto(
                Origin.SECURITY_SERVER,
                id,
                certificate.getEncoded(),
                "server.example.org");

        //the second request should fail
        assertThrows(DataIntegrityException.class, () -> service.add(dto2));
    }

    @Test
    public void testShouldFailIfConflictingRequests() throws CertificateEncodingException {
        final var certificate = TestCertUtil.getProducer().certChain[0];
        var id = SecurityServerId.create("TEST", "CLASS", "MEMBER1", "CODE");
        var dto = new AuthenticationCertificateRegistrationRequestDto(
                Origin.SECURITY_SERVER,
                id,
                certificate.getEncoded(),
                "server.example.org");
        service.add(dto);

        var id2 = SecurityServerId.create("TEST", "CLASS", "MEMBER2", "CODE");
        var dto2 = new AuthenticationCertificateRegistrationRequestDto(
                Origin.CENTER,
                id2,
                certificate.getEncoded(),
                "server.example.org");

        //the second request should fail
        assertThrows(DataIntegrityException.class, () -> service.add(dto2));
    }

    private void addServer(SecurityServerId serverId) {
        final byte[] certificate;
        try {
            certificate = TestCertUtil.getProducer().certChain[0].getEncoded();
        } catch (CertificateEncodingException e) {
            //should not happen
            throw new AssertionError(e);
        }

        var response = service.add(new AuthenticationCertificateRegistrationRequestDto(
                Origin.SECURITY_SERVER,
                serverId,
                certificate,
                "server.example.org"));

        var approved = service.approve(response.getId());
        Assert.assertEquals(ManagementRequestStatus.APPROVED, approved.getStatus());
    }
}
