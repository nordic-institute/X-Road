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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.identifier.LocalGroupId;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.openapi.model.GroupMemberDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDto;
import org.niis.xroad.serverconf.model.GroupMember;
import org.niis.xroad.serverconf.model.LocalGroup;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.identifier.XRoadId.ENCODED_ID_SEPARATOR;

/**
 * Helper to convert LocalGroups
 */
@Component
@RequiredArgsConstructor
public class LocalGroupConverter {

    private final GlobalConfProvider globalConfProvider;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Converts LocalGroupType to Group
     * @param localGroup
     * @return LocalGroupDto
     */
    public LocalGroupDto convert(LocalGroup localGroup) {
        LocalGroupDto localGroupDto = new LocalGroupDto();

        localGroupDto.setId(String.valueOf(localGroup.getId()));
        localGroupDto.setCode(localGroup.getGroupCode());
        localGroupDto.setDescription(localGroup.getDescription());
        localGroupDto.setUpdatedAt(FormatUtils.fromDateToOffsetDateTime(localGroup.getUpdated()));
        localGroupDto.setMemberCount(localGroup.getGroupMember().size());
        localGroupDto.setMembers(localGroup.getGroupMember().stream().map(this::convert).collect(Collectors.toSet()));

        return localGroupDto;
    }

    /**
     * Converts a group of LocalGroupType to a list of Groups
     * @param localGroupTypes
     * @return
     */
    public Set<LocalGroupDto> convert(Iterable<LocalGroup> localGroupTypes) {
        return Streams.stream(localGroupTypes)
                .map(this::convert).collect(Collectors.toSet());
    }

    /**
     * Converts LocalGroupDto to LocalGroupType. Ignores LocalGroup#id field since it is obsolete in LocalGroupType
     * @param localGroupDto
     * @return LocalGroupType
     */
    public LocalGroup convert(LocalGroupDto localGroupDto) {
        LocalGroup localGroup = new LocalGroup();

        localGroup.setDescription(localGroupDto.getDescription());
        localGroup.setGroupCode(localGroupDto.getCode());
        localGroup.setUpdated(new Date());
        if (localGroupDto.getMembers() != null) {
            localGroup.getGroupMember().addAll(localGroupDto.getMembers().stream()
                    .map(this::convert).collect(Collectors.toList()));
        }

        return localGroup;
    }

    /**
     * Converts LocalGroupAddDto to LocalGroupType
     * @param localGroupAddDto
     * @return LocalGroupType
     */
    public LocalGroup convert(LocalGroupAddDto localGroupAddDto) {
        LocalGroup localGroup = new LocalGroup();

        localGroup.setDescription(localGroupAddDto.getDescription());
        localGroup.setGroupCode(localGroupAddDto.getCode());
        localGroup.setUpdated(new Date());

        return localGroup;
    }

    /**
     * Converts GroupMemberDto to GroupMemberType. Ignores id field
     * @param groupMemberDto
     * @return GroupMemberType
     */
    private GroupMember convert(GroupMemberDto groupMemberDto) {
        GroupMember groupMember = new GroupMember();

        groupMember.setGroupMemberId(clientIdConverter.convertId(groupMemberDto.getId()));
        groupMember.setAdded(new Date(groupMemberDto.getCreatedAt().toEpochSecond()));

        return groupMember;
    }

    /**
     * Converts GroupMemberType to GroupMemberDto. Ignores id field
     * @param groupMember
     * @return GroupMemberDto
     */
    public GroupMemberDto convert(GroupMember groupMember) {
        GroupMemberDto groupMemberDto = new GroupMemberDto();
        groupMemberDto.setId(clientIdConverter.convertId(groupMember.getGroupMemberId()));
        groupMemberDto.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(groupMember.getAdded()));
        groupMemberDto.setName(globalConfProvider.getMemberName(groupMember.getGroupMemberId()));
        return groupMemberDto;
    }

    /**
     * Convert LocalGroupId into encoded id string
     * @return String
     */
    public String convertId(LocalGroupId localGroupId) {
        return convertId(localGroupId, false);
    }

    /**
     * Convert LocalGroupId into encoded id string
     * @param localGroupId
     * @return String
     */
    public String convertId(LocalGroupId localGroupId, boolean includeType) {
        StringBuilder builder = new StringBuilder();
        if (includeType) {
            builder.append(localGroupId.getObjectType())
                    .append(ENCODED_ID_SEPARATOR);
        }
        builder.append(localGroupId.getGroupCode());
        return builder.toString().trim();
    }
}
