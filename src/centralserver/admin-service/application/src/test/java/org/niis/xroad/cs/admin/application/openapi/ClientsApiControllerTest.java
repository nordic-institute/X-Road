/**
 * The MIT License
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

@Disabled("Has to be revorked for new architecture.")
@ExtendWith(MockitoExtension.class)
public class ClientsApiControllerTest implements WithInOrder {

    /*@Mock
    private ClientService clientService;
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
    private ClientTypeDtoConverter.Service clientTypeDtoConverter;

    @InjectMocks
    private ClientsApiController clientsApiController;

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
        @DisplayName("should find client successfully with empty encoded security server id")
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
        @DisplayName("should find client successfully with encoded security server id")
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
    }*/

}
