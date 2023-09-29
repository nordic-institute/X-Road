/*
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
import org.niis.xroad.securityserver.restapi.openapi.model.Client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test ClientSortingComparator
 */
public class ClientSortingComparatorTest {
    private static final String INSTANCE_ID = "TEST";

    @Test
    public void testClientSorting() {
        List<Client> clients = new ArrayList<Client>();
        clients.add(createTestClient("Client", "COM", "33456", "Client"));
        clients.add(createTestClient("client", "COM", "33455", "Client"));
        clients.add(createTestClient(null, "ORG", "33456", "Service1"));
        clients.add(createTestClient(null, "ORG", "33455", "Client1"));
        clients.add(createTestClient(null, "ORG", "33456", null));
        clients.add(createTestClient(null, "ORG", "33455", null));
        clients.add(createTestClient("äClient", "GOV", "12345", "Service"));
        clients.add(createTestClient("ÄClient", "GOV", "12346", "Service"));
        clients.add(createTestClient("AClient", "GOV", "12345", null));
        clients.add(createTestClient("AClient", "ORG", "12345", null));
        clients.add(createTestClient("AClient", "GOV", "12345", "System2"));
        clients.add(createTestClient("AClient", "GOV", "12345", "System1"));
        clients.add(createTestClient("AClient", "ORG", "12345", "System1"));
        clients.add(createTestClient("aClient", "GOV", "12346", "System1"));
        clients.add(createTestClient("aClient", "GOV", "12346", null));

        Collections.sort(clients, new ClientSortingComparator());

        assertEquals("AClient", clients.get(0).getMemberName());
        assertEquals("GOV", clients.get(0).getMemberClass());
        assertEquals("12345", clients.get(0).getMemberCode());
        assertEquals(null, clients.get(0).getSubsystemCode());

        assertEquals("AClient", clients.get(1).getMemberName());
        assertEquals("GOV", clients.get(1).getMemberClass());
        assertEquals("12345", clients.get(1).getMemberCode());
        assertEquals("System1", clients.get(1).getSubsystemCode());

        assertEquals("AClient", clients.get(2).getMemberName());
        assertEquals("GOV", clients.get(2).getMemberClass());
        assertEquals("12345", clients.get(2).getMemberCode());
        assertEquals("System2", clients.get(2).getSubsystemCode());

        assertEquals("aClient", clients.get(3).getMemberName());
        assertEquals("GOV", clients.get(3).getMemberClass());
        assertEquals("12346", clients.get(3).getMemberCode());
        assertEquals(null, clients.get(3).getSubsystemCode());

        assertEquals("aClient", clients.get(4).getMemberName());
        assertEquals("GOV", clients.get(4).getMemberClass());
        assertEquals("12346", clients.get(4).getMemberCode());
        assertEquals("System1", clients.get(4).getSubsystemCode());

        assertEquals("AClient", clients.get(5).getMemberName());
        assertEquals("ORG", clients.get(5).getMemberClass());
        assertEquals("12345", clients.get(5).getMemberCode());
        assertEquals(null, clients.get(5).getSubsystemCode());

        assertEquals("AClient", clients.get(6).getMemberName());
        assertEquals("ORG", clients.get(6).getMemberClass());
        assertEquals("12345", clients.get(6).getMemberCode());
        assertEquals("System1", clients.get(6).getSubsystemCode());

        assertEquals("client", clients.get(7).getMemberName());
        assertEquals("COM", clients.get(7).getMemberClass());
        assertEquals("33455", clients.get(7).getMemberCode());
        assertEquals("Client", clients.get(7).getSubsystemCode());

        assertEquals("Client", clients.get(8).getMemberName());
        assertEquals("COM", clients.get(8).getMemberClass());
        assertEquals("33456", clients.get(8).getMemberCode());
        assertEquals("Client", clients.get(8).getSubsystemCode());

        assertEquals("äClient", clients.get(9).getMemberName());
        assertEquals("GOV", clients.get(9).getMemberClass());
        assertEquals("12345", clients.get(9).getMemberCode());
        assertEquals("Service", clients.get(9).getSubsystemCode());

        assertEquals("ÄClient", clients.get(10).getMemberName());
        assertEquals("GOV", clients.get(10).getMemberClass());
        assertEquals("12346", clients.get(10).getMemberCode());
        assertEquals("Service", clients.get(10).getSubsystemCode());

        assertEquals(null, clients.get(11).getMemberName());
        assertEquals("ORG", clients.get(11).getMemberClass());
        assertEquals("33455", clients.get(11).getMemberCode());
        assertEquals(null, clients.get(11).getSubsystemCode());

        assertEquals(null, clients.get(12).getMemberName());
        assertEquals("ORG", clients.get(12).getMemberClass());
        assertEquals("33455", clients.get(12).getMemberCode());
        assertEquals("Client1", clients.get(12).getSubsystemCode());

        assertEquals(null, clients.get(13).getMemberName());
        assertEquals("ORG", clients.get(13).getMemberClass());
        assertEquals("33456", clients.get(13).getMemberCode());
        assertEquals(null, clients.get(13).getSubsystemCode());

        assertEquals(null, clients.get(14).getMemberName());
        assertEquals("ORG", clients.get(14).getMemberClass());
        assertEquals("33456", clients.get(14).getMemberCode());
        assertEquals("Service1", clients.get(14).getSubsystemCode());
    }

    private Client createTestClient(String name, String memberClass, String memberCode, String subsystemCode) {
        Client client = new Client();
        client.setInstanceId(INSTANCE_ID);
        client.setMemberName(name);
        client.setMemberClass(memberClass);
        client.setMemberCode(memberCode);
        client.setSubsystemCode(subsystemCode);
        client.setId(getClientId(memberClass, memberCode, subsystemCode));
        return client;
    }

    private String getClientId(String memberClass, String memberCode, String subsystemCode) {
        StringBuilder sb = new StringBuilder();
        sb.append(INSTANCE_ID).append(":").append(memberClass).append(":").append(memberCode);
        if (subsystemCode != null) {
            sb.append(":").append(subsystemCode);
        }
        return sb.toString();
    }
}
