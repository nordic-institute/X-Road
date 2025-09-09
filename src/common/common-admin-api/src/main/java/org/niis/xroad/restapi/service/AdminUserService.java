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
package org.niis.xroad.restapi.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.domain.AdminUser;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.entity.AdminUserEntity;
import org.niis.xroad.restapi.mapper.AdminUserEntityMapper;
import org.niis.xroad.restapi.repository.AdminUserRepository;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.niis.xroad.restapi.validator.AdminUserPasswordValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.CharBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.common.core.exception.ErrorCode.ACTION_NOT_POSSIBLE;
import static org.niis.xroad.common.core.exception.ErrorCode.PASSWORD_INCORRECT;
import static org.niis.xroad.restapi.auth.PasswordEncoderConfig.PASSWORD_ENCODER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SYSTEM_ADMINISTRATOR;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository userRepository;
    private final AdminUserEntityMapper mapper;
    @Qualifier(PASSWORD_ENCODER)
    private final PasswordEncoder passwordEncoder;
    private final AdminUserPasswordValidator passwordValidator;
    private final SecurityHelper securityHelper;
    private final AuditDataHelper auditDataHelper;

    public Optional<AdminUser> findAdminUser(String username) {
        return userRepository.findByUsername(username)
                .map(mapper::toDomainObject);
    }

    public List<AdminUser> getAll() {
        return userRepository.getAll()
                .stream()
                .map(mapper::toDomainObject)
                .toList();
    }

    public void create(AdminUser adminUser) {
        auditLog(adminUser.getUsername(), adminUser.getRoles());

        passwordValidator.validateUserPassword(adminUser.getPassword());

        var adminUserWithHashedPassword = new AdminUser(
                null,
                adminUser.getUsername(),
                passwordEncoder.encode(CharBuffer.wrap(adminUser.getPassword())).toCharArray(),
                adminUser.getRoles()
        );
        userRepository.create(mapper.toEntity(adminUserWithHashedPassword));
    }

    public void changePassword(String username, char[] oldPassword, char[] newPassword) {
        auditLog(username);

        var existingUser = userRepository.findByUsername(username).orElseThrow();
        var authenticatedUsername = getAuthentication().getPrincipal().toString();
        if (!securityHelper.hasAuthority(XROAD_SYSTEM_ADMINISTRATOR.getGrantedAuthorityName())
                || StringUtils.equals(authenticatedUsername, username)) {
            validateOldPassword(oldPassword, existingUser);
        }

        passwordValidator.validateUserPassword(newPassword);

        var newPasswordHashed = passwordEncoder.encode(CharBuffer.wrap(newPassword));
        existingUser.setPassword(newPasswordHashed.toCharArray());
        userRepository.update(existingUser);
    }

    public void updateRoles(String username, Set<Role> roles) {
        auditLog(username, roles);

        var existingUser = userRepository.findByUsername(username).orElseThrow();
        existingUser.setRoles(roles);
        userRepository.update(existingUser);
    }

    public void deleteByUsername(String username) {
        auditLog(username);

        var authenticatedUsername = getAuthentication().getPrincipal().toString();
        var existingUser = userRepository.findByUsername(username).orElseThrow();
        if (existingUser.getUsername().equals(authenticatedUsername)) {
            throw new BadRequestException(ACTION_NOT_POSSIBLE.build());
        }

        userRepository.delete(existingUser);
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void validateOldPassword(char[] oldPassword, AdminUserEntity existingUser) {
        if (!passwordEncoder.matches(CharBuffer.wrap(oldPassword), new String(existingUser.getPassword()))) {
            throw new BadRequestException(PASSWORD_INCORRECT.build());
        }
    }

    private void auditLog(String username) {
        auditDataHelper.put(RestApiAuditProperty.USERNAME, username);
    }

    private void auditLog(String username, Collection<Role> roles) {
        auditDataHelper.put(RestApiAuditProperty.USERNAME, username);
        auditDataHelper.put(RestApiAuditProperty.USER_ROLES, roles);
    }

}
