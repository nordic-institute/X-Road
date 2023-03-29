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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.securityserver.restapi.openapi.model.Client;
import org.niis.xroad.securityserver.restapi.openapi.model.IgnoreWarnings;
import org.niis.xroad.securityserver.restapi.openapi.model.Service;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionDisabledNotice;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionUpdate;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceType;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
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
import static org.niis.xroad.securityserver.restapi.util.TestUtils.CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.OWNER_SERVER_ID;

/**
 * Test ServiceDescriptionsApiController
 */
public class ServiceDescriptionsApiControllerIntegrationTest extends AbstractApiControllerTestContext {

    @Autowired
    ServiceDescriptionsApiController serviceDescriptionsApiController;

    @Autowired
    ClientsApiController clientsApiController;

    @Before
    public void setup() {
        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? identifier.getSubsystemCode() + "NAME"
                    : "test-member" + "NAME";
        });
        when(urlValidator.isValidUrl(any())).thenReturn(true);
        when(currentSecurityServerSignCertificates.getSignCertificateInfos()).thenReturn(new ArrayList<>());
        when(serverConfService.getSecurityServerId()).thenReturn(OWNER_SERVER_ID);
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
    }

    @Test
    @WithMockUser(authorities = { "ENABLE_DISABLE_WSDL", "VIEW_CLIENT_SERVICES" })
    public void enableServiceDescription() {
        // serviceDescription that was disabled
        serviceDescriptionsApiController.enableServiceDescription("2");
        Optional<ServiceDescription> serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1).getBody(), "2");
        assertTrue(serviceDescription.isPresent());
        assertFalse(serviceDescription.get().getDisabled());
        assertEquals("Kaputt", serviceDescription.get().getDisabledNotice());

        // serviceDescription that was enabled
        serviceDescriptionsApiController.enableServiceDescription("1");
        serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1).getBody(), "1");
        assertTrue(serviceDescription.isPresent());
        assertFalse(serviceDescription.get().getDisabled());
        assertEquals("Out of order", serviceDescription.get().getDisabledNotice());

        // serviceDescriptions that do not exist
        try {
            serviceDescriptionsApiController.enableServiceDescription("10000");
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
        }
        try {
            serviceDescriptionsApiController.enableServiceDescription("non-numeric-id");
            fail("should throw ResourceNotFoundException");
        } catch (NotFoundException expected) {
        }

    }

    private Optional<ServiceDescription> getServiceDescription(Set<ServiceDescription> serviceDescriptions,
            String id) {
        return serviceDescriptions.stream()
                .filter(serviceDescription -> serviceDescription.getId().equals(id))
                .findFirst();
    }

    @Test
    @WithMockUser(authorities = { "ENABLE_DISABLE_WSDL", "VIEW_CLIENT_SERVICES" })
    public void disableServiceDescription() {
        // serviceDescription that was disabled
        serviceDescriptionsApiController.disableServiceDescription("2",
                new ServiceDescriptionDisabledNotice().disabledNotice("foo-notice"));
        Optional<ServiceDescription> serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1).getBody(), "2");
        assertTrue(serviceDescription.isPresent());
        assertTrue(serviceDescription.get().getDisabled());
        assertEquals("foo-notice", serviceDescription.get().getDisabledNotice());

        // serviceDescription that was enabled
        serviceDescriptionsApiController.disableServiceDescription("1",
                new ServiceDescriptionDisabledNotice().disabledNotice("foo-notice"));
        serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1).getBody(), "1");
        assertTrue(serviceDescription.isPresent());
        assertTrue(serviceDescription.get().getDisabled());
        assertEquals("foo-notice", serviceDescription.get().getDisabledNotice());

        // serviceDescriptions that do not exist
        try {
            serviceDescriptionsApiController.enableServiceDescription("10000");
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
        }
        try {
            serviceDescriptionsApiController.enableServiceDescription("non-numeric-id");
            fail("should throw ResourceNotFoundException");
        } catch (NotFoundException expected) {
        }

    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "DELETE_WSDL", "VIEW_CLIENT_SERVICES" })
    public void deleteServiceDescription() {
        Client client = clientsApiController.getClient(TestUtils.CLIENT_ID_SS1).getBody();
        assertNotNull(client);
        serviceDescriptionsApiController.deleteServiceDescription("2");
        Set<ServiceDescription> serviceDescriptions =
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1).getBody();
        assertEquals(CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT - 1, serviceDescriptions.size());
        client = clientsApiController.getClient(TestUtils.CLIENT_ID_SS1).getBody();
        assertNotNull(client);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES", "EDIT_WSDL" })
    public void updateServiceDescription() {
        Client client = clientsApiController.getClient(TestUtils.CLIENT_ID_SS1).getBody();
        assertNotNull(client);
        ServiceDescription serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1).getBody(), "1").get();
        assertEquals("https://soapservice.com/v1/Endpoint?wsdl", serviceDescription.getUrl());
        Set<String> serviceIds = getServiceIds(serviceDescription);
        Set<String> serviceCodes = getServiceCodes(serviceDescription);
        assertEquals(4, serviceIds.size());
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_CODE_GET_RANDOM));
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_CALCULATE_PRIME));
        assertEquals(3, serviceCodes.size());
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_CODE_GET_RANDOM));
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_CALCULATE_PRIME));

        ServiceDescriptionUpdate serviceDescriptionUpdate = new ServiceDescriptionUpdate()
                .url("file:src/test/resources/wsdl/testservice.wsdl").type(ServiceType.WSDL);
        // ignore warningDeviations about adding and removing services
        serviceDescriptionUpdate.setIgnoreWarnings(true);
        serviceDescriptionsApiController.updateServiceDescription("1", serviceDescriptionUpdate);
        client = clientsApiController.getClient(TestUtils.CLIENT_ID_SS1).getBody();
        assertNotNull(client);
        serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1).getBody(), "1").get();
        assertEquals("file:src/test/resources/wsdl/testservice.wsdl", serviceDescription.getUrl());
        serviceIds = getServiceIds(serviceDescription);
        serviceCodes = getServiceCodes(serviceDescription);
        assertEquals(2, serviceIds.size());
        assertFalse(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_CODE_GET_RANDOM));
        assertFalse(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_CALCULATE_PRIME));
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_XROAD_GET_RANDOM));
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_CODE_BMI));

        assertEquals(2, serviceCodes.size());
        assertFalse(serviceCodes.contains(TestUtils.SERVICE_CODE_GET_RANDOM));
        assertFalse(serviceCodes.contains(TestUtils.SERVICE_CALCULATE_PRIME));
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_XROAD_GET_RANDOM));
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_CODE_BMI));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "REFRESH_WSDL" })
    public void refreshServiceDescription() {
        ServiceDescription serviceDescription = getServiceDescription(
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS2).getBody(), "3").get();
        Set<String> serviceIds = getServiceIds(serviceDescription);
        Set<String> serviceCodes = getServiceCodes(serviceDescription);
        assertEquals(2, serviceIds.size());
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS2 + ":" + TestUtils.FULL_SERVICE_XROAD_GET_RANDOM_OLD));
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS2 + ":" + TestUtils.FULL_SERVICE_CODE_BMI_OLD));
        assertEquals(2, serviceCodes.size());
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_XROAD_GET_RANDOM_OLD));
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_CODE_BMI_OLD));

        // ignore warningDeviations (about adding and deleting services)
        ServiceDescription refreshed = serviceDescriptionsApiController.refreshServiceDescription("3",
                new IgnoreWarnings().ignoreWarnings(true)).getBody();
        assertEquals(serviceDescription.getId(), refreshed.getId());
        serviceIds = getServiceIds(refreshed);
        serviceCodes = getServiceCodes(refreshed);
        assertEquals(2, serviceIds.size());
        // refreshed wsdl has updated serviceIds and the refreshedDate should be updated
        assertFalse(serviceIds.contains(TestUtils.CLIENT_ID_SS2 + ":" + TestUtils.FULL_SERVICE_XROAD_GET_RANDOM_OLD));
        assertFalse(serviceIds.contains(TestUtils.CLIENT_ID_SS2 + ":" + TestUtils.FULL_SERVICE_CODE_BMI_OLD));
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS2 + ":" + TestUtils.FULL_SERVICE_XROAD_GET_RANDOM));
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS2 + ":" + TestUtils.FULL_SERVICE_CODE_BMI));

        assertEquals(2, serviceCodes.size());
        // refreshed wsdl has updated serviceCodes and the refreshedDate should be updated
        assertFalse(serviceCodes.contains(TestUtils.SERVICE_XROAD_GET_RANDOM_OLD));
        assertFalse(serviceCodes.contains(TestUtils.SERVICE_CODE_BMI_OLD));
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_XROAD_GET_RANDOM));
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_CODE_BMI));
        assertTrue(refreshed.getRefreshedAt().isAfter(serviceDescription.getRefreshedAt()));
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "REFRESH_REST" })
    public void refreshRestServiceDescriptionWithoutRights() {
        serviceDescriptionsApiController.refreshServiceDescription("6", new IgnoreWarnings().ignoreWarnings(false));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES" })
    public void getServiceDescription() {
        ResponseEntity<ServiceDescription> response =
                serviceDescriptionsApiController.getServiceDescription("1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ServiceDescription serviceDescription = response.getBody();
        assertEquals("https://soapservice.com/v1/Endpoint?wsdl", serviceDescription.getUrl());
        Set<String> serviceIds = getServiceIds(serviceDescription);
        Set<String> serviceCodes = getServiceCodes(serviceDescription);
        assertEquals(4, serviceIds.size());
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_CODE_GET_RANDOM));
        assertTrue(serviceIds.contains(TestUtils.CLIENT_ID_SS1 + ":" + TestUtils.FULL_SERVICE_CALCULATE_PRIME));
        assertEquals(3, serviceCodes.size());
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_CODE_GET_RANDOM));
        assertTrue(serviceCodes.contains(TestUtils.SERVICE_CODE_GET_RANDOM));

        try {
            serviceDescriptionsApiController.getServiceDescription("123451");
            fail("should throw ResourceNotFoundException to 404");
        } catch (ResourceNotFoundException expected) {
        }

        try {
            serviceDescriptionsApiController.getServiceDescription("ugh");
            fail("should throw ResourceNotFoundException to 404");
        } catch (NotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES" })
    public void getServiceDescriptionServices() {
        ResponseEntity<Set<Service>> response =
                serviceDescriptionsApiController.getServiceDescriptionServices("1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<Service> services = response.getBody();
        assertEquals(4, services.size());
        Service getRandomService = getService(services, TestUtils.CLIENT_ID_SS1 + ":"
                + TestUtils.FULL_SERVICE_CODE_GET_RANDOM);
        assertEquals("https://soapservice.com/v1/Endpoint", getRandomService.getUrl());
        assertEquals(TestUtils.SERVICE_CODE_GET_RANDOM, getRandomService.getServiceCode());

        // test one without services - should return OK but empty list
        // (resource exists, but is an empty collection)
        response = serviceDescriptionsApiController.getServiceDescriptionServices("4");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());

        try {
            serviceDescriptionsApiController.getServiceDescriptionServices("123451");
            fail("should throw ResourceNotFoundException to 404");
        } catch (ResourceNotFoundException expected) {
        }

        try {
            serviceDescriptionsApiController.getServiceDescriptionServices("ugh");
            fail("should throw ResourceNotFoundException to 404");
        } catch (NotFoundException expected) {
        }
    }

    private Set<String> getServiceIds(ServiceDescription serviceDescription) {
        return serviceDescription.getServices()
                .stream()
                .map(Service::getId)
                .collect(Collectors.toSet());
    }

    private Set<String> getServiceCodes(ServiceDescription serviceDescription) {
        return serviceDescription.getServices()
                .stream()
                .map(Service::getServiceCode)
                .collect(Collectors.toSet());
    }

    private Service getService(Set<Service> services, String id) {
        return services.stream()
                .filter(s -> id.equals(s.getId()))
                .findFirst().get();
    }
}
