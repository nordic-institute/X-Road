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
package org.niis.xroad.cs.admin.application.openapi;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("java:S2187")
@Disabled("Has to be revorked for new architecture.")
@ExtendWith(MockitoExtension.class)
public class SubsystemsApiControllerTest implements WithInOrder {
 /*
    @Mock
    private SubsystemService subsystemService;

    @Mock
    private AuditDataHelper auditData;
    @Mock
    private ClientDtoConverter clientDtoConverter;
    @Spy
    private ClientIdConverter clientIdConverter = new ClientIdConverter();
    @Spy
    private SecurityServerIdConverter securityServerIdConverter = new SecurityServerIdConverter();

    @InjectMocks
    private SubsystemsApiController subsystemsApiController;

    private static final String SUBSYSTEM_CODE = "SUBSYSTEM_CODE";
    private static final String MEMBER_CLASS = "MEMBER_NAME";
    private static final String MEMBER_CODE = "MEMBER_CODE";

    @Nested
    @DisplayName("addMember(ClientDto clientDto)")
    public class AddClientMethod implements WithInOrder {

      @Mock
        private ClientDto newClientDto;
        @Mock
        private ClientIdDto clientIdDto;
        @Mock
        private ClientDto persistedClientDto;
        @Mock
        private Subsystem newSubsystem;
        @Mock
        private Subsystem persistedSubsystem;

        @Mock
        Function<SecurityServerClient, ? extends SecurityServerClient> expectTypeFn;

        @Test
        @DisplayName("should add subsystem successfully")
        public void shouldAddSubsystemSuccessfully() {
            doReturn(clientIdDto).when(newClientDto).getXroadId();
            doReturn(MEMBER_CLASS).when(clientIdDto).getMemberClass();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
            doReturn(SUBSYSTEM_CODE).when(clientIdDto).getSubsystemCode();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, SUBSYSTEM_CODE);
            doReturn(newSubsystem).when(clientDtoConverter).fromDto(newClientDto);
            doReturn(expectTypeFn).when(clientDtoConverter).expectType(Subsystem.class);
            doReturn(newSubsystem).when(expectTypeFn).apply(newSubsystem);
            doReturn(persistedSubsystem).when(subsystemService).add(newSubsystem);
            doReturn(persistedClientDto).when(clientDtoConverter).toDto(persistedSubsystem);

            ResponseEntity<ClientDto> result = subsystemsApiController.addSubsystem(newClientDto);

            assertNotNull(result);
            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertEquals(persistedClientDto, result.getBody());
            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
                inOrder.verify(clientIdDto).getMemberCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
                inOrder.verify(clientIdDto).getSubsystemCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, SUBSYSTEM_CODE);
                inOrder.verify(clientDtoConverter).fromDto(newClientDto);
                inOrder.verify(clientDtoConverter).expectType(Subsystem.class);
                inOrder.verify(expectTypeFn).apply(newSubsystem);
                inOrder.verify(subsystemService).add(newSubsystem);
                inOrder.verify(clientDtoConverter).toDto(persistedSubsystem);
            });
        }

        @Test
        @DisplayName("should fail while trying to add illegal type of security server client")
        public void shouldFailWhileTryingToAddIllegalTypeSecurityServerClient() {
            IllegalArgumentException expectedThrows = mock(IllegalArgumentException.class);
            doReturn(clientIdDto).when(newClientDto).getXroadId();
            doReturn(MEMBER_CLASS).when(clientIdDto).getMemberClass();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
            doReturn(SUBSYSTEM_CODE).when(clientIdDto).getSubsystemCode();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, SUBSYSTEM_CODE);
            doReturn(newSubsystem).when(clientDtoConverter).fromDto(newClientDto);
            doReturn(expectTypeFn).when(clientDtoConverter).expectType(Subsystem.class);
            doThrow(expectedThrows).when(expectTypeFn).apply(newSubsystem);

            Executable testable = () -> subsystemsApiController.addSubsystem(newClientDto);

            IllegalArgumentException actualThrows = assertThrows(IllegalArgumentException.class, testable);
            assertEquals(expectedThrows, actualThrows);
            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
                inOrder.verify(clientIdDto).getMemberCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
                inOrder.verify(clientIdDto).getSubsystemCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, SUBSYSTEM_CODE);
                inOrder.verify(clientDtoConverter).fromDto(newClientDto);
                inOrder.verify(clientDtoConverter).expectType(Subsystem.class);
                inOrder.verify(expectTypeFn).apply(newSubsystem);
            });
        }

        @Test
        @DisplayName("should fail while trying to add to repository")
        public void shouldFailWhileTryingToAddToRepository() {
            EntityExistsException expectedThrows = mock(EntityExistsException.class);
            doReturn(clientIdDto).when(newClientDto).getXroadId();
            doReturn(MEMBER_CLASS).when(clientIdDto).getMemberClass();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
            doReturn(SUBSYSTEM_CODE).when(clientIdDto).getSubsystemCode();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, SUBSYSTEM_CODE);
            doReturn(newSubsystem).when(clientDtoConverter).fromDto(newClientDto);
            doReturn(expectTypeFn).when(clientDtoConverter).expectType(Subsystem.class);
            doReturn(newSubsystem).when(expectTypeFn).apply(newSubsystem);
            doThrow(expectedThrows).when(subsystemService).add(newSubsystem);

            Executable testable = () -> subsystemsApiController.addSubsystem(newClientDto);

            EntityExistsException actualThrows = assertThrows(EntityExistsException.class, testable);
            assertEquals(expectedThrows, actualThrows);
            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
                inOrder.verify(clientIdDto).getMemberCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, MEMBER_CODE);
                inOrder.verify(clientIdDto).getSubsystemCode();
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, SUBSYSTEM_CODE);
                inOrder.verify(clientDtoConverter).fromDto(newClientDto);
                inOrder.verify(clientDtoConverter).expectType(Subsystem.class);
                inOrder.verify(expectTypeFn).apply(newSubsystem);
                inOrder.verify(subsystemService).add(newSubsystem);
            });
        }
    }

    @Nested
    @DisplayName("unregisterSubsystem(ClientId subsystemId, SecurityServerId securityServerId)")
    class UnregisterSubsystem implements WithInOrder {

        private final ClientId subsystemId = SubsystemId.create("TEST", "CLASS", "MEMBER", "SUBSYSTEM");
        private final String encodedSubsystemId = subsystemId.toShortString(':');

        private final SecurityServerId securityServerId = SecurityServerId.create("TEST", "CLASS", "MEMBER", "SERVER");
        private final String encodedSecurityServerId = securityServerId.toShortString(':');

        @Test
        @DisplayName("Should unregister subsystem")
        void shouldUnregisterSubsystem() {
            var result = subsystemsApiController.unregisterSubsystem(encodedSubsystemId, encodedSecurityServerId);

            assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdConverter).isEncodedSubsystemId(encodedSubsystemId);
                inOrder.verify(clientIdConverter).convertId(encodedSubsystemId);
                inOrder.verify(securityServerIdConverter).convertId(encodedSecurityServerId);
                inOrder.verify(auditData).put(RestApiAuditProperty.SERVER_CODE, securityServerId.getServerCode());
                inOrder.verify(auditData).put(RestApiAuditProperty.OWNER_CLASS, securityServerId.getOwner().getMemberClass());
                inOrder.verify(auditData).put(RestApiAuditProperty.OWNER_CODE, securityServerId.getOwner().getMemberCode());
                inOrder.verify(auditData).put(RestApiAuditProperty.CLIENT_IDENTIFIER, subsystemId);
                inOrder.verify(subsystemService).unregisterSubsystem(subsystemId, securityServerId);
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "INVALID-FORMAT",
                "TEST:CLASS",
                "TEST:CLASS:CODE"
        })
        @DisplayName("Should throw BadRequest if called with invalid subsystem id")
        void shouldThrowBadRequestExceptionWhenInvalidSubsystemId(String invalidSubsystemId) {
            Executable testable =
                    () -> subsystemsApiController.unregisterSubsystem(invalidSubsystemId, encodedSecurityServerId);

            var thrown = assertThrows(BadRequestException.class, testable,
                    "Expecting BadRequestException when called with invalid subsystem id");

            assertEquals("Invalid subsystem id", thrown.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "INVALID-FORMAT",
                "TEST':CLASS",
                "TEST:CLASS:CODE"
        })
        @DisplayName("Should throw BadRequest if called with invalid security server id")
        void shouldThrowBadRequestExceptionWhenInvalidSecurityServerId(String invalidSecurityServerId) {
            Executable testable =
                    () -> subsystemsApiController.unregisterSubsystem(encodedSubsystemId, invalidSecurityServerId);

            var thrown = assertThrows(BadRequestException.class, testable,
                    "Expecting BadRequestException when called with invalid subsystem id");

            assertEquals("Invalid security server id " + invalidSecurityServerId, thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("deleteSubsystem(String id)")
    class DeleteSubsystem implements WithInOrder {

        private final ClientId subsystemClientId = SubsystemId.create("TEST", "CLASS", "MEMBER", "SUBSYSTEM");
        private final String encodedSubsystemId = subsystemClientId.toShortString(':');

        @Test
        @DisplayName("Should delete subsystem")
        void shouldDeleteSubsystem() {
            var result = subsystemsApiController.deleteSubsystem(encodedSubsystemId);

            assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdConverter).convertId(encodedSubsystemId);
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, subsystemClientId.getMemberClass());
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, subsystemClientId.getMemberCode());
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, subsystemClientId.getSubsystemCode());
                inOrder.verify(subsystemService).deleteSubsystem(subsystemClientId);
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "INVALID-FORMAT",
                "TEST:CLASS",
                "TEST:CLASS:CODE"
        })
        @DisplayName("Should throw BadRequest if called with invalid id")
        void shouldThrowBadRequestException(String id) {
            Executable testable = () -> subsystemsApiController.deleteSubsystem(id);

            var thrown = assertThrows(BadRequestException.class, testable,
                    "Expecting BadRequestException when called with invalid subsystem id");

            assertEquals("Invalid subsystem id", thrown.getMessage());
        }
    }*/
}
