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
package org.niis.xroad.centralserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.centralserver.openapi.GlobalGroupsApi;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupCodeAndDescription;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupDescription;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupResource;
import org.niis.xroad.centralserver.openapi.model.Members;
import org.niis.xroad.centralserver.restapi.dto.GlobalGroupUpdateDto;
import org.niis.xroad.centralserver.restapi.service.GlobalGroupService;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_GLOBAL_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_GLOBAL_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_GLOBAL_GROUP_DESCRIPTION;
import static org.niis.xroad.restapi.openapi.ControllerUtil.API_V1_PREFIX;

@RestController
@RequestMapping(API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class GlobalGroupsApiController implements GlobalGroupsApi {

    private final GlobalGroupService globalGroupService;

    @AuditEventMethod(event = ADD_GLOBAL_GROUP)
    @PreAuthorize("hasAuthority('ADD_GLOBAL_GROUP')")
    public ResponseEntity<GlobalGroupResource> addGlobalGroup(GlobalGroupCodeAndDescription codeAndDescription) {
        return new ResponseEntity<>(globalGroupService.addGlobalGroup(codeAndDescription), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Members> addGlobalGroupMembers(Integer groupId, Members members) {
        throw new NotImplementedException("addGlobalGroupMembers not implemented yet");
    }

    @Override
    @AuditEventMethod(event = DELETE_GLOBAL_GROUP)
    @PreAuthorize("hasAuthority('DELETE_GROUP')")
    public ResponseEntity<Void> deleteGlobalGroup(Integer groupId) {
        globalGroupService.deleteGlobalGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteGlobalGroupMembers(Integer groupId, Members members) {
        throw new NotImplementedException("deleteGlobalGroupMembers not implemented yet");
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_GLOBAL_GROUPS')")
    public ResponseEntity<Set<GlobalGroupResource>> findGlobalGroups(String containsMember) {
        return ResponseEntity.ok(globalGroupService.findGlobalGroups(containsMember));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_GROUP_DETAILS')")
    public ResponseEntity<GlobalGroupResource> getGlobalGroup(Integer groupId) {
        return ResponseEntity.ok(globalGroupService.getGlobalGroup(groupId));
    }

    @Override
    @AuditEventMethod(event = EDIT_GLOBAL_GROUP_DESCRIPTION)
    @PreAuthorize("hasAuthority('EDIT_GROUP_DESCRIPTION')")
    public ResponseEntity<GlobalGroupResource> updateGlobalGroupDescription(
            Integer groupId, GlobalGroupDescription globalGroupDescription) {
        GlobalGroupUpdateDto updateDto = new GlobalGroupUpdateDto(groupId, globalGroupDescription.getDescription());
        return ResponseEntity.ok(globalGroupService.updateGlobalGroupDescription(updateDto));
    }
}
