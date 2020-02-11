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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.converter.TimestampingServiceConverter;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.TimestampingServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test TimestampingServiceApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class TimestampingServiceApiControllerTest {

    @MockBean
    GlobalConfService globalConfService;

    @Autowired
    private TimestampingServicesApiController timestampingServicesApiController;

    private static final Map<String, String> TIMESTAMPING_SERVICES = new HashMap<>();

    private static final String TSA_1_URL = "https://tsa.com";

    private static final String TSA_1_NAME = "TSA 1";

    private static final String TSA_2_URL = "https://example.com";

    private static final String TSA_2_NAME = "TSA 2";

    @Before
    public void setup() {

        TIMESTAMPING_SERVICES.put(TSA_1_URL, TSA_1_NAME);
        TIMESTAMPING_SERVICES.put(TSA_2_URL, TSA_2_NAME);

        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(
                new ArrayList<String>(TIMESTAMPING_SERVICES.keySet()));
        when(globalConfService.getApprovedTspName(TSA_1_URL)).thenReturn(TIMESTAMPING_SERVICES.get(TSA_1_NAME));
        when(globalConfService.getApprovedTspName(TSA_2_URL)).thenReturn(TIMESTAMPING_SERVICES.get(TSA_2_NAME));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getTimestampingServices() {
        ResponseEntity<List<TimestampingService>> response =
                timestampingServicesApiController.getTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(TIMESTAMPING_SERVICES.keySet().size(), timestampingServices.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_TSPS" })
    public void getTimestampingServicesEmptyList() {
        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(new ArrayList<String>());
        when(globalConfService.getApprovedTspName(any())).thenReturn(null);

        ResponseEntity<List<TimestampingService>> response =
                timestampingServicesApiController.getTimestampingServices();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingService> timestampingServices = response.getBody();

        assertEquals(0, timestampingServices.size());
    }
}
