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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.conf.serverconf.model.GroupMemberType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.identifier.LocalGroupId;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.openapi.model.GroupMember;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroup;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAdd;
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

    private final GlobalConfFacade globalConfFacade;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Converts LocalGroupType to Group
     * @param localGroupType
     * @return Group
     */
    public LocalGroup convert(LocalGroupType localGroupType) {
        LocalGroup group = new LocalGroup();

        group.setId(String.valueOf(localGroupType.getId()));
        group.setCode(localGroupType.getGroupCode());
        group.setDescription(localGroupType.getDescription());
        group.setUpdatedAt(FormatUtils.fromDateToOffsetDateTime(localGroupType.getUpdated()));
        group.setMemberCount(localGroupType.getGroupMember().size());
        group.setMembers(localGroupType.getGroupMember().stream().map(this::convert).collect(Collectors.toSet()));

        return group;
    }

    /**
     * Converts a group of LocalGroupType to a list of Groups
     * @param localGroupTypes
     * @return
     */
    public Set<LocalGroup> convert(Iterable<LocalGroupType> localGroupTypes) {
        return Streams.stream(localGroupTypes)
                .map(this::convert).collect(Collectors.toSet());
    }

    /**
     * Converts LocalGroup to LocalGroupType. Ignores LocalGroup#id field since it is obsolete in LocalGroupType
     * @param group
     * @return LocalGroupType
     */
    public LocalGroupType convert(LocalGroup group) {
        LocalGroupType localGroupType = new LocalGroupType();

        localGroupType.setDescription(group.getDescription());
        localGroupType.setGroupCode(group.getCode());
        localGroupType.setUpdated(new Date());
        if (group.getMembers() != null) {
            localGroupType.getGroupMember().addAll(group.getMembers().stream()
                    .map(this::convert).collect(Collectors.toList()));
        }

        return localGroupType;
    }

    /**
     * Converts LocalGroupAdd to LocalGroupType
     * @param group
     * @return LocalGroupType
     */
    public LocalGroupType convert(LocalGroupAdd group) {
        LocalGroupType localGroupType = new LocalGroupType();

        localGroupType.setDescription(group.getDescription());
        localGroupType.setGroupCode(group.getCode());
        localGroupType.setUpdated(new Date());

        return localGroupType;
    }

    /**
     * Converts GroupMember to GroupMemberType. Ignores id field
     * @param groupMember
     * @return GroupMemberType
     */
    private GroupMemberType convert(GroupMember groupMember) {
        GroupMemberType groupMemberType = new GroupMemberType();

        groupMemberType.setGroupMemberId(clientIdConverter.convertId(groupMember.getId()));
        groupMemberType.setAdded(new Date(groupMember.getCreatedAt().toEpochSecond()));

        return groupMemberType;
    }

    /**
     * Converts GroupMemberType to GroupMember. Ignores id field
     * @param groupMemberType
     * @return GroupMember
     */
    public GroupMember convert(GroupMemberType groupMemberType) {
        GroupMember groupMember = new GroupMember();
        groupMember.setId(clientIdConverter.convertId(groupMemberType.getGroupMemberId()));
        groupMember.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(groupMemberType.getAdded()));
        groupMember.setName(globalConfFacade.getMemberName(groupMemberType.getGroupMemberId()));
        return groupMember;
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
