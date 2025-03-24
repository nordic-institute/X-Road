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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.validation.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.securityserver.restapi.converter.comparator.ServiceClientSortingComparator;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointDto;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdateDto.MethodEnum;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientsDto;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.entity.ClientEntity;
import org.niis.xroad.serverconf.entity.EndpointEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.getClientId;

public class EndpointsApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    EndpointsApiController endpointsApiController;

    @Autowired
    ClientService clientService;

    @Autowired
    PersistenceUtils persistenceUtils;

    @Autowired
    ServiceClientSortingComparator serviceClientSortingComparator;

    private static final String NO_SUCH_ENDPOINT_ID = "1294379018";

    @Before
    public void setup() throws Exception {
        List<MemberInfo> globalMemberInfos = new ArrayList<>(Arrays.asList(
                // exists in serverconf
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                // exists in serverconf
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        TestUtils.SUBSYSTEM5),
                // exists in serverconf
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        TestUtils.SUBSYSTEM6)));
        when(globalConfProvider.getMembers(any())).thenReturn(globalMemberInfos);
        when(globalConfProvider.getMemberName(any())).thenAnswer(invocation -> {
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            Optional<MemberInfo> m = globalMemberInfos.stream()
                    .filter(g -> g.id().equals(clientId))
                    .findFirst();
            return m.map(MemberInfo::name).orElse(null);
        });
        when(globalConfProvider.getGlobalGroupDescription(any())).thenReturn("");
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ENDPOINT"})
    public void getEndpoint() {
        EndpointDto endpoint = endpointsApiController.getEndpoint("12").getBody();
        assertEquals("12", endpoint.getId());
        assertEquals(EndpointDto.MethodEnum.PUT, endpoint.getMethod());
        assertEquals("/foo", endpoint.getPath());
    }

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_ENDPOINT"})
    public void deleteEndpointNotExist() {
        endpointsApiController.deleteEndpoint(NO_SUCH_ENDPOINT_ID);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = {"DELETE_ENDPOINT"})
    public void deleteGeneratedEndpoint() {
        endpointsApiController.deleteEndpoint("10");
    }

    @Test
    @WithMockUser(authorities = {"DELETE_ENDPOINT"})
    public void deleteEndpoint() {
        ClientEntity client = clientService.getLocalClientEntity(getClientId("FI", "GOV", "M2", "SS6"));
        int aclCount = client.getAccessRights().size();
        endpointsApiController.deleteEndpoint("11");
        assertTrue(client.getEndpoints().stream().noneMatch(ep -> ep.getId().equals(11L)));
        assertTrue(client.getAccessRights().size() < aclCount);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = {"EDIT_OPENAPI3_ENDPOINT"})
    public void updateGeneratedEndpoint() {
        EndpointUpdateDto pathAndMethod = new EndpointUpdateDto();
        pathAndMethod.setMethod(EndpointUpdateDto.MethodEnum.STAR);
        pathAndMethod.setPath("/test");
        endpointsApiController.updateEndpoint("10", pathAndMethod);
    }

    @Test(expected = ConstraintViolationException.class)
    @WithMockUser(authorities = {"EDIT_OPENAPI3_ENDPOINT"})
    public void updateEndpointWithEmptyPathString() {
        EndpointUpdateDto pathAndMethod = new EndpointUpdateDto().method(MethodEnum.GET);
        endpointsApiController.updateEndpoint("12", pathAndMethod);
    }

    @Test(expected = ConstraintViolationException.class)
    @WithMockUser(authorities = {"EDIT_OPENAPI3_ENDPOINT"})
    public void updateEndpointWithEmptyMethod() {
        EndpointUpdateDto pathAndMethod = new EndpointUpdateDto().path("/foo").method(null);
        endpointsApiController.updateEndpoint("12", pathAndMethod);
    }

    @Test
    @WithMockUser(authorities = {"EDIT_OPENAPI3_ENDPOINT"})
    public void updateEndpoint() {
        EndpointUpdateDto pathAndMethod = new EndpointUpdateDto();
        pathAndMethod.setMethod(EndpointUpdateDto.MethodEnum.STAR);
        pathAndMethod.setPath("/test");
        endpointsApiController.updateEndpoint("12", pathAndMethod);

        ClientEntity client = clientService.getLocalClientEntity(getClientId("FI", "GOV", "M2", "SS6"));
        EndpointEntity endpointEntity = client.getEndpoints().stream().filter(ep -> ep.getId().equals(12L))
                .findFirst().get();

        assertTrue(endpointEntity.getMethod().equals("*"));
        assertTrue(endpointEntity.getPath().equals("/test"));
    }

    @Test(expected = NotFoundException.class)
    @WithMockUser(authorities = {"VIEW_ENDPOINT_ACL"})
    public void getInexistingEndpointAccessRights() {
        endpointsApiController.getEndpointServiceClients("NON_EXISTING_ENDPOINT_ID");
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ENDPOINT_ACL"})
    public void getEndpointAccesRights() {
        Set<ServiceClientDto> serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 3);
        // Test sorting order
        assertEquals(true, TestUtils.isSortOrderCorrect(serviceClients, serviceClientSortingComparator));
    }

    @Test
    @WithMockUser(authorities = {"EDIT_ENDPOINT_ACL", "VIEW_ENDPOINT_ACL"})
    public void removeExistingEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        Set<ServiceClientDto> serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 3);
        ServiceClientsDto deletedScs = new ServiceClientsDto()
                .addItemsItem(new ServiceClientDto().id(TestUtils.CLIENT_ID_SS6).serviceClientType(
                        ServiceClientTypeDto.SUBSYSTEM));
        endpointsApiController.deleteEndpointServiceClients("6", deletedScs);
        persistenceUtils.flush();
        serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 2);
        assertTrue(serviceClients.stream().anyMatch(sc -> "2".equals(sc.getId())));
    }

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = {"EDIT_ENDPOINT_ACL", "VIEW_ENDPOINT_ACL"})
    public void removeInexistingEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        Set<ServiceClientDto> serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 3);
        ServiceClientsDto deletedScs = new ServiceClientsDto()
                .addItemsItem(new ServiceClientDto().id(TestUtils.CLIENT_ID_SS1).serviceClientType(
                        ServiceClientTypeDto.SUBSYSTEM));
        endpointsApiController.deleteEndpointServiceClients("6", deletedScs);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = {"EDIT_ENDPOINT_ACL"})
    public void addExistingEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        doReturn(true).when(globalConfService).globalGroupsExist(any());

        ServiceClientsDto serviceClients = new ServiceClientsDto()
                .addItemsItem(new ServiceClientDto().id(TestUtils.CLIENT_ID_SS6).serviceClientType(
                        ServiceClientTypeDto.SUBSYSTEM));
        endpointsApiController.addEndpointServiceClients("9", serviceClients);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ENDPOINT_ACL", "EDIT_ENDPOINT_ACL"})
    public void addEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        doReturn(true).when(globalConfService).globalGroupsExist(any());

        // add access rights for a subsystem and global group to endpoint
        Set<ServiceClientDto> serviceClients = endpointsApiController.getEndpointServiceClients("9").getBody();
        assertTrue(serviceClients.size() == 1);
        ServiceClientsDto added = new ServiceClientsDto()
                .addItemsItem(new ServiceClientDto().id(TestUtils.CLIENT_ID_SS5).serviceClientType(
                        ServiceClientTypeDto.SUBSYSTEM))
                .addItemsItem(new ServiceClientDto().id(TestUtils.DB_GLOBALGROUP_ID).serviceClientType(
                        ServiceClientTypeDto.GLOBALGROUP));
        endpointsApiController.addEndpointServiceClients("9", added).getBody();
        persistenceUtils.flush();
        serviceClients = endpointsApiController.getEndpointServiceClients("9").getBody();

        assertTrue(serviceClients.size() == 3);
        // Test sorting order
        assertEquals(true, TestUtils.isSortOrderCorrect(serviceClients, serviceClientSortingComparator));
        // add access rights for a local group to endpoint
        Set<ServiceClientDto> localGroupTestServiceClients = endpointsApiController
                .getEndpointServiceClients("3").getBody();
        assertTrue(localGroupTestServiceClients.size() == 1);
        ServiceClientsDto localGroupScs = new ServiceClientsDto()
                .addItemsItem(new ServiceClientDto().id(TestUtils.DB_LOCAL_GROUP_ID_1).serviceClientType(
                        ServiceClientTypeDto.LOCALGROUP));
        endpointsApiController.addEndpointServiceClients("3", localGroupScs).getBody();
        persistenceUtils.flush();
        localGroupTestServiceClients = endpointsApiController.getEndpointServiceClients("3").getBody();

        assertTrue(localGroupTestServiceClients.size() == 2);
        assertTrue(localGroupTestServiceClients.stream().anyMatch(sc -> sc.getId()
                .equals(TestUtils.DB_LOCAL_GROUP_ID_1)));
    }

}
