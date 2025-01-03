/*
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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SecurityServerClient;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.api.dto.SubsystemCreationRequest;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapperImpl;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SubsystemRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE;

@ExtendWith(MockitoExtension.class)
public class SubsystemServiceImplTest implements WithInOrder {

    @Mock
    private SubsystemRepository subsystemRepository;
    @Mock
    private XRoadMemberRepository xRoadMemberRepository;
    @Mock
    private ServerClientRepository serverClientRepository;
    @Mock
    private GlobalGroupMemberService globalGroupMemberService;
    @Mock
    private IdentifierRepository<SubsystemIdEntity> subsystemIds;
    @Mock
    private AuditDataHelper auditDataHelper;

    @Spy
    private ClientIdMapper clientIdMapper = new ClientIdMapperImpl();

    @Spy
    @InjectMocks
    private SecurityServerClientMapper securityServerClientMapper = new SecurityServerClientMapperImpl();

    @InjectMocks
    private SubsystemServiceImpl subsystemService;

    @Nested
    @DisplayName("add(Client clientDto)")
    class AddMethod {
        private final String memberName = "member name";
        private final MemberId memberId = MemberId.create("TEST", "CLASS", "MEMBER");
        private final SubsystemId subsystemId = SubsystemId.create("TEST", "CLASS", "MEMBER", "SUBSYSTEM");
        private final SubsystemIdEntity subsystemIdEntity = SubsystemIdEntity.ensure(subsystemId);
        private final XRoadMemberEntity xRoadMember = new XRoadMemberEntity(memberName, memberId, new MemberClassEntity("CLASS", "DESC"));

        @Test
        @DisplayName("should create client when not already present")
        void shouldCreateClientWhenNotAlreadyPresent() {
            when(subsystemIds.findOrCreate(subsystemIdEntity)).thenReturn(subsystemIdEntity);
            when(subsystemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            when(xRoadMemberRepository.findMember(memberId)).thenReturn(Optional.of(xRoadMember));
            when(subsystemRepository.findOneBy(subsystemId)).thenReturn(Optional.empty());
            when(subsystemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            when(xRoadMemberRepository.findMember(memberId)).thenReturn(Optional.of(xRoadMember));

            SecurityServerClient result = subsystemService.add(new SubsystemCreationRequest(memberId, subsystemId));

            assertEquals("MEMBER", result.getIdentifier().getMemberCode());

            verify(subsystemRepository).findOneBy(subsystemId);
            verify(subsystemRepository).save(any());

            verify(auditDataHelper).put(MEMBER_CLASS, memberId.getMemberClass());
            verify(auditDataHelper).put(MEMBER_CODE, memberId.getMemberCode());
            verify(auditDataHelper).put(MEMBER_SUBSYSTEM_CODE, subsystemId.getSubsystemCode());
        }

        @Test
        @DisplayName("should not create client when already present")
        void shouldNotCreateClientWhenAlreadyPresent() {
            SubsystemEntity presentSecurityServerClient = mock(SubsystemEntity.class);
            when(subsystemRepository.findOneBy(subsystemId)).thenReturn(Optional.of(presentSecurityServerClient));

            Executable testable = () -> subsystemService.add(new SubsystemCreationRequest(memberId, subsystemId));

            DataIntegrityException exception = assertThrows(DataIntegrityException.class, testable);
            assertEquals(SUBSYSTEM_EXISTS.getDescription(), exception.getMessage());
            assertThat(exception.getErrorDeviation().getMetadata())
                    .hasSize(1)
                    .containsExactly(subsystemId.toShortString());

            verify(subsystemRepository).findOneBy(subsystemId);
            verify(auditDataHelper).put(MEMBER_CLASS, memberId.getMemberClass());
            verify(auditDataHelper).put(MEMBER_CODE, memberId.getMemberCode());
            verify(auditDataHelper).put(MEMBER_SUBSYSTEM_CODE, subsystemId.getSubsystemCode());
        }
    }

    @Nested
    @DisplayName("deleteSubsystem(ClientId subsystemClientId)")
    class DeleteSubsystem {

        @Mock
        private SubsystemEntity subsystem;

        private final ClientId subsystemClientId = SubsystemId.create(
                "TEST", "CLASS", "MEMBER", "SUBSYSTEM");

        @Test
        @DisplayName("Should delete subsystem")
        void shouldDeleteSubsystem() {
            doReturn(Optional.of(subsystem)).when(subsystemRepository).findOneBy(subsystemClientId);
            doReturn(Set.of()).when(subsystem).getServerClients();

            subsystemService.deleteSubsystem(subsystemClientId);

            verify(subsystemRepository).findOneBy(subsystemClientId);
            verify(subsystem).getServerClients();
            verify(subsystemRepository).deleteById(any());

            verify(auditDataHelper).put(MEMBER_CLASS, subsystemClientId.getMemberClass());
            verify(auditDataHelper).put(MEMBER_CODE, subsystemClientId.getMemberCode());
            verify(auditDataHelper).put(MEMBER_SUBSYSTEM_CODE, subsystemClientId.getSubsystemCode());
        }

        @Test
        @DisplayName("Should not delete subsystem if it does not exist")
        void shouldThrowNotFoundExceptionWhenSubsystemNotFound() {
            doReturn(Optional.empty()).when(subsystemRepository).findOneBy(subsystemClientId);

            Executable testable = () -> subsystemService.deleteSubsystem(subsystemClientId);

            NotFoundException actualThrown = assertThrows(NotFoundException.class, testable);
            assertEquals(SUBSYSTEM_NOT_FOUND.getDescription(), actualThrown.getMessage());
            verify(subsystemRepository).findOneBy(subsystemClientId);

            verify(auditDataHelper).put(MEMBER_CLASS, subsystemClientId.getMemberClass());
            verify(auditDataHelper).put(MEMBER_CODE, subsystemClientId.getMemberCode());
            verify(auditDataHelper).put(MEMBER_SUBSYSTEM_CODE, subsystemClientId.getSubsystemCode());
        }

        @Test
        @DisplayName("Should not delete subsystem if it is already registered")
        void shouldThrowValidationExceptionWhenSubsystemRegistered() {
            doReturn(Optional.of(subsystem)).when(subsystemRepository).findOneBy(subsystemClientId);
            doReturn(Set.of(mock(ServerClient.class))).when(subsystem).getServerClients();

            Executable testable = () -> subsystemService.deleteSubsystem(subsystemClientId);

            ValidationFailureException actualThrown = assertThrows(ValidationFailureException.class, testable);
            assertEquals(ErrorMessage.SUBSYSTEM_REGISTERED_AND_CANNOT_BE_DELETED.getDescription(), actualThrown.getMessage());
            verify(subsystemRepository).findOneBy(subsystemClientId);
            verify(subsystem).getServerClients();
            verify(auditDataHelper).put(MEMBER_CLASS, subsystemClientId.getMemberClass());
            verify(auditDataHelper).put(MEMBER_CODE, subsystemClientId.getMemberCode());
            verify(auditDataHelper).put(MEMBER_SUBSYSTEM_CODE, subsystemClientId.getSubsystemCode());
        }
    }
}
