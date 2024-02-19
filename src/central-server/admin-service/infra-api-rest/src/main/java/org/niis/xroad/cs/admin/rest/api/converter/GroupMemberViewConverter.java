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
package org.niis.xroad.cs.admin.rest.api.converter;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMemberView;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.openapi.model.GroupMemberListViewDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterDto;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
@RequiredArgsConstructor
public class GroupMemberViewConverter {
    private final ClientIdDtoConverter clientIdDtoConverter;

    public GroupMemberListViewDto convert(GlobalGroupMemberView entity) {
        return new GroupMemberListViewDto()
                .id(entity.getId())
                .name(entity.getMemberName())
                .clientId(clientIdDtoConverter.convert(entity.getIdentifier()))
                .createdAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    public GlobalGroupMemberService.Criteria convert(String groupCode, GroupMembersFilterDto filter) {
        return GlobalGroupMemberService.Criteria.builder()
                .groupCode(groupCode)
                .query(filter.getQuery())
                .memberClass(filter.getMemberClass())
                .instance(filter.getInstance())
                .codes(filter.getCodes())
                .subsystems(filter.getSubsystems())
                .types(toTypes(filter))
                .build();
    }

    private List<XRoadObjectType> toTypes(GroupMembersFilterDto filter) {
        return filter.getTypes() != null ? filter.getTypes().stream()
                .map(type -> XRoadObjectType.forIdentifierOf(type.toString()))
                .collect(toList()) : null;
    }
}
