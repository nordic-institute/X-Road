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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.converter.GroupConverter;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.Group;
import org.niis.xroad.restapi.openapi.model.InlineObject3;
import org.niis.xroad.restapi.openapi.model.InlineObject4;
import org.niis.xroad.restapi.service.GroupService;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * groups api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class GroupsApiController implements GroupsApi {

    private final ClientConverter clientConverter;
    private final GroupConverter groupConverter;
    private final GroupService groupsService;

    /**
     * GroupsApiController constructor
     * @param clientConverter
     * @param groupConverter
     * @param groupsService
     */
    @Autowired
    public GroupsApiController(ClientConverter clientConverter, GroupConverter groupConverter,
            GroupService groupsService) {
        this.clientConverter = clientConverter;
        this.groupConverter = groupConverter;
        this.groupsService = groupsService;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_LOCAL_GROUPS')")
    public ResponseEntity<Group> getGroup(String groupIdString) {
        LocalGroupType localGroupType = getLocalGroupType(groupIdString);
        return new ResponseEntity<>(groupConverter.convert(localGroupType), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_LOCAL_GROUP_DESC')")
    public ResponseEntity<Group> updateGroup(String groupIdString, String description) {
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        LocalGroupType localGroupType = groupsService.updateDescription(groupId, description);
        return new ResponseEntity<>(groupConverter.convert(localGroupType), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_LOCAL_GROUP_MEMBERS')")
    public ResponseEntity<Void> addGroupMember(String groupIdString, InlineObject3 memberItemsWrapper) {
        if (memberItemsWrapper == null || memberItemsWrapper.getItems() == null
                || memberItemsWrapper.getItems().size() < 1) {
            throw new InvalidParametersException("missing member id");
        }
        // remove duplicates
        List<String> uniqueIds = new ArrayList<>(new HashSet<>(memberItemsWrapper.getItems()));
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        groupsService.addLocalGroupMembers(groupId, clientConverter.convertIds(uniqueIds));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_LOCAL_GROUP')")
    public ResponseEntity<Void> deleteGroup(String groupIdString) {
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        groupsService.deleteLocalGroup(groupId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_LOCAL_GROUP_MEMBERS')")
    public ResponseEntity<Void> deleteGroupMember(String groupIdString, InlineObject4 memberItemsWrapper) {
        LocalGroupType localGroupType = getLocalGroupType(groupIdString);
        groupsService.deleteGroupMember(localGroupType, clientConverter.convertIds(memberItemsWrapper.getItems()));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * Read one group from DB, throw NotFoundException or
     * BadRequestException is needed
     */
    private LocalGroupType getLocalGroupType(String groupIdString) {
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        LocalGroupType localGroupType = groupsService.getLocalGroup(groupId);
        if (localGroupType == null) {
            throw new NotFoundException("LocalGroup with not found");
        }
        return localGroupType;
    }
}
