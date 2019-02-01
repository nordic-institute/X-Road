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
package org.niis.xroad.restapi.auth;

import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * test PAM authentication provider.
 * Application has to be run as a user who has read access to /etc/shadow (likely means that belongs to group shadow)
 * roles are granted with user groups xroad-auth-proto-user and xroad-auth-proto-admin
 */
public class PamAuthenticationProvider implements AuthenticationProvider {

    static Logger logger = LoggerFactory.getLogger(PamAuthenticationProvider.class);

    /**
     * users with these groups are allowed access
     */
    private static final Set<String> ALLOWED_GROUP_NAMES = Collections.unmodifiableSet(
            Stream.of("xroad-security-officer",
            "xroad-registration-officer",
            "xroad-service-administrator",
            "xroad-system-administrator",
            "xroad-securityserver-observer").collect(Collectors.toSet()));

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());
        PAM pam;
        try {
            pam = new PAM("XROAD-AUTH-PROTO");
        } catch (PAMException e) {
            throw new AuthenticationServiceException("Could not initialize PAM.", e);
        }
        try {
            logger.info("trying PAM login with {}/{}", username, password);
            UnixUser user = pam.authenticate(username, password);
            logger.info("logged in successfully");
            Set<String> groups = user.getGroups();
            logger.info("got groups: {}", groups);
            Set<GrantedAuthority> grants = new HashSet<>();
            Set<String> matchingGroups = groups.stream()
                    .filter(ALLOWED_GROUP_NAMES::contains)
                    .collect(Collectors.toSet());
            if (matchingGroups.isEmpty()) {
                throw new AuthenticationServiceException("user hasn't got any required groups");
            }
            for (String groupName: matchingGroups) {
                grants.add(new SimpleGrantedAuthority("ROLE_" + groupName.toUpperCase()));
            }
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

