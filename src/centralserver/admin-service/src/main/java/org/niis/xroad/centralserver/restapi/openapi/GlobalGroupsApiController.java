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
import org.eclipse.jetty.util.StringUtil;
import org.niis.xroad.centralserver.openapi.GlobalGroupsApi;
import org.niis.xroad.centralserver.openapi.model.GlobalGroup;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupCodeAndDescription;
import org.niis.xroad.centralserver.openapi.model.GlobalGroupDescription;
import org.niis.xroad.centralserver.openapi.model.Members;
import org.niis.xroad.centralserver.restapi.service.GlobalGroupService;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class GlobalGroupsApiController implements GlobalGroupsApi {

    private final GlobalGroupService globalGroupService;

    @Override
    public ResponseEntity<GlobalGroup> addGlobalGroup(GlobalGroupCodeAndDescription globalGroupCodeAndDescription) {
        throw new NotImplementedException("addGlobalGroup not implemented yet");
    }

    @Override
    public ResponseEntity<Members> addGlobalGroupMembers(String groupId, Members members) {
        throw new NotImplementedException("addGlobalGroupMembers not implemented yet");
    }

    @Override
    public ResponseEntity<Void> deleteGlobalGroup(String groupId) {
        throw new NotImplementedException("deleteGlobalGroup not implemented yet");
    }

    @Override
    public ResponseEntity<Void> deleteGlobalGroupMembers(String groupId, Members members) {
        throw new NotImplementedException("deleteGlobalGroupMembers not implemented yet");
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_GLOBAL_GROUPS')")
    public ResponseEntity<Set<GlobalGroup>> findGlobalGroups(String containsMember) {
        //TODO XRDDEV-2059 need to be implement or not?
        if (!StringUtil.isEmpty(containsMember)) {
            throw new NotImplementedException("If containsMember exists need to be implement");
        }
        return ResponseEntity.ok(globalGroupService.findGlobalGroups());
    }

    @Override
    public ResponseEntity<GlobalGroup> getGlobalGroup(String groupId) {
        throw new NotImplementedException("getGlobalGroup not implemented yet");
    }

    @Override
    public ResponseEntity<GlobalGroup> updateGlobalGroupDescription(
            String groupId, GlobalGroupDescription globalGroupDescription) {
        throw new NotImplementedException("updateGlobalGroupDescription not implemented yet");
    }
}
