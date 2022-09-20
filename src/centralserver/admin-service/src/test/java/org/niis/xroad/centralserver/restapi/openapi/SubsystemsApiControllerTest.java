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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.openapi.model.ClientDto;
import org.niis.xroad.centralserver.openapi.model.ClientIdDto;
import org.niis.xroad.centralserver.restapi.dto.converter.db.ClientDtoConverter;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.service.SubsystemService;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class SubsystemsApiControllerTest implements WithInOrder {

    @Mock
    private SubsystemService subsystemService;

    @Mock
    private AuditDataHelper auditData;
    @Mock
    private ClientDtoConverter clientDtoConverter;

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
        @DisplayName("should add member successfully")
        public void shouldAddMemberSuccessfully() {
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


}
