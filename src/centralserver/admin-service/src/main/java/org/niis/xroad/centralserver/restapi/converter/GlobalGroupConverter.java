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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupResource;
import org.niis.xroad.centralserver.openapi.model.GroupMember;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroupMember;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GlobalGroupConverter {

    private final GroupMemberConverter groupMemberConverter;

    public GlobalGroupResource convert(GlobalGroup entity) {
        return new GlobalGroupResource()
                .id(String.valueOf(entity.getId()))
                .code(entity.getGroupCode())
                .memberCount(entity.getMemberCount())
                .members(convertMembers(entity.getGlobalGroupMembers()))
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }

    private Set<GroupMember> convertMembers(Set<GlobalGroupMember> memberEntities) {
        return memberEntities.stream()
                .map(groupMemberConverter::convert)
                .collect(Collectors.toSet());
    }
}
