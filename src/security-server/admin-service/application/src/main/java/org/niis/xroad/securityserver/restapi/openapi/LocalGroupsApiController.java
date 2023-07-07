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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.converter.LocalGroupConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroup;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDescription;
import org.niis.xroad.securityserver.restapi.openapi.model.Members;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.LocalGroupNotFoundException;
import org.niis.xroad.securityserver.restapi.service.LocalGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_LOCAL_GROUP_MEMBERS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_LOCAL_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_LOCAL_GROUP_DESC;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REMOVE_LOCAL_GROUP_MEMBERS;

/**
 * groups api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class LocalGroupsApiController implements LocalGroupsApi {

    private final LocalGroupConverter localGroupConverter;
    private final LocalGroupService localGroupService;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_LOCAL_GROUPS')")
    public ResponseEntity<LocalGroup> getLocalGroup(String groupIdString) {
        LocalGroupType localGroupType = getLocalGroupType(groupIdString);
        return new ResponseEntity<>(localGroupConverter.convert(localGroupType), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_LOCAL_GROUP_DESC')")
    @AuditEventMethod(event = EDIT_LOCAL_GROUP_DESC)
    public ResponseEntity<LocalGroup> updateLocalGroup(String groupIdString,
            LocalGroupDescription localGroupDescription) {
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        String description = localGroupDescription.getDescription();
        LocalGroupType localGroupType = null;
        try {
            localGroupType = localGroupService.updateDescription(groupId, description);
        } catch (LocalGroupNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(localGroupConverter.convert(localGroupType), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_LOCAL_GROUP_MEMBERS')")
    @AuditEventMethod(event = ADD_LOCAL_GROUP_MEMBERS)
    public ResponseEntity<Members> addLocalGroupMember(String groupIdString, Members members) {
        if (members == null || members.getItems() == null || members.getItems().size() < 1) {
            throw new BadRequestException("missing member id");
        }
        // remove duplicates
        List<String> uniqueIds = new ArrayList<>(new HashSet<>(members.getItems()));
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        try {
            localGroupService.addLocalGroupMembers(groupId, clientIdConverter.convertIds(uniqueIds));
        } catch (LocalGroupService.MemberAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (LocalGroupNotFoundException
                | LocalGroupService.LocalGroupMemberNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(members, HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_LOCAL_GROUP')")
    @AuditEventMethod(event = DELETE_LOCAL_GROUP)
    public ResponseEntity<Void> deleteLocalGroup(String groupIdString) {
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        try {
            localGroupService.deleteLocalGroup(groupId);
        } catch (LocalGroupNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ClientNotFoundException e) {
            throw new ConflictException("Client not found for the given localgroup with id: " + groupIdString);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_LOCAL_GROUP_MEMBERS')")
    @AuditEventMethod(event = REMOVE_LOCAL_GROUP_MEMBERS)
    public ResponseEntity<Void> deleteLocalGroupMember(String groupIdString, Members members) {
        LocalGroupType localGroupType = getLocalGroupType(groupIdString);
        try {
            localGroupService.deleteGroupMembers(localGroupType.getId(),
                    clientIdConverter.convertIds(members.getItems()));
        } catch (LocalGroupService.LocalGroupMemberNotFoundException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Read one group from DB, throw ResourceNotFoundException or
     * BadRequestException is needed
     */
    private LocalGroupType getLocalGroupType(String groupIdString) {
        Long groupId = FormatUtils.parseLongIdOrThrowNotFound(groupIdString);
        LocalGroupType localGroupType = localGroupService.getLocalGroup(groupId);
        if (localGroupType == null) {
            throw new ResourceNotFoundException("LocalGroup with not found");
        }
        return localGroupType;
    }
}
