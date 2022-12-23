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
package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.TestCertUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.api.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Needs to be rewritten as unit test. Test just this bean.") //TODO refactor and enable.
@ExtendWith(MockitoExtension.class)
public class ManagementRequestServiceImplTest {
    private final X509Certificate certificate = TestCertUtil.getProducer().certChain[0];
    private final SecurityServerId securityServerId = SecurityServerId.create("TEST", "CLASS", "MEMBER", "SERVER");
    private final SubsystemId subsystemId = SubsystemId.create("TEST", "CLASS", "MEMBER", "SUB");

    @Mock
    private XRoadMemberRepository members;

    @Mock
    private MemberClassRepository memberClasses;

    @InjectMocks
    private ManagementRequestServiceImpl service;

    @BeforeEach
    public void setup() {
        MemberClassEntity memberClass = new MemberClassEntity("CLASS", "CLASS");
        MemberIdEntity memberId = MemberIdEntity.create("TEST", "CLASS", "MEMBER");

        XRoadMemberEntity member = new XRoadMemberEntity("MEMBER_NAME", memberId, memberClass);
        members.save(member);
    }

    @Test
    public void testAddRequest() throws CertificateEncodingException {
        addServer(securityServerId);


        var page = PageRequest.of(0, 10, Sort.by("origin", "securityServerIdentifierId"));
        var pagedResponse = service.findRequests(
                ManagementRequestService.Criteria.builder()
                        .origin(Origin.SECURITY_SERVER)
                        .types(List.of(ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST))
                        .status(ManagementRequestStatus.WAITING)
                        .build(),
                page);
        assertEquals(1, pagedResponse.getTotalElements());
    }

    @Test
    public void testAddRequestAutoApprove() throws CertificateEncodingException {
        //"pre-approve" request
        service.add(new AuthenticationCertificateRegistrationRequest(Origin.CENTER, securityServerId)
                .setAuthCert(certificate.getEncoded())
                .setAddress("server.example.org")
        );
        AuthenticationCertificateRegistrationRequest request =
                new AuthenticationCertificateRegistrationRequest(Origin.SECURITY_SERVER, securityServerId)
                        .setAuthCert(certificate.getEncoded())
                        .setAddress("server.example.org");

        AuthenticationCertificateRegistrationRequest response = service.add(request);

        assertEquals(ManagementRequestStatus.APPROVED, response.getProcessingStatus());
    }

    @Test
    public void testAddClientRegRequest() throws CertificateEncodingException {
        addServer(securityServerId);
        //"Pre-approve"
        service.add(new ClientRegistrationRequest(
                Origin.CENTER,
                securityServerId,
                subsystemId));
        ClientRegistrationRequest request = new ClientRegistrationRequest(
                Origin.SECURITY_SERVER,
                securityServerId,
                subsystemId);

        ClientRegistrationRequest response = service.add(request);

        assertEquals(ManagementRequestStatus.APPROVED, response.getProcessingStatus());
    }

    @Test
    public void testAddClientRegRequestShouldFailIfServerDoesNotExist() {
        ClientRegistrationRequest request = new ClientRegistrationRequest(
                Origin.CENTER,
                securityServerId,
                subsystemId);

        Executable testable = () -> service.add(request);

        assertThrows(DataIntegrityException.class, testable);
    }

    @Test
    public void testShouldFailIfSameOrigin() throws CertificateEncodingException {
        service.add(new AuthenticationCertificateRegistrationRequest(Origin.SECURITY_SERVER, securityServerId)
                .setAuthCert(certificate.getEncoded())
                .setAddress("server.example.org"));
        AuthenticationCertificateRegistrationRequest sameOriginRequest =
                new AuthenticationCertificateRegistrationRequest(Origin.SECURITY_SERVER, securityServerId)
                        .setAuthCert(certificate.getEncoded())
                        .setAddress("server.example.org");

        Executable testable = () -> service.add(sameOriginRequest);

        assertThrows(DataIntegrityException.class, testable);
    }

    @Test
    @SuppressWarnings("checkstyle:hiddenField")
    public void testShouldFailIfConflictingRequests() throws CertificateEncodingException {
        SecurityServerId securityServerId = SecurityServerId.create("TEST", "CLASS", "MEMBER1", "CODE");
        SecurityServerId conflictingSecurityServerId = SecurityServerId.create("TEST", "CLASS", "MEMBER2", "CODE");
        service.add(new AuthenticationCertificateRegistrationRequest(Origin.SECURITY_SERVER, securityServerId)
                .setAuthCert(certificate.getEncoded())
                .setAddress("server.example.org"));
        AuthenticationCertificateRegistrationRequest conflictingRequest =
                new AuthenticationCertificateRegistrationRequest(Origin.CENTER, conflictingSecurityServerId)
                        .setAuthCert(certificate.getEncoded())
                        .setAddress("server.example.org");

        Executable testable = () -> service.add(conflictingRequest);

        assertThrows(DataIntegrityException.class, testable);
    }

    private void addServer(SecurityServerId serverId) throws CertificateEncodingException {
        AuthenticationCertificateRegistrationRequest response =
                service.add(new AuthenticationCertificateRegistrationRequest(Origin.SECURITY_SERVER, serverId)
                        .setAuthCert(certificate.getEncoded())
                        .setAddress("server.example.org"));

        AuthenticationCertificateRegistrationRequest approved = service.approve(response.getId());

        assertEquals(ManagementRequestStatus.APPROVED, approved.getProcessingStatus());
    }
}
