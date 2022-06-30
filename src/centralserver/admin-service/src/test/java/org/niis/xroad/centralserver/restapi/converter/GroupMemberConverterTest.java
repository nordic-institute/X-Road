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
package org.niis.xroad.centralserver.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.GroupMember;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroupMember;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupMemberConverterTest {

    @Test
    void convert() {
        GlobalGroupMember mockEntity = mockEntity();
        GroupMemberConverter converter = new GroupMemberConverter();

        GroupMember result = converter.convert(mockEntity);

        assertEquals(String.valueOf(mockEntity.getId()), result.getId());
        assertEquals(mockEntity.getIdentifier().toShortString(':'), result.getName());
        assertEquals("ORG", result.getPropertyClass());
        assertEquals("CS", result.getInstance());
        assertEquals("2", result.getSubsystem());
        assertEquals("123", result.getCode());
        assertEquals("SUBSYSTEM", result.getType());
        assertEquals(mockEntity.getCreatedAt().atOffset(ZoneOffset.UTC), result.getCreatedAt());
    }

    private GlobalGroupMember mockEntity() {
        GlobalGroup globalGroup = new GlobalGroup();
        ClientId clientId = ClientId.create("CS", "ORG", "123", "2");
        GlobalGroupMember member = new GlobalGroupMember(globalGroup, clientId);
        member.setId(1);
        return member;
    }
}
