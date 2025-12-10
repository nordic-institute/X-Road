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
package org.niis.xroad.restapi.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.UserAuthenticationConfig;
import org.niis.xroad.restapi.config.UserRoleConfig;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.service.AdminUserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;

import static org.niis.xroad.restapi.auth.AuthenticationIpWhitelist.KEY_MANAGEMENT_API_WHITELIST;
import static org.niis.xroad.restapi.auth.PasswordEncoderConfig.PASSWORD_ENCODER;
import static org.niis.xroad.restapi.auth.securityconfigurer.FormLoginWebSecurityConfig.FORM_LOGIN_AUTHENTICATION;
import static org.niis.xroad.restapi.auth.securityconfigurer.ManageApiKeysWebSecurityConfig.KEY_MANAGEMENT_AUTHENTICATION;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthenticationProviderConfig {

    // allow all ipv4 and ipv6
    private static final Iterable<String> FORM_LOGIN_IP_WHITELIST = Arrays.asList("::/0", "0.0.0.0/0");

    private final UserAuthenticationConfig userAuthenticationConfig;
    private final AdminUserService adminUserService;
    private final GrantedAuthorityMapper grantedAuthorityMapper;
    private final AuditEventLoggingFacade auditEventLoggingFacade;
    private final UserRoleConfig userRoleConfig;

    /**
     * Authentication for form login, with corresponding IP whitelist
     * @return AuthenticationProvider
     */
    @Bean(FORM_LOGIN_AUTHENTICATION)
    public AuthenticationProvider formLoginAuthentication(@Qualifier(PASSWORD_ENCODER) PasswordEncoder passwordEncoder) {
        AuthenticationIpWhitelist formLoginWhitelist = new AuthenticationIpWhitelist();
        formLoginWhitelist.setWhitelistEntries(FORM_LOGIN_IP_WHITELIST);

        if (UserAuthenticationConfig.AuthenticationProviderType.DATABASE == userAuthenticationConfig.getAuthenticationProvider()) {
            return new DatabaseAuthenticationProvider(passwordEncoder, adminUserService, formLoginWhitelist,
                    grantedAuthorityMapper, RestApiAuditEvent.FORM_LOGIN, auditEventLoggingFacade);
        } else {
            return new PamAuthenticationProvider(formLoginWhitelist,
                    grantedAuthorityMapper, userRoleConfig.getUserRoleMappings(), RestApiAuditEvent.FORM_LOGIN, auditEventLoggingFacade);
        }
    }

    /**
     * Authentication for key management API, with corresponding IP whitelist
     * @return AuthenticationProvider
     */
    @Bean(KEY_MANAGEMENT_AUTHENTICATION)
    public AuthenticationProvider keyManagementAuthentication(@Qualifier(PASSWORD_ENCODER) PasswordEncoder passwordEncoder,
            @Qualifier(KEY_MANAGEMENT_API_WHITELIST) AuthenticationIpWhitelist keyManagementWhitelist) {

        if (UserAuthenticationConfig.AuthenticationProviderType.DATABASE == userAuthenticationConfig.getAuthenticationProvider()) {
            return new DatabaseAuthenticationProvider(passwordEncoder, adminUserService, keyManagementWhitelist,
                    grantedAuthorityMapper, RestApiAuditEvent.FORM_LOGIN, auditEventLoggingFacade);
        } else {
            return new PamAuthenticationProvider(keyManagementWhitelist, grantedAuthorityMapper, userRoleConfig.getUserRoleMappings(),
                    RestApiAuditEvent.KEY_MANAGEMENT_PAM_LOGIN, auditEventLoggingFacade);
        }
    }

}
