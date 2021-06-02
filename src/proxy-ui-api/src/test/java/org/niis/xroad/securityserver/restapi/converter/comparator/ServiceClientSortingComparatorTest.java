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
package org.niis.xroad.securityserver.restapi.converter.comparator;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test ServiceClientSortingComparator
 */
public class ServiceClientSortingComparatorTest {
    private static final String INSTANCE_ID = "TEST";

    @Test
    public void testClientSorting() {
        List<ServiceClient> clients = new ArrayList<ServiceClient>();
        clients.add(createTestServiceClient("Client", "TEST:COM:33456:Client"));
        clients.add(createTestServiceClient("client", "TEST:COM:33455:Client"));
        clients.add(createTestServiceClient(null, "TEST:ORG:33456:Service1"));
        clients.add(createTestServiceClient(null, "TEST:ORG:33455:Client1"));
        clients.add(createTestServiceClient(null, "TEST:ORG:33456"));
        clients.add(createTestServiceClient(null, "TEST:ORG:33455"));
        clients.add(createTestServiceClient("äClient", "TEST:GOV:12345:Service"));
        clients.add(createTestServiceClient("ÄClient", "TEST:GOV:12346:Service"));
        clients.add(createTestServiceClient("AClient", "TEST:GOV:12345"));
        clients.add(createTestServiceClient("AClient", "TEST:ORG:12345"));
        clients.add(createTestServiceClient("AClient", "TEST:GOV:12345:System2"));
        clients.add(createTestServiceClient("AClient", "TEST:GOV:12345:System1"));
        clients.add(createTestServiceClient("AClient", "TEST:ORG:12345:System1"));
        clients.add(createTestServiceClient("aClient", "TEST:GOV:12346:System1"));
        clients.add(createTestServiceClient("aClient", "TEST:GOV:12346"));
        clients.add(createTestServiceClient("Local group", "12346"));
        clients.add(createTestServiceClient("Global group", "TEST:security-server-owners"));

        Collections.sort(clients, new ServiceClientSortingComparator());

        assertEquals("AClient", clients.get(0).getName());
        assertEquals("TEST:GOV:12345", clients.get(0).getId());

        assertEquals("AClient", clients.get(1).getName());
        assertEquals("TEST:GOV:12345:System1", clients.get(1).getId());

        assertEquals("AClient", clients.get(2).getName());
        assertEquals("TEST:GOV:12345:System2", clients.get(2).getId());

        assertEquals("aClient", clients.get(3).getName());
        assertEquals("TEST:GOV:12346", clients.get(3).getId());

        assertEquals("aClient", clients.get(4).getName());
        assertEquals("TEST:GOV:12346:System1", clients.get(4).getId());

        assertEquals("AClient", clients.get(5).getName());
        assertEquals("TEST:ORG:12345", clients.get(5).getId());

        assertEquals("AClient", clients.get(6).getName());
        assertEquals("TEST:ORG:12345:System1", clients.get(6).getId());

        assertEquals("client", clients.get(7).getName());
        assertEquals("TEST:COM:33455:Client", clients.get(7).getId());

        assertEquals("Client", clients.get(8).getName());
        assertEquals("TEST:COM:33456:Client", clients.get(8).getId());

        assertEquals("Global group", clients.get(9).getName());
        assertEquals("TEST:security-server-owners", clients.get(9).getId());

        assertEquals("Local group", clients.get(10).getName());
        assertEquals("12346", clients.get(10).getId());

        assertEquals("äClient", clients.get(11).getName());
        assertEquals("TEST:GOV:12345:Service", clients.get(11).getId());

        assertEquals("ÄClient", clients.get(12).getName());
        assertEquals("TEST:GOV:12346:Service", clients.get(12).getId());

        assertEquals(null, clients.get(13).getName());
        assertEquals("TEST:ORG:33455", clients.get(13).getId());

        assertEquals(null, clients.get(14).getName());
        assertEquals("TEST:ORG:33455:Client1", clients.get(14).getId());

        assertEquals(null, clients.get(15).getName());
        assertEquals("TEST:ORG:33456", clients.get(15).getId());

        assertEquals(null, clients.get(16).getName());
        assertEquals("TEST:ORG:33456:Service1", clients.get(16).getId());
    }

    private ServiceClient createTestServiceClient(String name, String id) {
        ServiceClient serviceClient = new ServiceClient();
        serviceClient.setName(name);
        serviceClient.setId(id);
        return serviceClient;
    }
}
