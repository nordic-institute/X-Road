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

import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled("Has to be revorked for new architecture.")
@ExtendWith(MockitoExtension.class)
public class MembersApiControllerTest implements WithInOrder {
/*
    @Mock
    private MemberService memberService;
    @Mock
    private AuditDataHelper auditData;
    @Mock
    private ClientDtoConverter clientDtoConverter;
    @Mock
    private GroupMemberConverter groupMemberConverter;
    @Mock
    private SecurityServerDtoConverter securityServerDtoConverter;
    @Spy
    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    @InjectMocks
    private MembersApiController membersApiController;

    private static final String MEMBER_NAME = "MEMBER_NAME";
    private static final String MEMBER_CLASS = "MEMBER_NAME";
    private static final String MEMBER_CODE = "MEMBER_CODE";

    @Nested
    @DisplayName("addMember(ClientDto clientDto)")
    class AddClientMethod implements WithInOrder {

        @Mock
        private ClientDto newClientDto;
        @Mock
        private ClientIdDto clientIdDto;
        @Mock
        private ClientDto persistedClientDto;
        @Mock
        private XRoadMember newXRoadMember;
        @Mock
        private XRoadMember persistedXRoadMember;

        @Mock
        Function<SecurityServerClient, ? extends SecurityServerClient> expectTypeFn;

        @Test
        @DisplayName("should add member successfully")
        public void shouldAddMemberSuccessfully() {
            doReturn(MEMBER_NAME).when(newClientDto).getMemberName();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_NAME, MEMBER_NAME);
            doReturn(clientIdDto).when(newClientDto).getXroadId();
            doReturn(MEMBER_CLASS).when(clientIdDto).getMemberClass();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doReturn(newXRoadMember).when(clientDtoConverter).fromDto(newClientDto);
            doReturn(expectTypeFn).when(clientDtoConverter).expectType(XRoadMember.class);
            doReturn(newXRoadMember).when(expectTypeFn).apply(newXRoadMember);
            doReturn(persistedXRoadMember).when(memberService).add(newXRoadMember);
            doReturn(persistedClientDto).when(clientDtoConverter).toDto(persistedXRoadMember);

            ResponseEntity<ClientDto> result = membersApiController.addMember(newClientDto);

            assertNotNull(result);
            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertEquals(persistedClientDto, result.getBody());
            inOrder().verify(inOrder -> {
                inOrder.verify(newClientDto).getMemberName();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_NAME, MEMBER_NAME);
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
                inOrder.verify(clientIdDto).getMemberCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
                inOrder.verify(clientDtoConverter).fromDto(newClientDto);
                inOrder.verify(clientDtoConverter).expectType(XRoadMember.class);
                inOrder.verify(expectTypeFn).apply(newXRoadMember);
                inOrder.verify(memberService).add(newXRoadMember);
                inOrder.verify(clientDtoConverter).toDto(persistedXRoadMember);
            });
        }

        @Test
        @DisplayName("should fail while trying to add illegal type of security server client")
        public void shouldFailWhileTryingToAddIllegalTypeSecurityServerClient() {
            IllegalArgumentException expectedThrows = mock(IllegalArgumentException.class);
            doReturn(MEMBER_NAME).when(newClientDto).getMemberName();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_NAME, MEMBER_NAME);
            doReturn(clientIdDto).when(newClientDto).getXroadId();
            doReturn(MEMBER_CLASS).when(clientIdDto).getMemberClass();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
            doReturn(newXRoadMember).when(clientDtoConverter).fromDto(newClientDto);
            doReturn(expectTypeFn).when(clientDtoConverter).expectType(XRoadMember.class);
            doThrow(expectedThrows).when(expectTypeFn).apply(newXRoadMember);

            Executable testable = () -> membersApiController.addMember(newClientDto);

            IllegalArgumentException actualThrows = assertThrows(IllegalArgumentException.class, testable);
            assertEquals(expectedThrows, actualThrows);
            inOrder().verify(inOrder -> {
                inOrder.verify(newClientDto).getMemberName();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_NAME, MEMBER_NAME);
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
                inOrder.verify(clientIdDto).getMemberCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
                inOrder.verify(clientDtoConverter).fromDto(newClientDto);
                inOrder.verify(clientDtoConverter).expectType(XRoadMember.class);
                inOrder.verify(expectTypeFn).apply(newXRoadMember);
            });
        }

        @Test
        @DisplayName("should fail while trying to add to repository")
        public void shouldFailWhileTryingToAddToRepository() {
            EntityExistsException expectedThrows = mock(EntityExistsException.class);
            doReturn(MEMBER_NAME).when(newClientDto).getMemberName();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_NAME, MEMBER_NAME);
            doReturn(clientIdDto).when(newClientDto).getXroadId();
            doReturn(MEMBER_CLASS).when(clientIdDto).getMemberClass();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doReturn(newXRoadMember).when(clientDtoConverter).fromDto(newClientDto);
            doReturn(expectTypeFn).when(clientDtoConverter).expectType(XRoadMember.class);
            doReturn(newXRoadMember).when(expectTypeFn).apply(newXRoadMember);
            doThrow(expectedThrows).when(memberService).add(newXRoadMember);

            Executable testable = () -> membersApiController.addMember(newClientDto);

            EntityExistsException actualThrows = assertThrows(EntityExistsException.class, testable);
            assertEquals(expectedThrows, actualThrows);
            inOrder().verify(inOrder -> {
                inOrder.verify(newClientDto).getMemberName();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_NAME, MEMBER_NAME);
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
                inOrder.verify(clientIdDto).getMemberCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
                inOrder.verify(clientDtoConverter).fromDto(newClientDto);
                inOrder.verify(clientDtoConverter).expectType(XRoadMember.class);
                inOrder.verify(expectTypeFn).apply(newXRoadMember);
                inOrder.verify(memberService).add(newXRoadMember);
            });
        }
    }

    @Nested
    class GetMember implements WithInOrder {
        @Mock
        private XRoadMember xRoadMember;
        @Mock
        private ClientDto clientDto;

        private final ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "CODE");
        private final String encodedClientId = "TEST:CLASS:CODE";
        private final String notExistingEncodedClientId = "TEST:MEMBER:DOES-NOT-EXIST";

        @Test
        @DisplayName("Should return client with given id")
        void shouldReturnMember() {
            doReturn(Option.of(xRoadMember)).when(memberService).findMember(clientId);
            doReturn(clientDto).when(clientDtoConverter).toDto(xRoadMember);

            var result = membersApiController.getMember(encodedClientId);

            assertSame(clientDto, result.getBody());
            inOrder(auditData, memberService).verify(inOrder -> {
                inOrder.verify(auditData).put(RestApiAuditProperty.CLIENT_IDENTIFIER, clientId);
                inOrder.verify(memberService).findMember(clientId);
                inOrder.verify(clientDtoConverter).toDto(xRoadMember);
            });
        }

        @Test
        @DisplayName("Should throw NotFoundException if member with given id is not in database")
        void shouldThrowNotFoundException() {
            doReturn(Option.none()).when(memberService).findMember(any());
            Executable testable = () -> membersApiController.getMember(notExistingEncodedClientId);

            assertThrows(
                    NotFoundException.class,
                    testable);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "INVALID-FORMAT",
                "TEST:CLASS:CODE:SUBSYSTEM"})
        @DisplayName("Should throw BadRequest if called with invalid id")
        void shouldThrowBadRequestException(String id) {
            Executable testable = () -> membersApiController.getMember(id);

            var thrown = assertThrows(
                    BadRequestException.class,
                    testable,
                    "Expecting BadRequestException when called with invalid member id");

            assertEquals("Invalid member id", thrown.getMessage());
        }
    }

    @Nested
    class DeleteMember implements WithInOrder {
        @Mock
        private XRoadMember xRoadMember;

        private final ClientId clientId = ClientId.Conf.create("INSTANCE", "CLASS", "CODE");
        private final String encodedClientId = "INSTANCE:CLASS:CODE";
        private final String notExistingEncodedClientId = "INSTANCE:MEMBER:NON-EXISTENT";

        @Test
        @DisplayName("Should delete client with given id")
        void shouldReturnMember() {
            var result = membersApiController.deleteMember(encodedClientId);

            assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
            inOrder(auditData, memberService).verify(inOrder -> {
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, clientId.getMemberClass());
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, clientId.getMemberCode());
                inOrder.verify(memberService).delete(clientId);
            });
        }
    }

    @Nested
    class UpdateMemberName implements WithInOrder {
        @Mock
        private XRoadMember xRoadMember;
        @Mock
        private ClientDto clientDto;

        private final ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "CODE");
        private final String encodedClientId = "TEST:CLASS:CODE";
        private final String notExistingEncodedClientId = "TEST:MEMBER:DOES-NOT-EXIST";
        private final String newName = "NEW NAME";

        @Test
        @DisplayName("Should return client with updated name")
        void shouldReturnMember() {
            doReturn(Option.of(xRoadMember)).when(memberService).updateMemberName(clientId, newName);
            doReturn(clientDto).when(clientDtoConverter).toDto(xRoadMember);

            var result = membersApiController.updateMemberName(encodedClientId, new MemberNameDto().memberName(newName));

            assertSame(clientDto, result.getBody());
            inOrder(auditData, memberService).verify(inOrder -> {
                inOrder.verify(auditData).put(RestApiAuditProperty.CLIENT_IDENTIFIER, clientId);
                inOrder.verify(memberService).updateMemberName(clientId, newName);
                inOrder.verify(clientDtoConverter).toDto(xRoadMember);
            });
        }

        @Test
        @DisplayName("Should throw NotFoundException if client with given id is not in database")
        void shouldThrowNotFoundException() {
            doReturn(Option.none()).when(memberService).updateMemberName(any(), any());
            Executable testable = () ->
                    membersApiController.updateMemberName(notExistingEncodedClientId, new MemberNameDto().memberName("new name"));

            assertThrows(
                    NotFoundException.class,
                    testable);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "INVALID-FORMAT",
                "TEST:CLASS:CODE:SUBSYSTEM"})
        @DisplayName("Should throw BadRequest if called with invalid id")
        void shouldThrowBadRequestException(String id) {
            Executable testable = () -> membersApiController.updateMemberName(id, new MemberNameDto().memberName("new name"));

            var thrown = assertThrows(
                    BadRequestException.class,
                    testable,
                    "Expecting BadRequestException when called with invalid member id");

            assertEquals("Invalid member id", thrown.getMessage());
        }
    }

    @Nested
    class GetMemberGlobalGroups implements WithInOrder {

        private final ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "CODE");
        private final String encodedClientId = "TEST:CLASS:CODE";

        @Test
        @DisplayName("Should return members global groups")
        void shouldReturnGlobalGroups() {
            var memberGlobalGroupsEntitiesMock = List.of(mock(GlobalGroupMember.class), mock(GlobalGroupMember.class));
            var memberGlobalGroupsDtosMock = Set.of(mock(MemberGlobalGroupDto.class), mock(MemberGlobalGroupDto.class));
            doReturn(memberGlobalGroupsEntitiesMock).when(memberService).getMemberGlobalGroups(clientId);
            doReturn(memberGlobalGroupsDtosMock).when(groupMemberConverter).convertMemberGlobalGroups(memberGlobalGroupsEntitiesMock);

            var memberGlobalGroupsResponse = membersApiController.getMemberGlobalGroups(encodedClientId);

            assertEquals(HttpStatus.OK, memberGlobalGroupsResponse.getStatusCode());
            assertEquals(memberGlobalGroupsDtosMock.size(), memberGlobalGroupsResponse.getBody().size());

            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdConverter).convertId(encodedClientId);
                inOrder.verify(memberService).getMemberGlobalGroups(clientId);
                inOrder.verify(groupMemberConverter).convertMemberGlobalGroups(memberGlobalGroupsEntitiesMock);
            });
        }
    }

    @Nested
    class GetMemberOwnedServers implements WithInOrder {
        private final ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "CODE");
        private final String encodedClientId = "TEST:CLASS:CODE";

        @Test
        @DisplayName("Should return members owned servers")
        void shouldReturnOwnedServers() {
            var memberOwnedServersEntitiesMock = Set.of(mock(SecurityServer.class), mock(SecurityServer.class));
            doReturn(memberOwnedServersEntitiesMock).when(memberService).getMemberOwnedServers(clientId);
            doReturn(mock(SecurityServerDto.class), mock(SecurityServerDto.class))
                    .when(securityServerDtoConverter).toDto(isA(SecurityServer.class));

            var memberOwnedServers = membersApiController.getOwnedServers(encodedClientId);

            assertEquals(HttpStatus.OK, memberOwnedServers.getStatusCode());
            assertEquals(memberOwnedServersEntitiesMock.size(), memberOwnedServers.getBody().size());

            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdConverter).convertId(encodedClientId);
                inOrder.verify(memberService).getMemberOwnedServers(clientId);
                inOrder.verify(securityServerDtoConverter, times(2)).toDto(isA(SecurityServer.class));
            });
        }
    }*/

}
