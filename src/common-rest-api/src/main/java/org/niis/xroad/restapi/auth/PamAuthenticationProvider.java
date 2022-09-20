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
package org.niis.xroad.restapi.auth;

import lombok.extern.slf4j.Slf4j;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PAM authentication provider.
 * Application has to be run as a user who has read access to /etc/shadow (
 * likely means that belongs to group shadow)
 * roles are granted with user groups, mappings in {@link Role}
 *
 * Authentication is limited with an IP whitelist.
 */
@Slf4j
public class PamAuthenticationProvider implements AuthenticationProvider {

    // from PAMLoginModule
    private static final String PAM_SERVICE_NAME = "xroad";

    public static final String KEY_MANAGEMENT_PAM_AUTHENTICATION = "keyManagementPam";
    public static final String FORM_LOGIN_PAM_AUTHENTICATION = "formLoginPam";

    private final AuthenticationIpWhitelist authenticationIpWhitelist;
    private final GrantedAuthorityMapper grantedAuthorityMapper;
    private final RestApiAuditEvent loginEvent; // login event to audit log
    private final AuditEventLoggingFacade auditEventLoggingFacade;
    private final SecurityHelper securityHelper;

    /**
     * constructor
     * @param authenticationIpWhitelist whitelist that limits the authentication
     */
    public PamAuthenticationProvider(AuthenticationIpWhitelist authenticationIpWhitelist,
            GrantedAuthorityMapper grantedAuthorityMapper, RestApiAuditEvent loginEvent,
            AuditEventLoggingFacade auditEventLoggingFacade, SecurityHelper securityHelper) {
        this.authenticationIpWhitelist = authenticationIpWhitelist;
        this.grantedAuthorityMapper = grantedAuthorityMapper;
        this.loginEvent = loginEvent;
        this.auditEventLoggingFacade = auditEventLoggingFacade;
        this.securityHelper = securityHelper;
    }

    /**
     * users with these groups are allowed access
     */
    private static final Set<String> ALLOWED_GROUP_NAMES = Collections.unmodifiableSet(
            Arrays.stream(Role.values())
                    .map(Role::getLinuxGroupName)
                    .collect(Collectors.toSet()));

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        boolean success = false;
        String username = "unknown user";
        Exception caughException = null;

        try {
            username = String.valueOf(authentication.getPrincipal());
            Authentication result = doAuthenticateInternal(authentication, username);
            success = true;
            return result;
        } catch (Exception e) {
            caughException = e;
            throw e;
        } finally {
            if (success) {
                auditEventLoggingFacade.auditLogSuccess(loginEvent, username);
            } else {
                auditEventLoggingFacade.auditLogFail(loginEvent, caughException, username);
            }
        }
    }

    private Authentication doAuthenticateInternal(Authentication authentication, String username) {
        String password = String.valueOf(authentication.getCredentials());
        authenticationIpWhitelist.validateIpAddress(authentication);
        PAM pam;
        try {
            pam = new PAM(PAM_SERVICE_NAME);
        } catch (PAMException e) {
            throw new AuthenticationServiceException("Could not initialize PAM.", e);
        }
        try {
            UnixUser user = pam.authenticate(username, password);
            Set<String> groups = user.getGroups();
            Set<String> matchingGroups = groups.stream()
                    .filter(ALLOWED_GROUP_NAMES::contains)
                    .collect(Collectors.toSet());
            if (matchingGroups.isEmpty()) {
                throw new AuthenticationServiceException("user hasn't got any required groups");
            }
            Collection<Role> xroadRoles = matchingGroups.stream()
                    .map(groupName -> Role.getForGroupName(groupName).get())
                    .collect(Collectors.toSet());
            Set<GrantedAuthority> grants = grantedAuthorityMapper.getAuthorities(xroadRoles);
            return new UsernamePasswordAuthenticationToken(user.getUserName(), authentication.getCredentials(), grants);
        } catch (PAMException e) {
            throw new BadCredentialsException("PAM authentication failed.", e);
        } finally {
            pam.dispose();
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(
                UsernamePasswordAuthenticationToken.class);
    }
}

