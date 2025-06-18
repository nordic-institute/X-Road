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
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.restapi.domain.AdminUser;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.mapper.AdminUserEntityMapper;
import org.niis.xroad.restapi.repository.AdminUserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.ACTION_NOT_POSSIBLE;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.PASSWORD_INCORRECT;
import static org.niis.xroad.restapi.auth.PasswordEncoderConfig.PASSWORD_ENCODER;

@Service
@Transactional
public class AdminUserService {

    private final AdminUserRepository userRepository;
    private final AdminUserEntityMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AdminUserRepository userRepository,
                            AdminUserEntityMapper mapper,
                            @Qualifier(PASSWORD_ENCODER) PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

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
        adminUser = new AdminUser(
                null,
                adminUser.getUsername(),
                passwordEncoder.encode(adminUser.getPassword()),
                adminUser.getRoles()
        );
        userRepository.create(mapper.toEntity(adminUser));
    }

    public void changePassword(String username, String oldPassword, String newPassword) {
        var existingUser = userRepository.findByUsername(username).orElseThrow();
        if (!passwordEncoder.matches(oldPassword, existingUser.getPassword())) {
            throw new BadRequestException(PASSWORD_INCORRECT.build());
        }
        newPassword = passwordEncoder.encode(newPassword);
        existingUser.setPassword(newPassword);
        userRepository.update(existingUser);
    }

    public void updateRoles(String username, Set<Role> roles) {
        var existingUser = userRepository.findByUsername(username).orElseThrow();
        existingUser.setRoles(roles);
        userRepository.update(existingUser);
    }

    public void deleteByUsername(String username) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var authenticatedUsername = authentication.getPrincipal().toString();
        var existingUser = userRepository.findByUsername(username).orElseThrow();
        if (existingUser.getUsername().equals(authenticatedUsername)) {
            throw new BadRequestException(ACTION_NOT_POSSIBLE.build());
        }

        userRepository.delete(existingUser);
    }

}
