/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.restapi.openapi.model.Subject;
import org.niis.xroad.restapi.openapi.model.SubjectType;
import org.niis.xroad.restapi.openapi.model.Subjects;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.service.AccessRightService.AccessRightNotFoundException.ERROR_ACCESSRIGHT_NOT_FOUND;
import static org.niis.xroad.restapi.service.ClientNotFoundException.ERROR_CLIENT_NOT_FOUND;
import static org.niis.xroad.restapi.service.ServiceService.ServiceNotFoundException.ERROR_SERVICE_NOT_FOUND;

/**
 * Test ServicesApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
public class ServicesApiControllerIntegrationTest {

    public static final String LOCALGROUP = "LOCALGROUP";
    public static final String GLOBALGROUP = "GLOBALGROUP";
    public static final String SUBSYSTEM = "SUBSYSTEM";
    public static final String NAME_FOR = "Name for: ";
    public static final String GLOBAL_GROUP_CODE = "security-server-owners";
    public static final String GLOBAL_GROUP_ID = "FI:security-server-owners";
    public static final String LOCAL_GROUP_ID_1 = "1";
    public static final String LOCAL_GROUP_ID_2 = "2";
    public static final String LOCAL_GROUP_CODE = "group1";
    public static final String LOCAL_GROUP_DESC = "foo";
    public static final String SS2_CLIENT_ID = "FI:GOV:M1:SS2";
    public static final String SS3_CLIENT_ID = "FI:GOV:M1:SS3";
    public static final String SS4_CLIENT_ID = "FI:GOV:M1:SS4";
    public static final String SS0_GET_RANDOM = "FI:GOV:M1:SS0:getRandom.v1";
    public static final String SS1_GET_RANDOM = "FI:GOV:M1:SS1:getRandom.v1";
    public static final String SS1_GET_RANDOM_V2 = "FI:GOV:M1:SS1:getRandom.v2";
    public static final String SS1_CALCULATE_PRIME = "FI:GOV:M1:SS1:calculatePrime.v1";
    public static final String SS1_PREDICT_WINNING_LOTTERY_NUMBERS = "FI:GOV:M1:SS1:predictWinningLotteryNumbers.v1";
    public static final String NEW_SERVICE_URL_HTTPS = "https://foo.bar";
    public static final String NEW_SERVICE_URL_HTTP = "http://foo.bar";
    private static final String INSTANCE_FI = "FI";
    private static final String INSTANCE_EE = "EE";
    private static final String MEMBER_CLASS_GOV = "GOV";
    private static final String MEMBER_CLASS_PRO = "PRO";
    private static final String MEMBER_CODE_M1 = "M1";
    private static final String MEMBER_CODE_M2 = "M2";
    private static final String SUBSYSTEM1 = "SS1";
    private static final String SUBSYSTEM2 = "SS2";
    private static final String SUBSYSTEM3 = "SS3";

    @Autowired
    private ServicesApiController servicesApiController;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private GlobalConfService globalConfService;

    @Before
    public void setup() {
        when(globalConfFacade.getGlobalGroupDescription(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            GlobalGroupId id = (GlobalGroupId) args[0];
            return NAME_FOR + id.getGroupCode();
        });

        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return NAME_FOR + identifier.toShortString().replace("/", ":");
        });
        when(globalConfFacade.getMembers(any())).thenReturn(new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1),
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM2),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M2, SUBSYSTEM3),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M1, SUBSYSTEM1),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M2, null))
        ));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS", "VIEW_CLIENT_DETAILS" })
    public void updateServiceHttps() {
        Service service = servicesApiController.getService(SS1_GET_RANDOM).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(false);
        service.setUrl(NEW_SERVICE_URL_HTTPS);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service);

        Service updatedService = servicesApiController.updateService(SS1_GET_RANDOM, serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(false, updatedService.getSslAuth());
        assertEquals(NEW_SERVICE_URL_HTTPS, updatedService.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS", "VIEW_CLIENT_DETAILS" })
    public void updateServiceHttp() {
        Service service = servicesApiController.getService(SS1_GET_RANDOM).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(true); // value does not matter if http - will aways be set to null
        service.setUrl(NEW_SERVICE_URL_HTTP);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service);

        Service updatedService = servicesApiController.updateService(SS1_GET_RANDOM, serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertNull(updatedService.getSslAuth());
        assertEquals(NEW_SERVICE_URL_HTTP, updatedService.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS", "VIEW_CLIENT_DETAILS" })
    public void updateServiceAll() {
        Service service = servicesApiController.getService(SS1_GET_RANDOM).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(false);
        service.setUrl(NEW_SERVICE_URL_HTTPS);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service).urlAll(true)
                .sslAuthAll(true).timeoutAll(true);

        Service updatedService = servicesApiController.updateService(SS1_GET_RANDOM, serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(false, updatedService.getSslAuth());
        assertEquals(NEW_SERVICE_URL_HTTPS, updatedService.getUrl());

        Service otherServiceFromSameServiceDesc = servicesApiController.getService(SS1_CALCULATE_PRIME).getBody();

        assertEquals(10, otherServiceFromSameServiceDesc.getTimeout().intValue());
        assertEquals(false, otherServiceFromSameServiceDesc.getSslAuth());
        assertEquals(NEW_SERVICE_URL_HTTPS, otherServiceFromSameServiceDesc.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS", "VIEW_CLIENT_DETAILS" })
    public void updateServiceOnlyUrlAll() {
        Service service = servicesApiController.getService(SS1_GET_RANDOM).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSslAuth(true);
        service.setUrl(NEW_SERVICE_URL_HTTPS);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service).urlAll(true);

        Service updatedService = servicesApiController.updateService(SS1_GET_RANDOM, serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(true, updatedService.getSslAuth());
        assertEquals(NEW_SERVICE_URL_HTTPS, updatedService.getUrl());

        Service otherServiceFromSameServiceDesc = servicesApiController.getService(SS1_CALCULATE_PRIME).getBody();

        assertEquals(60, otherServiceFromSameServiceDesc.getTimeout().intValue());
        assertEquals(false, otherServiceFromSameServiceDesc.getSslAuth());
        assertEquals(NEW_SERVICE_URL_HTTPS, otherServiceFromSameServiceDesc.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void getServiceClientNotFound() {
        try {
            servicesApiController.getService(SS0_GET_RANDOM).getBody();
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_CLIENT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void getServiceNotFound() {
        try {
            servicesApiController.getService(SS1_PREDICT_WINNING_LOTTERY_NUMBERS).getBody();
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_SERVICE_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void getServiceAccessRights() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        ServiceClient serviceClient = getServiceClientByType(serviceClients, GLOBALGROUP).get();
        assertEquals(NAME_FOR + GLOBAL_GROUP_CODE, serviceClient.getSubject().getMemberNameGroupDescription());
        assertEquals(GLOBAL_GROUP_ID, serviceClient.getSubject().getId());
        assertEquals(GLOBALGROUP, serviceClient.getSubject().getSubjectType().name());
        assertNull(serviceClient.getAccessRights());

        serviceClient = getServiceClientByType(serviceClients, LOCALGROUP).get();
        assertEquals(LOCAL_GROUP_ID_1, serviceClient.getSubject().getId());
        assertEquals(LOCAL_GROUP_CODE, serviceClient.getSubject().getLocalGroupCode());
        assertEquals(LOCAL_GROUP_DESC, serviceClient.getSubject().getMemberNameGroupDescription());
        assertEquals(LOCALGROUP, serviceClient.getSubject().getSubjectType().name());
        assertNull(serviceClient.getAccessRights());

        serviceClient = getServiceClientByType(serviceClients, SUBSYSTEM).get();
        assertEquals(NAME_FOR + SS2_CLIENT_ID, serviceClient.getSubject().getMemberNameGroupDescription());
        assertEquals(SS2_CLIENT_ID, serviceClient.getSubject().getId());
        assertEquals(SUBSYSTEM, serviceClient.getSubject().getSubjectType().name());
        assertNull(serviceClient.getAccessRights());

        serviceClients = servicesApiController.getServiceAccessRights(SS1_CALCULATE_PRIME).getBody();
        assertTrue(serviceClients.isEmpty());

        // different versions of a service should have the same access rights
        serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM_V2).getBody();
        assertEquals(3, serviceClients.size());

        try {
            servicesApiController.getServiceAccessRights(SS0_GET_RANDOM);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_CLIENT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }

        try {
            servicesApiController.getServiceAccessRights(SS1_PREDICT_WINNING_LOTTERY_NUMBERS);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            assertEquals(ERROR_SERVICE_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    private Optional<ServiceClient> getServiceClientByType(List<ServiceClient> serviceClients, String type) {
        return serviceClients
                .stream()
                .filter(serviceClient -> serviceClient.getSubject().getSubjectType().name().equals(type))
                .findFirst();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteServiceAccessRights() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(GLOBAL_GROUP_ID).subjectType(SubjectType.GLOBALGROUP));

        servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
        serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(2, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteMultipleServiceAccessRights() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(GLOBAL_GROUP_ID).subjectType(SubjectType.GLOBALGROUP))
                .addItemsItem(new Subject().id(SS2_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM));

        servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
        serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteMultipleSameServiceAccessRights() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(GLOBAL_GROUP_ID).subjectType(SubjectType.GLOBALGROUP))
                .addItemsItem(new Subject().id(GLOBAL_GROUP_ID).subjectType(SubjectType.GLOBALGROUP));

        servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
        serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(2, serviceClients.size());
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteServiceAccessRightsWrongType() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(SS2_CLIENT_ID).subjectType(SubjectType.GLOBALGROUP));
        servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteServiceAccessRightsWrongTypeLocalGroup() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(SS2_CLIENT_ID).subjectType(SubjectType.LOCALGROUP));
        servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteServiceAccessRightsWithRedundantSubjects() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(SS2_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM))
                .addItemsItem(new Subject().id(SS3_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM))
                .addItemsItem(new Subject().id(SS4_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM));
        try {
            servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
        } catch (BadRequestException expected) {
            assertEquals(ERROR_ACCESSRIGHT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteServiceAccessRightsLocalGroupsWithRedundantSubjects() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(LOCAL_GROUP_ID_1).subjectType(SubjectType.LOCALGROUP))
                .addItemsItem(new Subject().id(SS3_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM))
                .addItemsItem(new Subject().id(LOCAL_GROUP_ID_2).subjectType(SubjectType.LOCALGROUP));
        try {
            servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
        } catch (BadRequestException expected) {
            assertEquals(ERROR_ACCESSRIGHT_NOT_FOUND, expected.getErrorDeviation().getCode());
        }
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteServiceAccessRightsWrongLocalGroupId() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(LOCAL_GROUP_CODE).subjectType(SubjectType.LOCALGROUP))
                .addItemsItem(new Subject().id(SS3_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM))
                .addItemsItem(new Subject().id(LOCAL_GROUP_ID_2).subjectType(SubjectType.LOCALGROUP));
        servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void deleteServiceAccessRightsWrongLocalGroupType() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjects = new Subjects()
                .addItemsItem(new Subject().id(LOCAL_GROUP_ID_2).subjectType(SubjectType.GLOBALGROUP))
                .addItemsItem(new Subject().id(SS3_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM));
        servicesApiController.deleteServiceAccessRight(SS1_GET_RANDOM, subjects).getBody();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void addAccessRights() {
        when(globalConfService.identifiersExist(any())).thenReturn(true);
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(
                SS1_CALCULATE_PRIME).getBody();
        assertEquals(0, serviceClients.size());

        Subjects subjectsToAdd = new Subjects()
                .addItemsItem(new Subject().id(LOCAL_GROUP_ID_2).subjectType(SubjectType.LOCALGROUP))
                .addItemsItem(new Subject().id(GLOBAL_GROUP_ID).subjectType(SubjectType.GLOBALGROUP))
                .addItemsItem(new Subject().id(SS2_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM));

        List<ServiceClient> updatedServiceClients = servicesApiController
                .addServiceAccessRight(SS1_CALCULATE_PRIME, subjectsToAdd).getBody();

        assertEquals(3, updatedServiceClients.size());
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void addDuplicateAccessRight() {
        when(globalConfService.identifiersExist(any())).thenReturn(true);
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjectsToAdd = new Subjects()
                .addItemsItem(new Subject().id(LOCAL_GROUP_ID_2).subjectType(SubjectType.LOCALGROUP))
                .addItemsItem(new Subject().id(SS2_CLIENT_ID).subjectType(SubjectType.SUBSYSTEM));

        servicesApiController.addServiceAccessRight(SS1_GET_RANDOM, subjectsToAdd);
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "EDIT_SERVICE_ACL", "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_SERVICES" })
    public void addBogusAccessRight() {
        when(globalConfService.identifiersExist(any())).thenReturn(false);
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        Subjects subjectsToAdd = new Subjects()
                .addItemsItem(new Subject().id(LOCAL_GROUP_ID_2).subjectType(SubjectType.LOCALGROUP))
                .addItemsItem(new Subject().id(SS2_CLIENT_ID + "foo").subjectType(SubjectType.SUBSYSTEM));

        servicesApiController.addServiceAccessRight(SS1_GET_RANDOM, subjectsToAdd);
    }
}
