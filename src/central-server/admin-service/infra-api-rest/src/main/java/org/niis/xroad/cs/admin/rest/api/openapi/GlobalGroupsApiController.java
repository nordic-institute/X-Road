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
package org.niis.xroad.cs.admin.rest.api.openapi;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.dto.GlobalGroupUpdateDto;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.rest.api.converter.GlobalGroupConverter;
import org.niis.xroad.cs.admin.rest.api.converter.GroupMemberConverter;
import org.niis.xroad.cs.admin.rest.api.converter.GroupMemberFilterModelConverter;
import org.niis.xroad.cs.admin.rest.api.converter.PageRequestConverter;
import org.niis.xroad.cs.admin.rest.api.converter.PagedGroupMemberConverter;
import org.niis.xroad.cs.openapi.GlobalGroupsApi;
import org.niis.xroad.cs.openapi.model.GlobalGroupCodeAndDescriptionDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupDescriptionDto;
import org.niis.xroad.cs.openapi.model.GlobalGroupResourceDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterDto;
import org.niis.xroad.cs.openapi.model.GroupMembersFilterModelDto;
import org.niis.xroad.cs.openapi.model.MembersDto;
import org.niis.xroad.cs.openapi.model.PagedGroupMemberDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@RestController
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class GlobalGroupsApiController implements GlobalGroupsApi {
    private final GlobalGroupService globalGroupService;
    private final GlobalGroupMemberService globalGroupMemberService;
    private final GroupMemberConverter groupMemberConverter;
    private final PageRequestConverter pageRequestConverter;
    private final PagedGroupMemberConverter pagedGroupMemberConverter;
    private final GlobalGroupConverter globalGroupConverter;
    private final GroupMemberFilterModelConverter groupMemberFilterModelConverter;
    private final PageRequestConverter.MappableSortParameterConverter findSortParameterConverter =
            new PageRequestConverter.MappableSortParameterConverter(
                    entry("name", "memberName"),
                    entry("type", "identifier.objectType"),
                    entry("instance", "identifier.xRoadInstance"),
                    entry("class", "identifier.memberClass"),
                    entry("code", "identifier.memberCode"),
                    entry("subsystem", "identifier.subsystemCode"),
                    entry("created_at", "createdAt")
            );

    @AuditEventMethod(event = RestApiAuditEvent.ADD_GLOBAL_GROUP)
    @PreAuthorize("hasAuthority('ADD_GLOBAL_GROUP')")
    public ResponseEntity<GlobalGroupResourceDto> addGlobalGroup(GlobalGroupCodeAndDescriptionDto codeAndDescription) {
        var globalGroup = globalGroupConverter.toEntity(codeAndDescription);

        var persistedGlobalGroup = globalGroupService.addGlobalGroup(globalGroup);
        return new ResponseEntity<>(
                globalGroupConverter.convert(persistedGlobalGroup, globalGroupService.countGroupMembers(persistedGlobalGroup.getId())),
                CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_AND_REMOVE_GROUP_MEMBERS')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_GLOBAL_GROUP_MEMBERS)
    public ResponseEntity<MembersDto> addGlobalGroupMembers(Integer groupId, MembersDto members) {
        final var response = new MembersDto();
        response.setItems(globalGroupService.addGlobalGroupMembers(groupId, toMembersList(members)));
        return status(CREATED)
                .body(response);
    }

    @Override
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_GLOBAL_GROUP)
    @PreAuthorize("hasAuthority('DELETE_GROUP')")
    public ResponseEntity<Void> deleteGlobalGroup(Integer groupId) {
        globalGroupService.deleteGlobalGroupMember(groupId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_GLOBAL_GROUP_MEMBER)
    @PreAuthorize("hasAuthority('ADD_AND_REMOVE_GROUP_MEMBERS')")
    public ResponseEntity<Void> deleteGlobalGroupMember(Integer groupId, Integer memberId) {
        globalGroupMemberService.removeMemberFromGlobalGroup(groupId, memberId);
        return ResponseEntity.noContent().build();
    }


    @Override
    @PreAuthorize("hasAuthority('VIEW_GLOBAL_GROUPS')")
    public ResponseEntity<List<GlobalGroupResourceDto>> findGlobalGroups() {
        final var globalGroups = globalGroupService.findGlobalGroups();
        final var memberCounts = globalGroupService.countGroupMembers();
        return ok(globalGroups.stream()
                .map(group -> globalGroupConverter.convert(group, memberCounts.getOrDefault(group.getId(), 0L).intValue()))
                .collect(toList()));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_GROUP_DETAILS')")
    public ResponseEntity<PagedGroupMemberDto> findGlobalGroupMembers(Integer groupId, GroupMembersFilterDto filter) {
        var pageRequest = pageRequestConverter.convert(filter.getPagingSorting(), findSortParameterConverter);
        var resultPage = globalGroupMemberService.find(groupMemberConverter.convert(groupId, filter), pageRequest);

        PagedGroupMemberDto pagedResults = pagedGroupMemberConverter.convert(resultPage, filter.getPagingSorting());
        return ok(pagedResults);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_GROUP_DETAILS')")
    public ResponseEntity<GlobalGroupResourceDto> getGlobalGroup(Integer groupId) {
        var globalGroup = globalGroupConverter.convert(
                globalGroupService.getGlobalGroup(groupId),
                globalGroupService.countGroupMembers(groupId));
        return ok(globalGroup);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_GROUP_DETAILS')")
    public ResponseEntity<GroupMembersFilterModelDto> getGroupMembersFilterModel(Integer groupId) {
        var globalGroupMembers = globalGroupMemberService.findByGroupId(groupId);
        var groupMemberFilterModelDto = groupMemberFilterModelConverter.convert(globalGroupMembers);

        return ok(groupMemberFilterModelDto);
    }

    @Override
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_GLOBAL_GROUP_DESCRIPTION)
    @PreAuthorize("hasAuthority('EDIT_GROUP_DESCRIPTION')")
    public ResponseEntity<GlobalGroupResourceDto> updateGlobalGroupDescription(
            Integer groupId, GlobalGroupDescriptionDto globalGroupDescription) {
        GlobalGroupUpdateDto updateDto = new GlobalGroupUpdateDto(groupId, globalGroupDescription.getDescription());

        var updatedGlobalGroup = globalGroupService.updateGlobalGroupDescription(updateDto);
        var updatedGlobalGroupDto = globalGroupConverter.convert(
                updatedGlobalGroup,
                globalGroupService.countGroupMembers(groupId));
        return ok(updatedGlobalGroupDto);
    }

    private List<String> toMembersList(MembersDto membersDto) {
        return Optional.ofNullable(membersDto)
                .map(MembersDto::getItems)
                .stream().flatMap(Collection::stream)
                .collect(toList());
    }

}
