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
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.domain.AdminUser;
import org.niis.xroad.restapi.service.AdminUserService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@RequiredArgsConstructor
public class DatabaseAuthenticationProvider implements AuthenticationProvider {

    private final PasswordEncoder passwordEncoder;
    private final AdminUserService adminUserService;
    private final AuthenticationIpWhitelist authenticationIpWhitelist;
    private final GrantedAuthorityMapper grantedAuthorityMapper;
    private final RestApiAuditEvent loginEvent;
    private final AuditEventLoggingFacade auditEventLoggingFacade;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        authenticationIpWhitelist.validateIpAddress(authentication);
        String username = authentication.getPrincipal().toString();
        String presentedPassword = authentication.getCredentials().toString();

        try {
            AdminUser user = adminUserService.findAdminUser(authentication.getName())
                    .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
            if (!this.passwordEncoder.matches(presentedPassword, new String(user.getPassword()))) {
                throw new BadCredentialsException("Bad credentials");
            }
            Set<GrantedAuthority> grants = grantedAuthorityMapper.getAuthorities(user.getRoles());
            auditEventLoggingFacade.auditLogSuccess(loginEvent, username);
            return new UsernamePasswordAuthenticationToken(user.getUsername(), authentication.getCredentials(), grants);
        } catch (Exception e) {
            auditEventLoggingFacade.auditLogFail(loginEvent, e, username);
            throw e;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
