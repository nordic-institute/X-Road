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
package org.niis.xroad.cs.admin.jpa.repository;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Deprecated. Should be replaced by servicetests")
public class JpaSecurityServerClientRepositoryTest extends AbstractRepositoryTest {

    private final ClientIdEntity memberId = MemberIdEntity.create("TEST", "CLASS", "MEMBER");

    @Autowired
    private JpaIdentifierRepository<ClientIdEntity> identifierRepository;
    @Autowired
    private JpaMemberClassRepository memberClassRepository;

    @Autowired
    private JpaXRoadMemberRepository xRoadMemberRepository;

    @Nested
    @DisplayName("findOneBy(ClientId id, XRoadObjectType explicitType)")
    class FindOneByMethod {

        @Test
        @DisplayName("should return a security server client when found by client id")
        public void shouldReturnASecurityServerClientWhenFoundByClientId() {
            MemberClassEntity memberClass = memberClassRepository.save(new MemberClassEntity("CLASS", "description"));
            ClientIdEntity persistedMemberId = identifierRepository.save(memberId);
            xRoadMemberRepository.save(new XRoadMemberEntity("TestMember", persistedMemberId, memberClass));

            SecurityServerClientEntity client = xRoadMemberRepository.findOneBy(memberId, null).getOrNull();

            assertNotNull(client);
            assertEquals(memberId, client.getIdentifier());
        }

        @Test
        @DisplayName("should return a security server client when found by client id and")
        public void shouldReturnASecurityServerClientWhenFoundByClientIdAndExplicitObjectType() {
            MemberClassEntity memberClass = memberClassRepository.save(new MemberClassEntity("CLASS", "description"));
            ClientIdEntity persistedMemberId = identifierRepository.save(memberId);
            xRoadMemberRepository.save(new XRoadMemberEntity("TestMember", persistedMemberId, memberClass));

            SecurityServerClientEntity client = xRoadMemberRepository.findOneBy(memberId, XRoadObjectType.MEMBER).getOrNull();

            assertNotNull(client);
            assertEquals(memberId, client.getIdentifier());
        }

        @Test
        @DisplayName("should not return a security server client when matching object client id and type not present")
        public void shouldReturnASecurityServerClientWhenMatchingObjectClientIdAndTypeNotPresent() {
            MemberClassEntity memberClass = memberClassRepository.save(new MemberClassEntity("CLASS", "description"));
            ClientIdEntity persistedMemberId = identifierRepository.save(memberId);
            xRoadMemberRepository.save(new XRoadMemberEntity("TestMember", persistedMemberId, memberClass));

            SecurityServerClientEntity client = xRoadMemberRepository.findOneBy(memberId, XRoadObjectType.SERVER).getOrNull();

            assertNull(client);
        }

        @Test
        @DisplayName("should throw an exception if client explicit object type is SUBSYSTEM but subsystem code is null")
        public void shouldThrowAnExceptionIfClientExplicitObjectTypeIsSubsystemButSubsystemCodeIsNull() {
            MemberClassEntity memberClass = memberClassRepository.save(new MemberClassEntity("CLASS", "description"));
            ClientIdEntity persistedMemberId = identifierRepository.save(memberId);
            xRoadMemberRepository.save(new XRoadMemberEntity("TestMember", persistedMemberId, memberClass));

            Executable testable = () -> xRoadMemberRepository.findOneBy(memberId, XRoadObjectType.SUBSYSTEM);

            RuntimeException thrown = assertThrows(RuntimeException.class, testable);
            assertEquals("Subsystem code is null", thrown.getMessage());
        }
    }

}
