/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EndpointServiceIntegrationTest extends AbstractServiceIntegrationTestContext {

    @Autowired
    ClientService clientService;

    @Autowired
    EndpointService endpointService;

    @Test
    public void getServiceBaseEndpoints() throws Exception {
        Set<String> serviceCodes = new HashSet<>(Arrays.asList("getRandom", "openapi-servicecode", "rest-servicecode"));
        ClientEntity owner = clientService.getLocalClientEntity(TestUtils.getClientId("FI:GOV:M1:SS1"));
        List<EndpointEntity> endpoints = endpointService.getServiceBaseEndpointEntities(owner, serviceCodes);
        assertEquals(3, endpoints.size());
        Set<Long> expectedIds = new HashSet<>(Arrays.asList(1L, 4L, 5L));
        Set<Long> ids = endpoints.stream().map(EndpointEntity::getId).collect(Collectors.toSet());
        assertEquals(expectedIds, ids);

        Set<String> wrongServiceCodes = new HashSet<>(Arrays.asList("getRandom", "openapi-servicecode", "wrong"));
        assertThrows(EndpointNotFoundException.class, () ->
                endpointService.getServiceBaseEndpointEntities(owner, wrongServiceCodes));

        ClientEntity wrongOwner = clientService.getLocalClientEntity(TestUtils.getClientId("FI:GOV:M1:SS2"));
        assertThrows(EndpointNotFoundException.class, () ->
                endpointService.getServiceBaseEndpointEntities(wrongOwner, serviceCodes));
    }
}
