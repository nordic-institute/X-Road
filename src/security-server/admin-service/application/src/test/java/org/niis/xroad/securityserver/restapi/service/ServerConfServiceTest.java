/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * test ServerConfService
 */
public class ServerConfServiceTest extends AbstractServiceTestContext {

    @Autowired
    ServerConfService serverConfService;

    @Test
    public void getSecurityServerId() {
        SecurityServerId.Conf expected = SecurityServerId.Conf.create("FI", "GOV", "M1", "SS1");
        assertEquals(expected, serverConfService.getSecurityServerId());
    }

    @Test
    public void getSecurityServerOwnerId() {
        ClientId expected = TestUtils.getClientId("FI", "GOV", "M1", null);
        assertEquals(expected, serverConfService.getSecurityServerOwnerId());
    }

    @Test
    public void getConfiguredTimestampingServices() {
        List<TspType> configuredTimestampingServices = new ArrayList<>();
        configuredTimestampingServices.add(TestUtils.createTspType("https://tsa3.com", "TSA 3"));
        configuredTimestampingServices.add(TestUtils.createTspType("https://tsa2.com", "TSA 2"));
        configuredTimestampingServices.add(TestUtils.createTspType("https://tsa1.com", "TSA 1"));

        when(serverConfRepository.getServerConf()).thenReturn(serverConfType);
        when(serverConfType.getTsp()).thenReturn(configuredTimestampingServices);

        List<TspType> tsp = serverConfService.getConfiguredTimestampingServices();

        assertEquals(configuredTimestampingServices.size(), tsp.size());
        assertEquals("TSA 1", tsp.get(2).getName());
        assertEquals("TSA 2", tsp.get(1).getName());
        assertEquals("TSA 3", tsp.get(0).getName());
    }
}
