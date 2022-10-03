/**
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
package org.niis.xroad.centralserver.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.GroupMemberDto;
import org.niis.xroad.centralserver.openapi.model.MemberGlobalGroupDto;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroupMember;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupMemberConverterTest {

    private final GroupMemberConverter converter = new GroupMemberConverter();

    @Test
    void convert() {
        GlobalGroupMember mockEntity = mockEntity(1, "CS", "ORG", "123", "2");

        GroupMemberDto result = converter.convert(mockEntity);

        assertEquals(String.valueOf(mockEntity.getId()), result.getId());
        assertEquals(mockEntity.getIdentifier().toShortString(':'), result.getName());
        assertEquals("ORG", result.getPropertyClass());
        assertEquals("CS", result.getInstance());
        assertEquals("2", result.getSubsystem());
        assertEquals("123", result.getCode());
        assertEquals("SUBSYSTEM", result.getType());
        assertEquals(mockEntity.getCreatedAt().atOffset(ZoneOffset.UTC), result.getCreatedAt());
    }

    private GlobalGroupMember mockEntity(int id, String instance, String memberClass, String memberCode, String subsystem) {
        GlobalGroup globalGroup = new GlobalGroup();
        globalGroup.setGroupCode("code-" + id);
        ClientId clientId = ClientId.Conf.create(instance, memberClass, memberCode, subsystem);
        GlobalGroupMember member = new GlobalGroupMember(globalGroup, clientId);
        member.setId(id);
        return member;
    }

    @Test
    void testConvertMemberGlobalGroups() {
        final Set<MemberGlobalGroupDto> result = converter.convertMemberGlobalGroups(List.of(
                mockEntity(1, "CS1", "ORG1", "111", "subsystem1"),
                mockEntity(2, "CS2", "ORG2", "222", null)
        ));

        assertEquals(2, result.size());

        final MemberGlobalGroupDto group1 = result.stream().filter(x -> x.getGroupCode().equals("code-1")).findFirst().get();
        final MemberGlobalGroupDto group2 = result.stream().filter(x -> x.getGroupCode().equals("code-2")).findFirst().get();
        assertMemberGlobalGroup(group1, "code-1", "subsystem1");
        assertMemberGlobalGroup(group2, "code-2", null);
    }

    private void assertMemberGlobalGroup(MemberGlobalGroupDto memberGroup, String groupCode, String subsystem) {
        assertNotNull(memberGroup);
        assertEquals(groupCode, memberGroup.getGroupCode());
        if (subsystem != null) {
            assertEquals(subsystem, memberGroup.getSubsystem());
        } else {
            assertNull(memberGroup.getSubsystem());
        }
        assertNotNull(memberGroup.getAddedToGroup());
    }

}
