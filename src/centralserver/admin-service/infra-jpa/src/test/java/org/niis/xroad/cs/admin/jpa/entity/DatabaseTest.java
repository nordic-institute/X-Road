/**
 * The MIT License
 * <p>
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
package org.niis.xroad.cs.admin.jpa.entity;


import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.jpa.repository.AbstractRepositoryTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@Deprecated
@Disabled("Deprecated. Should be replaced by servicetests")
@Slf4j
public class DatabaseTest extends AbstractRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testPersistence() {
        MemberClassEntity memberClass = entityManager.persist(new MemberClassEntity("CLASS", "Description for CLASS"));
        MemberIdEntity memberId = entityManager.persist(MemberIdEntity.create("TEST", "CLASS", "CODE"));
        SubsystemIdEntity subsystemId = entityManager.persist(SubsystemIdEntity.create("TEST", "CLASS", "CODE", "SUBSYSTEM"));

        XRoadMemberEntity member = new XRoadMemberEntity("XRoadMemberName", memberId, memberClass);
        member.getSubsystems().add(new SubsystemEntity(member, subsystemId));
        member = entityManager.persist(member);

        final SecurityServerEntity server = new SecurityServerEntity(member, "SERVERCODE");

        server.setAddress("ss1.example.org");

        ServerClientEntity sc = new ServerClientEntity();
        sc.setSecurityServer(server);
        sc.setSecurityServerClient(member.getSubsystems().iterator().next());
        server.getServerClients().add(sc);

        entityManager.persist(server);

        entityManager.flush();
        entityManager.clear();

        member = entityManager.find(XRoadMemberEntity.class, member.getId());
        Assertions.assertEquals(1, member.getSubsystems().size());
        Assertions.assertEquals(1, member.getOwnedServers().size());
        final SubsystemEntity sub = member.getSubsystems().iterator().next();
        Assertions.assertEquals(1, sub.getServerClients().size());
    }

}
