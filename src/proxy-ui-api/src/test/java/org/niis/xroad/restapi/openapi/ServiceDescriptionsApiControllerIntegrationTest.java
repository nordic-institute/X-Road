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

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionDisabledNotice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test ServiceDescriptionsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ServiceDescriptionsApiControllerIntegrationTest {

    public static final String CLIENT_ID_SS1 = "FI:GOV:M1:SS1";

    @Autowired
    private ServiceDescriptionsApiController serviceDescriptionsApiController;

    @Autowired
    private ClientsApiController clientsApiController;


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
        } catch (NotFoundException expected) { }
        try {
            serviceDescriptionsApiController.enableServiceDescription("non-numeric-id");
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) { }

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
        } catch (NotFoundException expected) { }
        try {
            serviceDescriptionsApiController.enableServiceDescription("non-numeric-id");
            fail("should throw NotFoundException");
        } catch (NotFoundException expected) { }


    }
}
