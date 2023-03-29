/**
 * The MIT License
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

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.securityserver.restapi.converter.comparator.ServiceClientSortingComparator;
import org.niis.xroad.securityserver.restapi.openapi.model.Endpoint;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdate;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdate.MethodEnum;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientType;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClients;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import javax.validation.ConstraintViolationException;

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
        when(globalConfFacade.getMembers(any())).thenReturn(globalMemberInfos);
        when(globalConfFacade.getMemberName(any())).thenAnswer(invocation -> {
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            Optional<MemberInfo> m = globalMemberInfos.stream()
                    .filter(g -> g.getId().equals(clientId))
                    .findFirst();
            if (m.isPresent()) {
                return m.get().getName();
            } else {
                return null;
            }
        });
        when(globalConfFacade.getGlobalGroupDescription(any())).thenReturn("");
    }

    @Test
    @WithMockUser(authorities = { "VIEW_ENDPOINT" })
    public void getEndpoint() {
        Endpoint endpoint = endpointsApiController.getEndpoint("12").getBody();
        assertTrue(endpoint.getId().equals("12"));
        assertTrue(endpoint.getMethod().equals(Endpoint.MethodEnum.PUT));
        assertTrue(endpoint.getPath().equals("/foo"));
    }

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_ENDPOINT" })
    public void deleteEndpointNotExist() {
        endpointsApiController.deleteEndpoint(NO_SUCH_ENDPOINT_ID);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "DELETE_ENDPOINT" })
    public void deleteGeneratedEndpoint() {
        endpointsApiController.deleteEndpoint("10");
    }

    @Test
    @WithMockUser(authorities = { "DELETE_ENDPOINT" })
    public void deleteEndpoint() {
        ClientType client = clientService.getLocalClient(getClientId("FI", "GOV", "M2", "SS6"));
        int aclCount = client.getAcl().size();
        endpointsApiController.deleteEndpoint("11");
        assertTrue(client.getEndpoint().stream().noneMatch(ep -> ep.getId().equals(11L)));
        assertTrue(client.getAcl().size() < aclCount);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "EDIT_OPENAPI3_ENDPOINT" })
    public void updateGeneratedEndpoint() {
        EndpointUpdate pathAndMethod = new EndpointUpdate();
        pathAndMethod.setMethod(EndpointUpdate.MethodEnum.STAR);
        pathAndMethod.setPath("/test");
        endpointsApiController.updateEndpoint("10", pathAndMethod);
    }

    @Test(expected = ConstraintViolationException.class)
    @WithMockUser(authorities = { "EDIT_OPENAPI3_ENDPOINT" })
    public void updateEndpointWithEmptyPathString() {
        EndpointUpdate pathAndMethod = new EndpointUpdate().method(MethodEnum.GET);
        endpointsApiController.updateEndpoint("12", pathAndMethod);
    }

    @Test(expected = ConstraintViolationException.class)
    @WithMockUser(authorities = { "EDIT_OPENAPI3_ENDPOINT" })
    public void updateEndpointWithEmptyMethod() {
        EndpointUpdate pathAndMethod = new EndpointUpdate().path("/foo").method(null);
        endpointsApiController.updateEndpoint("12", pathAndMethod);
    }

    @Test
    @WithMockUser(authorities = { "EDIT_OPENAPI3_ENDPOINT" })
    public void updateEndpoint() {
        EndpointUpdate pathAndMethod = new EndpointUpdate();
        pathAndMethod.setMethod(EndpointUpdate.MethodEnum.STAR);
        pathAndMethod.setPath("/test");
        endpointsApiController.updateEndpoint("12", pathAndMethod);

        ClientType client = clientService.getLocalClient(getClientId("FI", "GOV", "M2", "SS6"));
        EndpointType endpointType = client.getEndpoint().stream().filter(ep -> ep.getId().equals(12L))
                .findFirst().get();

        assertTrue(endpointType.getMethod().equals("*"));
        assertTrue(endpointType.getPath().equals("/test"));
    }

    @Test(expected = NotFoundException.class)
    @WithMockUser(authorities = { "VIEW_ENDPOINT_ACL" })
    public void getInexistingEndpointAccessRights() {
        endpointsApiController.getEndpointServiceClients("NON_EXISTING_ENDPOINT_ID");
    }

    @Test
    @WithMockUser(authorities = { "VIEW_ENDPOINT_ACL" })
    public void getEndpointAccesRights() {
        Set<ServiceClient> serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 3);
        // Test sorting order
        assertEquals(true, TestUtils.isSortOrderCorrect(serviceClients, serviceClientSortingComparator));
    }

    @Test
    @WithMockUser(authorities = { "EDIT_ENDPOINT_ACL", "VIEW_ENDPOINT_ACL" })
    public void removeExistingEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        Set<ServiceClient> serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 3);
        ServiceClients deletedScs = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS6).serviceClientType(
                        ServiceClientType.SUBSYSTEM));
        endpointsApiController.deleteEndpointServiceClients("6", deletedScs);
        persistenceUtils.flush();
        serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 2);
        assertTrue(serviceClients.stream().anyMatch(sc -> "2".equals(sc.getId())));
    }

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = { "EDIT_ENDPOINT_ACL", "VIEW_ENDPOINT_ACL" })
    public void removeInexistingEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        Set<ServiceClient> serviceClients = endpointsApiController.getEndpointServiceClients("6").getBody();
        assertTrue(serviceClients.size() == 3);
        ServiceClients deletedScs = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS1).serviceClientType(
                        ServiceClientType.SUBSYSTEM));
        endpointsApiController.deleteEndpointServiceClients("6", deletedScs);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "EDIT_ENDPOINT_ACL" })
    public void addExistingEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        doReturn(true).when(globalConfService).globalGroupsExist(any());

        ServiceClients serviceClients = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS6).serviceClientType(
                        ServiceClientType.SUBSYSTEM));
        endpointsApiController.addEndpointServiceClients("9", serviceClients);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_ENDPOINT_ACL", "EDIT_ENDPOINT_ACL" })
    public void addEndpointAccessRights() {
        doReturn(true).when(globalConfService).clientsExist(any());
        doReturn(true).when(globalConfService).globalGroupsExist(any());

        // add access rights for a subsystem and global group to endpoint
        Set<ServiceClient> serviceClients = endpointsApiController.getEndpointServiceClients("9").getBody();
        assertTrue(serviceClients.size() == 1);
        ServiceClients added = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.CLIENT_ID_SS5).serviceClientType(
                        ServiceClientType.SUBSYSTEM))
                .addItemsItem(new ServiceClient().id(TestUtils.DB_GLOBALGROUP_ID).serviceClientType(
                        ServiceClientType.GLOBALGROUP));
        endpointsApiController.addEndpointServiceClients("9", added).getBody();
        persistenceUtils.flush();
        serviceClients = endpointsApiController.getEndpointServiceClients("9").getBody();

        assertTrue(serviceClients.size() == 3);
        // Test sorting order
        assertEquals(true, TestUtils.isSortOrderCorrect(serviceClients, serviceClientSortingComparator));
        // add access rights for a local group to endpoint
        Set<ServiceClient> localGroupTestServiceClients = endpointsApiController
                .getEndpointServiceClients("3").getBody();
        assertTrue(localGroupTestServiceClients.size() == 1);
        ServiceClients localGroupScs = new ServiceClients()
                .addItemsItem(new ServiceClient().id(TestUtils.DB_LOCAL_GROUP_ID_1).serviceClientType(
                        ServiceClientType.LOCALGROUP));
        endpointsApiController.addEndpointServiceClients("3", localGroupScs).getBody();
        persistenceUtils.flush();
        localGroupTestServiceClients = endpointsApiController.getEndpointServiceClients("3").getBody();

        assertTrue(localGroupTestServiceClients.size() == 2);
        assertTrue(localGroupTestServiceClients.stream().anyMatch(sc -> sc.getId()
                .equals(TestUtils.DB_LOCAL_GROUP_ID_1)));
    }

}
