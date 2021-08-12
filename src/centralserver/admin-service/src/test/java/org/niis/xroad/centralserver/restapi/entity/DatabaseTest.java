/**
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
package org.niis.xroad.centralserver.restapi.entity;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@DataJpaTest
@Slf4j
public class DatabaseTest {
    @Autowired
    private TestEntityManager entityManager;

    @Test
    public void testPersistence() {
        XRoadMember member = new XRoadMember();
        member.setIdentifier(ClientId.create("TEST", "CLASS", "CODE"));
        member.getSubsystems().add(new Subsystem(member, "SUBSYSTEM"));
        member = entityManager.persist(member);

        final SecurityServer server = new SecurityServer();
        server.setOwner(member);
        server.setServerCode("SERVERCODE");

        ServerClient sc = new ServerClient();
        sc.setSecurityServer(server);
        sc.setSecurityServerClient(member.getSubsystems().iterator().next());
        server.getServerClients().add(sc);

        entityManager.persist(server);

        entityManager.flush();
        entityManager.clear();

        member = entityManager.find(XRoadMember.class, member.getId());
        assertEquals(1, member.getSubsystems().size());
        assertEquals(1, member.getOwnedServers().size());
        final Subsystem sub = member.getSubsystems().iterator().next();
        assertEquals(1, sub.getServerClients().size());
    }

    @SpringBootApplication
    static class TestApplication {

    }
}
