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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.openapi.model.MemberGlobalGroupDto;
import org.niis.xroad.centralserver.openapi.model.SecurityServerDto;
import org.niis.xroad.centralserver.restapi.converter.GroupMemberConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.db.SecurityServerDtoConverter;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroupMember;
import org.niis.xroad.centralserver.restapi.entity.MemberId;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClientName;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.GlobalGroupMemberRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerClientNameRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_EXISTS;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest implements WithInOrder {

    @Mock
    private XRoadMemberRepository xRoadMemberRepository;
    @Mock
    private SecurityServerClientNameRepository securityServerClientNameRepository;
    @Mock
    private GlobalGroupMemberRepository globalGroupMemberRepository;

    @Mock
    private SecurityServerDtoConverter securityServerDtoConverter;
    @Spy
    private GroupMemberConverter groupMemberConverter;

    @InjectMocks
    private MemberService memberService;

    @Nested
    @DisplayName("add(Client clientDto)")
    class AddMethod implements WithInOrder {

        @Mock
        private XRoadMember xRoadMember;
        private MemberId memberId = MemberId.create("TEST", "CLASS", "MEMBER");

        @Test
        @DisplayName("should create client when not already present")
        void shouldCreateClientWhenNotAlreadyPresent() {
            XRoadMember persistedXRoadMember = mock(XRoadMember.class);
            String memberName = "member name";
            doReturn(memberName).when(persistedXRoadMember).getName();
            doReturn(memberId).when(xRoadMember).getIdentifier();
            doReturn(memberId).when(persistedXRoadMember).getIdentifier();
            doReturn(Option.none()).when(xRoadMemberRepository).findOneBy(memberId);
            doReturn(persistedXRoadMember).when(xRoadMemberRepository).save(xRoadMember);

            SecurityServerClient result = memberService.add(xRoadMember);

            assertEquals(persistedXRoadMember, result);
            ArgumentCaptor<SecurityServerClientName> captor = ArgumentCaptor.forClass(SecurityServerClientName.class);
            inOrder(persistedXRoadMember).verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findOneBy(memberId);
                inOrder.verify(xRoadMemberRepository).save(xRoadMember);
                inOrder.verify(securityServerClientNameRepository).save(captor.capture());
            });
            assertThat(captor.getValue().getName()).isEqualTo(memberName);
            assertThat(captor.getValue().getIdentifier()).isEqualTo(memberId);
        }

        @Test
        @DisplayName("should not create client when already present")
        void shouldNotCreateClientWhenAlreadyPresent() {
            SecurityServerClient presentSecurityServerClient = mock(SecurityServerClient.class);
            doReturn(memberId).when(xRoadMember).getIdentifier();
            doReturn(Option.of(presentSecurityServerClient)).when(xRoadMemberRepository).findOneBy(memberId);
            String clientIdentifier = memberId.toShortString();

            Executable testable = () -> memberService.add(xRoadMember);

            EntityExistsException exception = assertThrows(EntityExistsException.class, testable);
            assertEquals(MEMBER_EXISTS.getDescription(), exception.getMessage());
            assertThat(exception.getErrorDeviation().getMetadata())
                    .hasSize(1)
                    .containsExactly(clientIdentifier);
            inOrder(presentSecurityServerClient).verify(inOrder -> {
                inOrder.verify(xRoadMember).getIdentifier();
                inOrder.verify(xRoadMemberRepository).findOneBy(memberId);
                inOrder.verify(xRoadMember).getIdentifier();
            });
        }
    }

    @Nested
    @DisplayName("findMember(ClientId clientId)")
    class FindMember implements WithInOrder {

        private ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "MEMBER");

        @Mock
        private XRoadMember xRoadMember;

        @Test
        @DisplayName("Should find client from xRoadMemberRepository")
        void shouldFindClient() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            var result = memberService.findMember(clientId);

            assertTrue(result.isDefined());
        }
    }

    @Nested
    @DisplayName("deleteMember(ClientId clientId)")
    class DeleteMember implements WithInOrder {
        private ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "MEMBER");

        @Mock
        private XRoadMember xRoadMember;

        @Test
        @DisplayName("Should delete client from xRoadMemberRepository")
        void shouldDeleteClient() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            memberService.delete(clientId);

            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
                inOrder.verify(xRoadMemberRepository).delete(xRoadMember);
            });
        }

        @Test
        @DisplayName("Should not delete client when it's non-existent")
        void shouldThrowExceptionWhenClientNotFound() {
            doReturn(Option.none()).when(xRoadMemberRepository).findMember(clientId);

            Executable testable = () -> memberService.delete(clientId);

            NotFoundException actualThrown = assertThrows(NotFoundException.class, testable);
            assertEquals(ErrorMessage.MEMBER_NOT_FOUND.getDescription(), actualThrown.getMessage());
            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
            });
        }
    }

    @Nested
    @DisplayName("updateMemberName(clientId, newName)")
    class UpdateName implements WithInOrder {

        private ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "MEMBER");

        @Mock
        private XRoadMember xRoadMember;
        @Mock
        private SecurityServerClientName securityServerClientName;

        @Test
        @DisplayName("Should set new name")
        void shouldUpdateName() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);
            doReturn(Set.of(securityServerClientName)).when(securityServerClientNameRepository).findByIdentifierIn(any());

            var result = memberService.updateMemberName(clientId, "new name");

            assertTrue(result.isDefined());
            verify(xRoadMember).setName("new name");
            verify(securityServerClientName).setName("new name");
        }
    }

    @Nested
    @DisplayName("findMemberGlobalGroups(ClientId memberId)")
    class FindMemberGlobalGroups implements WithInOrder {

        private final ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "MEMBER");

        @Test
        void shouldReturnMemberGlobalGroups() {
            final GlobalGroupMember memberGroup = new GlobalGroupMember(new GlobalGroup("groupCode"), clientId);
            doReturn(List.of(memberGroup)).when(globalGroupMemberRepository).findMemberGroups(clientId);

            final Set<MemberGlobalGroupDto> result = memberService.getMemberGlobalGroups(clientId);

            inOrder().verify(inOrder -> {
                inOrder.verify(globalGroupMemberRepository).findMemberGroups(clientId);
                inOrder.verify(groupMemberConverter).convertMemberGlobalGroups(List.of(memberGroup));
            });
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("getMemberOwnedServers(ClientId memberId)")
    class GetMemberOwnedServers implements WithInOrder {
        private final ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "MEMBER");

        @Mock
        private XRoadMember xRoadMember;

        @Test
        void shouldReturnMemberOwnedServers() {
            var securityServersMock = Set.of(mock(SecurityServer.class), mock(SecurityServer.class));

            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);
            doReturn(securityServersMock).when(xRoadMember).getOwnedServers();
            doReturn(mock(SecurityServerDto.class), mock(SecurityServerDto.class))
                    .when(securityServerDtoConverter).toDto(isA(SecurityServer.class));

            final Set<SecurityServerDto> result = memberService.getMemberOwnedServers(clientId);

            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
                inOrder.verify(xRoadMember).getOwnedServers();
                inOrder.verify(securityServerDtoConverter, times(securityServersMock.size())).toDto(isA(SecurityServer.class));
            });
            assertEquals(securityServersMock.size(), result.size());
        }

        @Test
        void shouldReturnEmptySetWhenMemberNotFound() {
            doReturn(Option.none()).when(xRoadMemberRepository).findMember(clientId);

            final Set<SecurityServerDto> result = memberService.getMemberOwnedServers(clientId);

            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
            });
            assertTrue(CollectionUtils.isEmpty(result));
        }
    }
}
