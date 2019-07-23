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
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Test ServicesApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ServicesApiControllerIntegrationTest {

    public static final String SS1_GET_RANDOM = "FI:GOV:M1:SS1:getRandom.v1";
    public static final String SS1_CALCULATE_PRIME = "FI:GOV:M1:SS1:calculatePrime.v1";
    public static final String SEC_CATEGORY1 = "SecurityCategory1";
    public static final String SEC_CATEGORY2 = "SecurityCategory2";
    public static final String SEC_CATEGORY1_SHORT_STRING = "FI/SecurityCategory1";
    public static final String SEC_CATEGORY2_SHORT_STRING = "FI/SecurityCategory2";
    public static final String NEW_SERVICE_URL = "https://foo.bar";

    @Autowired
    private ServicesApiController servicesApiController;

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS" })
    public void updateService() {
        Service service = servicesApiController.getService(SS1_GET_RANDOM).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSecurityCategory(Arrays.asList(SEC_CATEGORY1, SEC_CATEGORY2));
        service.setSslAuth(false);
        service.setUrl(NEW_SERVICE_URL);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service);

        Service updatedService = servicesApiController.updateService(SS1_GET_RANDOM, serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(Arrays.asList(SEC_CATEGORY1_SHORT_STRING, SEC_CATEGORY2_SHORT_STRING),
                updatedService.getSecurityCategory());
        assertEquals(false, updatedService.getSslAuth());
        assertEquals(NEW_SERVICE_URL, updatedService.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS" })
    public void updateServiceAll() {
        Service service = servicesApiController.getService(SS1_GET_RANDOM).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setTimeout(10);
        service.setSecurityCategory(Arrays.asList(SEC_CATEGORY1, SEC_CATEGORY2));
        service.setSslAuth(false);
        service.setUrl(NEW_SERVICE_URL);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service).securityCategoryAll(true).urlAll(true)
                .sslAuthAll(true).timeoutAll(true);

        Service updatedService = servicesApiController.updateService(SS1_GET_RANDOM, serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(Arrays.asList(SEC_CATEGORY1_SHORT_STRING, SEC_CATEGORY2_SHORT_STRING),
                updatedService.getSecurityCategory());
        assertEquals(false, updatedService.getSslAuth());
        assertEquals(NEW_SERVICE_URL, updatedService.getUrl());

        Service otherServiceFromSameServiceDesc = servicesApiController.getService(SS1_CALCULATE_PRIME).getBody();

        assertEquals(10, otherServiceFromSameServiceDesc.getTimeout().intValue());
        assertEquals(Arrays.asList(SEC_CATEGORY1_SHORT_STRING, SEC_CATEGORY2_SHORT_STRING),
                otherServiceFromSameServiceDesc.getSecurityCategory());
        assertEquals(false, otherServiceFromSameServiceDesc.getSslAuth());
        assertEquals(NEW_SERVICE_URL, otherServiceFromSameServiceDesc.getUrl());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES", "EDIT_SERVICE_PARAMS" })
    public void updateServiceOnlyUrlAll() {
        Service service = servicesApiController.getService(SS1_GET_RANDOM).getBody();
        assertEquals(60, service.getTimeout().intValue());

        service.setSecurityCategory(Arrays.asList(SEC_CATEGORY1, SEC_CATEGORY2));
        service.setTimeout(10);
        service.setSslAuth(true);
        service.setUrl(NEW_SERVICE_URL);
        ServiceUpdate serviceUpdate = new ServiceUpdate().service(service).urlAll(true);

        Service updatedService = servicesApiController.updateService(SS1_GET_RANDOM, serviceUpdate).getBody();
        assertEquals(10, updatedService.getTimeout().intValue());
        assertEquals(Arrays.asList(SEC_CATEGORY1_SHORT_STRING, SEC_CATEGORY2_SHORT_STRING),
                updatedService.getSecurityCategory());
        assertEquals(true, updatedService.getSslAuth());
        assertEquals(NEW_SERVICE_URL, updatedService.getUrl());

        Service otherServiceFromSameServiceDesc = servicesApiController.getService(SS1_CALCULATE_PRIME).getBody();

        assertEquals(60, otherServiceFromSameServiceDesc.getTimeout().intValue());
        assertEquals(new ArrayList<>(), otherServiceFromSameServiceDesc.getSecurityCategory());
        assertEquals(false, otherServiceFromSameServiceDesc.getSslAuth());
        assertEquals(NEW_SERVICE_URL, otherServiceFromSameServiceDesc.getUrl());
    }
}
