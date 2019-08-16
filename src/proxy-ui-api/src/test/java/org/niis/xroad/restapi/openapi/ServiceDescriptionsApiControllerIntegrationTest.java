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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.converter.GlobalConfWrapper;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionDisabledNotice;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionUpdate;
import org.niis.xroad.restapi.openapi.model.ServiceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test ServiceDescriptionsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ServiceDescriptionsApiControllerIntegrationTest {

    public static final String CLIENT_ID_SS1 = "FI:GOV:M1:SS1";
    // services from initial test data: src/test/resources/data.sql
    public static final String GET_RANDOM = "getRandom.v1";
    public static final String CALCULATE_PRIME = "calculatePrime.v1";
    // services from wsdl test file: src/test/resources/testservice.wsdl
    public static final String XROAD_GET_RANDOM = "xroadGetRandom.v1";
    public static final String BMI = "bodyMassIndex.v1";

    @Autowired
    private ServiceDescriptionsApiController serviceDescriptionsApiController;

    @Autowired
    private ClientsApiController clientsApiController;

    @MockBean
    private GlobalConfWrapper globalConfWrapper;

    @Before
    public void setup() {
        when(globalConfWrapper.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? identifier.getSubsystemCode() + "NAME"
                    : "test-member" + "NAME";
        });
    }

    @Test
    @WithMockUser(authorities = { "ENABLE_DISABLE_WSDL", "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void enableServiceDescription() {
        // serviceDescription that was disabled
        serviceDescriptionsApiController.enableServiceDescription("2");
        Optional<ServiceDescription> serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1).getBody(), "2");
        assertTrue(serviceDescription.isPresent());
        assertFalse(serviceDescription.get().getDisabled());
        assertEquals("Kaputt", serviceDescription.get().getDisabledNotice());

        // serviceDescription that was enabled
        serviceDescriptionsApiController.enableServiceDescription("1");
        serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1).getBody(), "1");
        assertTrue(serviceDescription.isPresent());
        assertFalse(serviceDescription.get().getDisabled());
        assertEquals("Out of order", serviceDescription.get().getDisabledNotice());

        // serviceDescriptions that do not exist
        try {
            serviceDescriptionsApiController.enableServiceDescription("10000");
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
        }
        try {
            serviceDescriptionsApiController.enableServiceDescription("non-numeric-id");
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
        }

    }

    private Optional<ServiceDescription> getServiceDescription(List<ServiceDescription> serviceDescriptions,
            String id) {
        return serviceDescriptions.stream()
                .filter(serviceDescription -> serviceDescription.getId().equals(id))
                .findFirst();
    }

    @Test
    @WithMockUser(authorities = { "ENABLE_DISABLE_WSDL", "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void disableServiceDescription() {
        // serviceDescription that was disabled
        serviceDescriptionsApiController.disableServiceDescription("2",
                new ServiceDescriptionDisabledNotice().disabledNotice("foo-notice"));
        Optional<ServiceDescription> serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1).getBody(), "2");
        assertTrue(serviceDescription.isPresent());
        assertTrue(serviceDescription.get().getDisabled());
        assertEquals("foo-notice", serviceDescription.get().getDisabledNotice());

        // serviceDescription that was enabled
        serviceDescriptionsApiController.disableServiceDescription("1",
                new ServiceDescriptionDisabledNotice().disabledNotice("foo-notice"));
        serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1).getBody(), "1");
        assertTrue(serviceDescription.isPresent());
        assertTrue(serviceDescription.get().getDisabled());
        assertEquals("foo-notice", serviceDescription.get().getDisabledNotice());

        // serviceDescriptions that do not exist
        try {
            serviceDescriptionsApiController.enableServiceDescription("10000");
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
        }
        try {
            serviceDescriptionsApiController.enableServiceDescription("non-numeric-id");
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) {
        }

    }

    @Test
    @WithMockUser(authorities = { "DELETE_WSDL", "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void deleteServiceDescription() {
        Client client = clientsApiController.getClient(CLIENT_ID_SS1).getBody();
        assertNotNull(client);
        serviceDescriptionsApiController.deleteServiceDescription("2");
        List<ServiceDescription> serviceDescriptions =
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1).getBody();
        assertEquals(1, serviceDescriptions.size());
        client = clientsApiController.getClient(CLIENT_ID_SS1).getBody();
        assertNotNull(client);
    }

    @Test
    @WithMockUser(authorities = { "EDIT_WSDL", "VIEW_CLIENT_SERVICES", "VIEW_CLIENT_DETAILS" })
    public void updateServiceDescription() {
        Client client = clientsApiController.getClient(CLIENT_ID_SS1).getBody();
        assertNotNull(client);
        ServiceDescription serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1).getBody(), "1").get();
        assertEquals("https://soapservice.com/v1/Endpoint?wsdl", serviceDescription.getUrl());
        Set<String> serviceIds = serviceDescription.getServices()
                .stream()
                .map(Service::getId)
                .collect(Collectors.toSet());
        assertEquals(2, serviceIds.size());
        assertTrue(serviceIds.contains(GET_RANDOM));
        assertTrue(serviceIds.contains(CALCULATE_PRIME));

        ServiceDescriptionUpdate serviceDescriptionUpdate = new ServiceDescriptionUpdate()
                .url("file:src/test/resources/testservice.wsdl").type(ServiceType.WSDL);
        serviceDescriptionsApiController.updateServiceDescription("1", false, serviceDescriptionUpdate);
        client = clientsApiController.getClient(CLIENT_ID_SS1).getBody();
        assertNotNull(client);
        serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1).getBody(), "1").get();
        assertEquals("file:src/test/resources/testservice.wsdl", serviceDescription.getUrl());
        serviceIds = serviceDescription.getServices()
                .stream()
                .map(Service::getId)
                .collect(Collectors.toSet());
        assertEquals(2, serviceIds.size());
        assertFalse(serviceIds.contains(GET_RANDOM));
        assertFalse(serviceIds.contains(CALCULATE_PRIME));
        assertTrue(serviceIds.contains(XROAD_GET_RANDOM));
        assertTrue(serviceIds.contains(BMI));
    }
}
