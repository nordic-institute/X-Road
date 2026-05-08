/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.restapi.config.UserAuthenticationConfig;
import org.niis.xroad.restapi.config.UserAuthenticationConfig.AuthenticationProviderType;
import org.niis.xroad.restapi.domain.AdminUser;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.service.AdminUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.INITIAL_ADMIN_USER_NOT_ALLOWED;

/**
 * Handles creation of the very first admin user when database-based authentication is enabled
 * and the Security Server has not yet been initialized. The endpoints backed by this service are
 * unauthenticated; bootstrap state is verified for every call so the entry point closes as soon
 * as an admin exists or the server is fully initialized.
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("permitAll()")
@RequiredArgsConstructor
public class InitialAdminUserService {

    private static final Set<Role> BOOTSTRAP_ADMIN_ROLES = EnumSet.of(
            Role.XROAD_SECURITY_OFFICER,
            Role.XROAD_REGISTRATION_OFFICER,
            Role.XROAD_SERVICE_ADMINISTRATOR,
            Role.XROAD_SYSTEM_ADMINISTRATOR,
            Role.XROAD_SECURITYSERVER_OBSERVER);

    private final UserAuthenticationConfig userAuthenticationConfig;
    private final AdminUserService adminUserService;
    private final ServerConfService serverConfService;
    private final TokenService tokenService;

    /**
     * @return true when the unauthenticated initial admin user creation flow should be offered
     */
    public boolean isInitialAdminUserRequired() {
        return isDatabaseAuthentication() && !hasAnyAdminUser() && !isServerFullyInitialized();
    }

    /**
     * Creates the very first admin user. Re-checks bootstrap state inside the transaction so
     * concurrent callers cannot create more than one bootstrap user, and assigns the default
     * full-admin role set that mirrors the PAM <code>xroad-admin</code> Linux user.
     *
     * @throws InitialAdminUserNotAllowedException when bootstrap state no longer holds
     */
    public void createInitialAdminUser(String username, char[] password) {
        if (!isInitialAdminUserRequired()) {
            throw new InitialAdminUserNotAllowedException();
        }
        adminUserService.create(new AdminUser(null, username, password, BOOTSTRAP_ADMIN_ROLES));
    }

    private boolean isDatabaseAuthentication() {
        return AuthenticationProviderType.DATABASE == userAuthenticationConfig.getAuthenticationProvider();
    }

    private boolean hasAnyAdminUser() {
        return adminUserService.count() > 0;
    }

    private boolean isServerFullyInitialized() {
        return serverConfService.isServerCodeInitialized()
                && serverConfService.isServerOwnerInitialized()
                && tokenService.isSoftwareTokenInitialized();
    }

    /**
     * Thrown when initial admin user creation is rejected because bootstrap state no longer holds.
     */
    public static class InitialAdminUserNotAllowedException extends ConflictException {
        public InitialAdminUserNotAllowedException() {
            super("Initial admin user creation is not allowed in current state",
                    INITIAL_ADMIN_USER_NOT_ALLOWED.build());
        }
    }
}
