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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.TspType;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.TimestampingService;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class SystemServiceTest {

    @Autowired
    private SystemService systemService;

    @MockBean
    private ServerConfService serverConfService;

    @MockBean
    private GlobalConfService globalConfService;

    private static final String TSA_1_URL = "https://tsa.com";

    private static final String TSA_1_NAME = "TSA 1";

    private static final String TSA_2_URL = "https://example.com";

    private static final String TSA_2_NAME = "TSA 2";

    @Before
    public void setup() {

        systemService.setInternalKeyPath("src/test/resources/internal.key");

        TspType tsa1 = TestUtils.createTspType(TSA_1_URL, TSA_1_NAME);
        TspType tsa2 = TestUtils.createTspType(TSA_2_URL, TSA_2_NAME);

        when(globalConfService.getApprovedTspsForThisInstance()).thenReturn(
                Arrays.asList(tsa1, tsa2));
        when(globalConfService.getApprovedTspName(TSA_1_URL))
                .thenReturn(tsa1.getName());
        when(globalConfService.getApprovedTspName(TSA_2_URL))
                .thenReturn(tsa2.getName());
        when(systemService.getConfiguredTimestampingServices()).thenReturn(new ArrayList<>(Arrays.asList(tsa1)));
    }

    @Test
    public void generateInternalCsr() throws Exception {
        byte[] csrBytes = systemService.generateInternalCsr("C=FI, serialNumber=123");
        assertTrue(csrBytes.length > 0);
    }

    @Test(expected = InvalidDistinguishedNameException.class)
    public void generateInternalCsrFail() throws Exception {
        systemService.generateInternalCsr("this is wrong");
    }

    @Test
    public void addConfiguredTimestampingService() throws
            SystemService.DuplicateConfiguredTimestampingServiceException, TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_2_URL, TSA_2_NAME);

        assertEquals(1, serverConfService.getConfiguredTimestampingServices().size());

        systemService.addConfiguredTimestampingService(timestampingService);

        assertEquals(2, serverConfService.getConfiguredTimestampingServices().size());
        assertEquals(TSA_2_NAME, serverConfService.getConfiguredTimestampingServices().get(1).getName());
        assertEquals(TSA_2_URL, serverConfService.getConfiguredTimestampingServices().get(1).getUrl());
    }

    @Test
    public void addConfiguredTimestampingServiceNonApproved() throws
            SystemService.DuplicateConfiguredTimestampingServiceException {
        TimestampingService timestampingService = TestUtils
                .createTimestampingService("http://test.com", "TSA 3");

        try {
            systemService.addConfiguredTimestampingService(timestampingService);
            fail("should throw TimestampingServiceNotFoundException");
        } catch (TimestampingServiceNotFoundException expected) {
            // success
        }
    }

    @Test
    public void addConfiguredTimestampingServiceDuplicate() throws TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        try {
            systemService.addConfiguredTimestampingService(timestampingService);
            fail("should throw DuplicateConfiguredTimestampingServiceException");
        } catch (SystemService.DuplicateConfiguredTimestampingServiceException expected) {
            // success
        }
    }

    @Test
    public void deleteConfiguredTimestampingService() throws TimestampingServiceNotFoundException {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_1_URL, TSA_1_NAME);

        assertEquals(1, serverConfService.getConfiguredTimestampingServices().size());

        systemService.deleteConfiguredTimestampingService(timestampingService);

        assertEquals(0, serverConfService.getConfiguredTimestampingServices().size());
    }

    @Test
    public void deleteConfiguredTimestampingServiceNonExisting() {
        TimestampingService timestampingService = TestUtils.createTimestampingService(TSA_2_URL, TSA_2_NAME);

        try {
            systemService.deleteConfiguredTimestampingService(timestampingService);
            fail("should throw TimestampingServiceNotFoundException");
        } catch (TimestampingServiceNotFoundException expected) {
            // success
        }
    }
}
