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
package org.niis.xroad.restapi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.domain.InvalidRoleNameException;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.mapper.AdminUserDtoMapper;
import org.niis.xroad.restapi.openapi.model.AdminUser;
import org.niis.xroad.restapi.openapi.model.AdminUserPasswordChangeRequest;
import org.niis.xroad.restapi.service.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCodes.INVALID_ROLE;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("#{commonModuleEndpointPaths.adminUsersPath}")
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class AdminUsersController {

    private final AdminUserService adminUserService;
    private final AdminUserDtoMapper mapper;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ADMIN_USERS')")
    public ResponseEntity<List<AdminUser>> getAll() {
        var adminUsers = adminUserService.getAll()
                .stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(adminUsers);
    }

    @PostMapping
    @AuditEventMethod(event = RestApiAuditEvent.ADMIN_USER_ADD)
    @PreAuthorize("hasAuthority('ADD_ADMIN_USER')")
    public ResponseEntity<Void> create(@RequestBody @Valid AdminUser adminUser) {
        adminUserService.create(mapper.toDomainObject(adminUser));
        return ResponseEntity.status(CREATED).build();
    }

    @PutMapping("/{username}/password")
    @AuditEventMethod(event = RestApiAuditEvent.ADMIN_USER_CHANGE_PASSWORD)
    @PreAuthorize("hasAuthority('UPDATE_ADMIN_USER') or #username == authentication.principal")
    public ResponseEntity<Void> changePassword(
            @PathVariable("username") String username, @RequestBody @Valid AdminUserPasswordChangeRequest passwordChangeRequest) {
        adminUserService.changePassword(
                username, passwordChangeRequest.getOldPassword().toCharArray(), passwordChangeRequest.getNewPassword().toCharArray());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/roles")
    @AuditEventMethod(event = RestApiAuditEvent.ADMIN_USER_EDIT_ROLES)
    @PreAuthorize("hasAuthority('UPDATE_ADMIN_USER')")
    public ResponseEntity<Void> updateRoles(@PathVariable("username") String username, @RequestBody List<String> roles) {
        try {
            adminUserService.updateRoles(username, Role.getForNames(roles));
            return ResponseEntity.ok().build();
        } catch (InvalidRoleNameException e) {
            throw new BadRequestException(e, INVALID_ROLE.build());
        }
    }

    @DeleteMapping("/{username}")
    @AuditEventMethod(event = RestApiAuditEvent.ADMIN_USER_DELETE)
    @PreAuthorize("hasAuthority('DELETE_ADMIN_USER')")
    public ResponseEntity<Void> delete(@PathVariable("username") String username) {
        adminUserService.deleteByUsername(username);
        return ResponseEntity.ok().build();
    }

}
