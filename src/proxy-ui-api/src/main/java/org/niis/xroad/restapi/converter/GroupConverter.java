/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.conf.serverconf.model.GroupMemberType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;

import org.niis.xroad.restapi.openapi.model.Group;
import org.niis.xroad.restapi.openapi.model.GroupMember;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper to convert Groups
 */
@Component
public class GroupConverter {

    private final ClientConverter clientConverter;
    private final GlobalConfWrapper globalConfWrapper;

    @Autowired
    public GroupConverter(ClientConverter clientConverter, GlobalConfWrapper globalConfWrapper) {
        this.clientConverter = clientConverter;
        this.globalConfWrapper = globalConfWrapper;
    }

    /**
     * Converts LocalGroupType to Group
     * @param localGroupType
     * @return Group
     */
    public Group convert(LocalGroupType localGroupType) {
        Group group = new Group();

        group.setId(String.valueOf(localGroupType.getId()));
        group.setCode(localGroupType.getGroupCode());
        group.setDescription(localGroupType.getDescription());
        group.setUpdatedAt(FormatUtils.fromDateToOffsetDateTime(localGroupType.getUpdated()));
        group.setMemberCount(localGroupType.getGroupMember().size());
        group.setMembers(localGroupType.getGroupMember().stream().map(this::convert).collect(Collectors.toList()));

        return group;
    }

    /**
     * Converts a list of LocalGroupType to a list of Groups
     * @param localGroupTypes
     * @return
     */
    public List<Group> convert(List<LocalGroupType> localGroupTypes) {
        return localGroupTypes.stream()
                .map(this::convert).collect(Collectors.toList());
    }

    /**
     * Converts Group to LocalGroupType. Ignores Group#id field since it is obsolete in LocalGroupType
     * @param group
     * @return LocalGroupType
     */
    public LocalGroupType convert(Group group) {
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
     * Converts GroupMember to GroupMemberType. Ignores id field
     * @param groupMember
     * @return GroupMemberType
     */
    private GroupMemberType convert(GroupMember groupMember) {
        GroupMemberType groupMemberType = new GroupMemberType();

        groupMemberType.setGroupMemberId(clientConverter.convertId(groupMember.getId()));
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
        groupMember.setId(clientConverter.convertId(groupMemberType.getGroupMemberId()));
        groupMember.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(groupMemberType.getAdded()));
        groupMember.setName(globalConfWrapper.getMemberName(groupMemberType.getGroupMemberId()));
        return groupMember;
    }
}
