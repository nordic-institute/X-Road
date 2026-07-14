/*
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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.SystemProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.ClientRegistrationRequestRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.cs.admin.api.domain.Origin.CENTER;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;

@ExtendWith(MockitoExtension.class)
class ClientRegistrationRequestHandlerTest {

    private static final String INSTANCE = "CS";
    private static final String MEMBER_CLASS = "MEMBER-CLASS";
    private static final String MEMBER_CODE = "MEMBER-CODE";
    private static final String SERVER_CODE = "SERVER-CODE";

    private final IdentifierRepository<SecurityServerIdEntity> serverIds = mock(IdentifierRepository.class);
    private final IdentifierRepository<ClientIdEntity> clientIds = mock(IdentifierRepository.class);
    private final XRoadMemberRepository members = mock(XRoadMemberRepository.class);
    private final SecurityServerClientRepository<SecurityServerClientEntity> clients = mock(SecurityServerClientRepository.class);
    private final ClientRegistrationRequestRepository clientRegRequests = mock(ClientRegistrationRequestRepository.class);
    private final SecurityServerRepository servers = mock(SecurityServerRepository.class);
    private final ServerClientRepository serverClientRepository = mock(ServerClientRepository.class);
    private final RequestMapper requestMapper = mock(RequestMapper.class);
    private final MemberHelper memberHelper = mock(MemberHelper.class);

    private final ClientRegistrationRequestHandler handler = new ClientRegistrationRequestHandler(
            serverIds, clientIds, members, clients, clientRegRequests, servers, serverClientRepository, requestMapper,
            memberHelper);

    private final SecurityServerId securityServerId = SecurityServerId.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE, SERVER_CODE);
    private final ClientId clientId = MemberId.create(INSTANCE, "OTHER-MEMBER-CLASS", "OTHER-MEMBER-CODE");

    @Test
    void canAutoApproveFalseWhenSubmittedForApprovalAndFlagDisabled() {
        lenient().when(servers.count(securityServerId)).thenReturn(1L);
        lenient().when(members.findMember(clientId)).thenReturn(Optional.of(mock(XRoadMemberEntity.class)));

        final ClientRegistrationRequest request = new ClientRegistrationRequest(SECURITY_SERVER, securityServerId, clientId, null);
        request.setProcessingStatus(SUBMITTED_FOR_APPROVAL);

        try (MockedStatic<SystemProperties> systemProperties = Mockito.mockStatic(SystemProperties.class)) {
            systemProperties.when(SystemProperties::getCenterAutoApproveClientRegRequests).thenReturn(false);

            assertThat(handler.canAutoApprove(request)).isFalse();
        }
    }

    @Test
    void canAutoApproveTrueWhenFlagEnabledAndPreconditionsMet() {
        when(servers.count(securityServerId)).thenReturn(1L);
        when(members.findMember(clientId)).thenReturn(Optional.of(mock(XRoadMemberEntity.class)));

        final ClientRegistrationRequest request = new ClientRegistrationRequest(SECURITY_SERVER, securityServerId, clientId, null);
        request.setProcessingStatus(SUBMITTED_FOR_APPROVAL);

        try (MockedStatic<SystemProperties> systemProperties = Mockito.mockStatic(SystemProperties.class)) {
            systemProperties.when(SystemProperties::getCenterAutoApproveClientRegRequests).thenReturn(true);

            assertThat(handler.canAutoApprove(request)).isTrue();
        }
    }

    @Test
    void canAutoApproveFalseWhenOriginIsCenter() {
        final ClientRegistrationRequest request = new ClientRegistrationRequest(CENTER, securityServerId, clientId, null);
        request.setProcessingStatus(SUBMITTED_FOR_APPROVAL);

        try (MockedStatic<SystemProperties> systemProperties = Mockito.mockStatic(SystemProperties.class)) {
            systemProperties.when(SystemProperties::getCenterAutoApproveClientRegRequests).thenReturn(true);

            assertThat(handler.canAutoApprove(request)).isFalse();
        }
    }
}
