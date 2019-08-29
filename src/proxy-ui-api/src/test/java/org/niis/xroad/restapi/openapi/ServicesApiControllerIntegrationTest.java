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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.restapi.service.ClientService;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test ServicesApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ServicesApiControllerIntegrationTest {

    public static final String LOCALGROUP = "LOCALGROUP";
    public static final String GLOBALGROUP = "GLOBALGROUP";
    public static final String SUBSYSTEM = "SUBSYSTEM";
    public static final String NAME_FOR = "Name for: ";
    public static final String GLOBAL_GROUP_CODE = "security-server-owners";
    public static final String GLOBAL_GROUP_ID = "GLOBALGROUP:FI:security-server-owners";
    public static final String LOCAL_GROUP_DESC = "foo";
    public static final String LOCAL_GROUP_ID = "LOCALGROUP:group1";
    public static final String SS2_CLIENT_ID = "FI:GOV:M1:SS2";
    public static final String SS0_GET_RANDOM = "FI:GOV:M1:SS0:getRandom.v1";
    public static final String SS1_GET_RANDOM = "FI:GOV:M1:SS1:getRandom.v1";
    public static final String SS1_CALCULATE_PRIME = "FI:GOV:M1:SS1:calculatePrime.v1";
    public static final String SS1_PREDICT_WINNING_LOTTERY_NUMBERS = "FI:GOV:M1:SS1:predictWinningLotteryNumbers.v1";
    public static final String NEW_SERVICE_URL_HTTPS = "https://foo.bar";
    public static final String NEW_SERVICE_URL_HTTP = "http://foo.bar";

    @Autowired
    private ServicesApiController servicesApiController;

    @MockBean
    private GlobalConfService globalConfService;

    @Before
    public void setup() {
        when(globalConfService.getGlobalGroupDescription(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            GlobalGroupId id = (GlobalGroupId) args[0];
            return NAME_FOR + id.getGroupCode();
        });

        when(globalConfService.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return NAME_FOR + identifier.toShortString().replace("/", ":");
        });
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
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(ClientService.CLIENT_NOT_FOUND_ERROR_CODE, expected.getError().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void getServiceNotFound() {
        try {
            servicesApiController.getService(SS1_PREDICT_WINNING_LOTTERY_NUMBERS).getBody();
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(ServiceService.SERVICE_NOT_FOUND_ERROR_CODE, expected.getError().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_SERVICE_ACL", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void getServiceAccessRights() {
        List<ServiceClient> serviceClients = servicesApiController.getServiceAccessRights(SS1_GET_RANDOM).getBody();
        assertEquals(3, serviceClients.size());

        ServiceClient serviceClient = getServiceClientByType(serviceClients, GLOBALGROUP).get();
        assertEquals(NAME_FOR + GLOBAL_GROUP_CODE, serviceClient.getName());
        assertEquals(GLOBAL_GROUP_ID, serviceClient.getId());
        assertNull(serviceClient.getAccessRights());

        serviceClient = getServiceClientByType(serviceClients, LOCALGROUP).get();
        assertEquals(LOCAL_GROUP_DESC, serviceClient.getName());
        assertEquals(LOCAL_GROUP_ID, serviceClient.getId());
        assertNull(serviceClient.getAccessRights());

        serviceClient = getServiceClientByType(serviceClients, SUBSYSTEM).get();
        assertEquals(NAME_FOR + SS2_CLIENT_ID, serviceClient.getName());
        assertEquals(SUBSYSTEM + ":" + SS2_CLIENT_ID, serviceClient.getId());
        assertNull(serviceClient.getAccessRights());

        serviceClients = servicesApiController.getServiceAccessRights(SS1_CALCULATE_PRIME).getBody();
        assertTrue(serviceClients.isEmpty());

        try {
            servicesApiController.getServiceAccessRights(SS0_GET_RANDOM);
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(ClientService.CLIENT_NOT_FOUND_ERROR_CODE, expected.getError().getCode());
        }

        try {
            servicesApiController.getServiceAccessRights(SS1_PREDICT_WINNING_LOTTERY_NUMBERS);
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(ServiceService.SERVICE_NOT_FOUND_ERROR_CODE, expected.getError().getCode());
        }
    }

    private Optional<ServiceClient> getServiceClientByType(List<ServiceClient> serviceClients, String type) {
        return serviceClients
                .stream()
                .filter(serviceClient -> serviceClient.getId().contains(type))
                .findFirst();
    }
}
