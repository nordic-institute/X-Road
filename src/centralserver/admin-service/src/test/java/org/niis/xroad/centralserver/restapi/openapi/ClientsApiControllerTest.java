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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.openapi.model.ClientDto;
import org.niis.xroad.centralserver.openapi.model.ClientIdDto;
import org.niis.xroad.centralserver.openapi.model.ClientTypeDto;
import org.niis.xroad.centralserver.openapi.model.MemberNameDto;
import org.niis.xroad.centralserver.openapi.model.PagedClientsDto;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.centralserver.restapi.converter.PageRequestConverter;
import org.niis.xroad.centralserver.restapi.converter.PagedClientsConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.db.ClientDtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.model.ClientTypeDtoConverter;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerId;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.FlattenedSecurityServerClientRepository;
import org.niis.xroad.centralserver.restapi.service.ClientService;
import org.niis.xroad.centralserver.restapi.service.SecurityServerService;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ClientsApiControllerTest implements WithInOrder {

    @Mock
    private ClientService clientService;
    @Mock
    private AuditDataHelper auditData;
    @Mock
    private SecurityServerService securityServerService;

    @Mock
    private PagedClientsConverter pagedClientsConverter;
    @Mock
    private PageRequestConverter pageRequestConverter;
    @Mock
    private SecurityServerIdConverter securityServerIdConverter;
    @Mock
    private ClientDtoConverter.Flattened flattenedSecurityServerClientViewDtoConverter;
    @Mock
    private ClientDtoConverter clientDtoConverter;
    @Mock
    private ClientTypeDtoConverter.Service clientTypeDtoConverter;
    @Spy
    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    @InjectMocks
    private ClientsApiController clientsApiController;

    private static final String MEMBER_NAME = "MEMBER_NAME";
    private static final String MEMBER_CLASS = "MEMBER_NAME";
    private static final String MEMBER_CODE = "MEMBER_CODE";

    @Nested
    @DisplayName("addClient(ClientDto clientDto)")
    public class AddClientMethod implements WithInOrder {

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
        @DisplayName("should add client successfully")
        public void shouldAddClientSuccessfully() {
            doReturn(MEMBER_NAME).when(newClientDto).getMemberName();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_NAME, MEMBER_NAME);
            doReturn(clientIdDto).when(newClientDto).getXroadId();
            doReturn(MEMBER_CLASS).when(clientIdDto).getMemberClass();
            doNothing().when(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doReturn(newXRoadMember).when(clientDtoConverter).fromDto(newClientDto);
            doReturn(expectTypeFn).when(clientDtoConverter).expectType(XRoadMember.class);
            doReturn(newXRoadMember).when(expectTypeFn).apply(newXRoadMember);
            doReturn(persistedXRoadMember).when(clientService).add(newXRoadMember);
            doReturn(persistedClientDto).when(clientDtoConverter).toDto(persistedXRoadMember);

            ResponseEntity<ClientDto> result = clientsApiController.addClient(newClientDto);

            assertNotNull(result);
            assertEquals(HttpStatus.OK, result.getStatusCode());
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
                inOrder.verify(clientService).add(newXRoadMember);
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

            Executable testable = () -> clientsApiController.addClient(newClientDto);

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
            doThrow(expectedThrows).when(clientService).add(newXRoadMember);

            Executable testable = () -> clientsApiController.addClient(newClientDto);

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
                inOrder.verify(clientService).add(newXRoadMember);
            });
        }
    }

    @Nested
    @DisplayName("findClients(String query, "
            + "PagingSortingParametersDto pagingSorting, "
            + "String name, "
            + "String instance, "
            + "String memberClass, "
            + "String memberCode, "
            + "String subsystemCode, "
            + "ClientTypeDto clientTypeDto, "
            + "String encodedSecurityServerId)")
    public class FindClientsMethod implements WithInOrder {

        private String query = "query";
        @Mock
        private PagingSortingParametersDto pagingSorting;
        private String name = "name";
        private String instance = "instance";
        private String memberClass = "memberClass";
        private String memberCode = "memberCode";
        private String subsystemCode = "subsystemCode";
        private ClientTypeDto clientTypeDto = ClientTypeDto.MEMBER;
        private String encodedSecurityServerId = "encodedSecurityServerId";

        @Mock
        private PageRequest pageRequest;
        @Mock
        private Page<ClientDto> clientDtosPage;
        @Mock
        private Page<FlattenedSecurityServerClientView> flattenedSecurityServerClientViewsPage;
        @Mock
        private FlattenedSecurityServerClientView flattenedSecurityServerClientView;
        @Mock
        private ClientDto clientDto;
        @Mock
        private PagedClientsDto pagedClientsDto;
        private XRoadObjectType xRoadObjectType = XRoadObjectType.MEMBER;
        private SecurityServerId securityServerId = SecurityServerId.create("TEST", "CLASS", "MEMBER",  "SERVER");
        @Mock
        private SecurityServer securityServer;

        @Captor
        private ArgumentCaptor<FlattenedSecurityServerClientRepository.SearchParameters> paramsCaptor;

        @Test
        @DisplayName("should add client successfully with empty encoded security server id")
        public void shouldFindClientsSuccessfullyWithEmptyEncodedSecurityServerId() {
            encodedSecurityServerId = StringUtils.EMPTY;
            doReturn(pageRequest).when(pageRequestConverter).convert(
                    eq(pagingSorting), any(PageRequestConverter.MappableSortParameterConverter.class));
            doReturn(xRoadObjectType).when(clientTypeDtoConverter).fromDto(clientTypeDto);
            doReturn(flattenedSecurityServerClientViewsPage).when(clientService).find(any(), eq(pageRequest));
            doAnswer(invocation -> {
                Function<FlattenedSecurityServerClientView, ClientDto> fun = invocation.getArgument(0);
                ClientDto actualClientDto = fun.apply(flattenedSecurityServerClientView);
                assertEquals(clientDto, actualClientDto);
                return clientDtosPage;
            }).when(flattenedSecurityServerClientViewsPage).map(any());
            doReturn(clientDto).when(flattenedSecurityServerClientViewDtoConverter).toDto(flattenedSecurityServerClientView);
            doReturn(pagedClientsDto).when(pagedClientsConverter).convert(clientDtosPage, pagingSorting);

            ResponseEntity<PagedClientsDto> response = clientsApiController.findClients(query,
                    pagingSorting,
                    name,
                    instance,
                    memberClass,
                    memberCode,
                    subsystemCode,
                    clientTypeDto,
                    encodedSecurityServerId);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(pagedClientsDto, response.getBody());
            inOrder().verify(inOrder -> {
                inOrder.verify(pageRequestConverter).convert(
                        eq(pagingSorting), any(PageRequestConverter.MappableSortParameterConverter.class));
                inOrder.verify(clientTypeDtoConverter).fromDto(clientTypeDto);
                inOrder.verify(clientService).find(paramsCaptor.capture(), eq(pageRequest));
                inOrder.verify(flattenedSecurityServerClientViewsPage).map(any());
                inOrder.verify(flattenedSecurityServerClientViewDtoConverter).toDto(flattenedSecurityServerClientView);
                inOrder.verify(pagedClientsConverter).convert(clientDtosPage, pagingSorting);
            });
            assertSearchParams(paramsCaptor.getValue(), null);
        }

        @Test
        @DisplayName("should add client successfully with encoded security server id")
        public void shouldFindClientsSuccessfullyWithEncodedSecurityServerId() {
            int securityServedDbId = 1;
            doReturn(pageRequest).when(pageRequestConverter).convert(
                    eq(pagingSorting), any(PageRequestConverter.MappableSortParameterConverter.class));
            doReturn(xRoadObjectType).when(clientTypeDtoConverter).fromDto(clientTypeDto);
            doReturn(securityServerId).when(securityServerIdConverter).convert(encodedSecurityServerId);
            doReturn(Option.of(securityServer)).when(securityServerService).find(securityServerId);
            doReturn(securityServedDbId).when(securityServer).getId();
            doReturn(flattenedSecurityServerClientViewsPage).when(clientService).find(any(), eq(pageRequest));
            doAnswer(invocation -> {
                Function<FlattenedSecurityServerClientView, ClientDto> fun = invocation.getArgument(0);
                ClientDto actualClientDto = fun.apply(flattenedSecurityServerClientView);
                assertEquals(clientDto, actualClientDto);
                return clientDtosPage;
            }).when(flattenedSecurityServerClientViewsPage).map(any());
            doReturn(clientDto).when(flattenedSecurityServerClientViewDtoConverter).toDto(flattenedSecurityServerClientView);
            doReturn(pagedClientsDto).when(pagedClientsConverter).convert(clientDtosPage, pagingSorting);

            ResponseEntity<PagedClientsDto> response = clientsApiController.findClients(query,
                    pagingSorting,
                    name,
                    instance,
                    memberClass,
                    memberCode,
                    subsystemCode,
                    clientTypeDto,
                    encodedSecurityServerId);

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(pagedClientsDto, response.getBody());
            inOrder().verify(inOrder -> {
                inOrder.verify(pageRequestConverter).convert(
                        eq(pagingSorting), any(PageRequestConverter.MappableSortParameterConverter.class));
                inOrder.verify(clientTypeDtoConverter).fromDto(clientTypeDto);
                inOrder.verify(securityServerIdConverter).convert(encodedSecurityServerId);
                inOrder.verify(securityServerService).find(securityServerId);
                inOrder.verify(securityServer).getId();
                inOrder.verify(clientService).find(paramsCaptor.capture(), eq(pageRequest));
                inOrder.verify(flattenedSecurityServerClientViewsPage).map(any());
                inOrder.verify(flattenedSecurityServerClientViewDtoConverter).toDto(flattenedSecurityServerClientView);
                inOrder.verify(pagedClientsConverter).convert(clientDtosPage, pagingSorting);
            });
            assertSearchParams(paramsCaptor.getValue(), securityServedDbId);
        }

        @Test
        @DisplayName("should fail finding clients with encoded security server id if security server not present in db")
        public void shouldFailFindingClientsWitnEncodedSecurityServerIdIfSecurityServerNotPresenInDb() {
            int securityServedDbId = 1;
            doReturn(pageRequest).when(pageRequestConverter).convert(
                    eq(pagingSorting), any(PageRequestConverter.MappableSortParameterConverter.class));
            doReturn(xRoadObjectType).when(clientTypeDtoConverter).fromDto(clientTypeDto);
            doReturn(securityServerId).when(securityServerIdConverter).convert(encodedSecurityServerId);
            doReturn(Option.none()).when(securityServerService).find(securityServerId);

            Executable testable = () -> clientsApiController.findClients(query,
                    pagingSorting,
                    name,
                    instance,
                    memberClass,
                    memberCode,
                    subsystemCode,
                    clientTypeDto,
                    encodedSecurityServerId);

            BadRequestException actualThrown = assertThrows(BadRequestException.class, testable);
            assertEquals("Security server does not exist", actualThrown.getMessage());
            inOrder().verify(inOrder -> {
                inOrder.verify(pageRequestConverter).convert(
                        eq(pagingSorting), any(PageRequestConverter.MappableSortParameterConverter.class));
                inOrder.verify(clientTypeDtoConverter).fromDto(clientTypeDto);
                inOrder.verify(securityServerIdConverter).convert(encodedSecurityServerId);
                inOrder.verify(securityServerService).find(securityServerId);
            });
        }

        private void assertSearchParams(FlattenedSecurityServerClientRepository.SearchParameters actualParams,
                                        Integer expectedSecurityServerId) {
            assertNotNull(actualParams);
            assertEquals(query, actualParams.getMultifieldSearch());
            assertEquals(name, actualParams.getMemberNameSearch());
            assertEquals(instance, actualParams.getInstanceSearch());
            assertEquals(memberClass, actualParams.getMemberClassSearch());
            assertEquals(memberCode, actualParams.getMemberCodeSearch());
            assertEquals(subsystemCode, actualParams.getSubsystemCodeSearch());
            assertEquals(xRoadObjectType, actualParams.getClientType());
            assertEquals(expectedSecurityServerId, actualParams.getSecurityServerId());
        }
    }

    @Nested
    public class GetClient implements WithInOrder {
        @Mock
        private XRoadMember xRoadMember;
        @Mock
        private ClientDto clientDto;

        private final ClientId clientId = ClientId.Conf.create("TEST", "CLASS", "CODE");
        private final String encodedClientId = "TEST:CLASS:CODE";
        private final String notExistingEncodedClientId = "TEST:MEMBER:DOES-NOT-EXIST";

        @Test
        @DisplayName("Should return client with given id")
        void shouldReturnClient() {
            doReturn(Option.of(xRoadMember)).when(clientService).findMember(clientId);
            doReturn(clientDto).when(clientDtoConverter).toDto(xRoadMember);

            var result = clientsApiController.getClient(encodedClientId);

            assertSame(clientDto, result.getBody());
            inOrder(auditData, clientService).verify(inOrder -> {
                inOrder.verify(auditData).put(RestApiAuditProperty.CLIENT_IDENTIFIER, clientId);
                inOrder.verify(clientService).findMember(clientId);
                inOrder.verify(clientDtoConverter).toDto(xRoadMember);
            });
        }

        @Test
        @DisplayName("Should throw NotFoundException if client with given id is not in database")
        void shouldThrowNotFoundException() {
            doReturn(Option.none()).when(clientService).findMember(any());
            Executable testable = () -> clientsApiController.getClient(notExistingEncodedClientId);

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
            Executable testable = () -> clientsApiController.getClient(id);

            var thrown = assertThrows(
                    BadRequestException.class,
                    testable,
                    "Expecting BadRequestException when called with invalid member id");

            assertEquals("Invalid member id", thrown.getMessage());
        }
    }

    @Nested
    public class DeleteClient implements WithInOrder {
        @Mock
        private XRoadMember xRoadMember;

        private final ClientId clientId = ClientId.Conf.create("INSTANCE", "CLASS", "CODE");
        private final String encodedClientId = "INSTANCE:CLASS:CODE";
        private final String notExistingEncodedClientId = "INSTANCE:MEMBER:NON-EXISTENT";

        @Test
        @DisplayName("Should delete client with given id")
        void shouldReturnClient() {
            var result = clientsApiController.deleteClient(encodedClientId);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            inOrder(auditData, clientService).verify(inOrder -> {
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, clientId.getMemberClass());
                inOrder.verify(auditData).put(RestApiAuditProperty.MEMBER_CODE, clientId.getMemberCode());
                inOrder.verify(clientService).delete(clientId);
            });
        }
    }

    @Nested
    public class UpdateMemberName implements WithInOrder {
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
        void shouldReturnClient() {
            doReturn(Option.of(xRoadMember)).when(clientService).updateMemberName(clientId, newName);
            doReturn(clientDto).when(clientDtoConverter).toDto(xRoadMember);

            var result = clientsApiController.updateMemberName(encodedClientId, new MemberNameDto().memberName(newName));

            assertSame(clientDto, result.getBody());
            inOrder(auditData, clientService).verify(inOrder -> {
                inOrder.verify(auditData).put(RestApiAuditProperty.CLIENT_IDENTIFIER, clientId);
                inOrder.verify(clientService).updateMemberName(clientId, newName);
                inOrder.verify(clientDtoConverter).toDto(xRoadMember);
            });
        }

        @Test
        @DisplayName("Should throw NotFoundException if client with given id is not in database")
        void shouldThrowNotFoundException() {
            doReturn(Option.none()).when(clientService).updateMemberName(any(), any());
            Executable testable = () ->
                    clientsApiController.updateMemberName(notExistingEncodedClientId, new MemberNameDto().memberName("new name"));

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
            Executable testable = () -> clientsApiController.updateMemberName(id, new MemberNameDto().memberName("new name"));

            var thrown = assertThrows(
                    BadRequestException.class,
                    testable,
                    "Expecting BadRequestException when called with invalid member id");

            assertEquals("Invalid member id", thrown.getMessage());
        }
    }

}
