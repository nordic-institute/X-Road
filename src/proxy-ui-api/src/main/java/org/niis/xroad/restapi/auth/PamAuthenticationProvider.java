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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PAM authentication provider.
 * Application has to be run as a user who has read access to /etc/shadow (
 * likely means that belongs to group shadow)
 * roles are granted with user groups, mappings in {@link Role}
 *
 * if {@link PamAuthenticationProvider#setLimitIps(boolean)} is set to true,
 * allows authentication only from IP addresses defined with
 * {@link PamAuthenticationProvider#setIpWhitelist(List)}. Whitelist IPs
 * can have net mask such as 192.168.1.0/24, see {@link IpAddressMatcher}.
 *
 */
@Slf4j
@Configuration
@Profile("!devtools-test-auth")
public class PamAuthenticationProvider implements AuthenticationProvider {

    // from PAMLoginModule
    private static final String PAM_SERVICE_NAME = "xroad";

    public static final String REGULAR_PAM_AUTHENTICATION_BEAN = "pamAuthentication";
    public static final String LOCALHOST_PAM_AUTHENTICATION_BEAN = "localhostPamAuthentication";

    private static final String LOCALHOST = "127.0.0.1";

    @Getter
    @Setter
    // if true, only requests from ipWhitelist are allowed to authenticate
    private boolean limitIps = false;
    @Getter
    @Setter
    private List<String> ipWhitelist = new ArrayList();

    /**
     * PAM authentication without IP limits
     * @return
     */
    @Bean(REGULAR_PAM_AUTHENTICATION_BEAN)
    public PamAuthenticationProvider regularPamAuthentication() {
        return new PamAuthenticationProvider();
    }

    /**
     * PAM authentication which is limited to localhost
     * @return
     */
    @Bean(LOCALHOST_PAM_AUTHENTICATION_BEAN)
    public PamAuthenticationProvider localhostPamAuthentication() {
        PamAuthenticationProvider pam = new PamAuthenticationProvider();
        pam.setIpWhitelist(Collections.singletonList(LOCALHOST));
        pam.setLimitIps(true);
        return pam;
    }

    /**
     * If ipLimits = true, go through the whitelisted ips and check that one of them matches
     * caller remote address. If not, throw BadRemoteAddressException
     * @param authentication
     * @throws BadRemoteAddressException if caller ip was not allowed for this authentication provider
     */
    private void validateIpAddress(Authentication authentication) {
        if (limitIps) {
            WebAuthenticationDetails details = (WebAuthenticationDetails) authentication.getDetails();
            String userIp = details.getRemoteAddress();
            for (String whiteListedIp : ipWhitelist) {
                if (new IpAddressMatcher(whiteListedIp).matches(userIp)) {
                    return;
                }
            }
            throw new BadRemoteAddressException("Invalid IP Address");
        }
    }

    public static class BadRemoteAddressException extends AuthenticationException {
        public BadRemoteAddressException(String msg) {
            super(msg);
        }
    }

    @Autowired
    private GrantedAuthorityMapper grantedAuthorityMapper;

    /**
     * users with these groups are allowed access
     */
    private static final Set<String> ALLOWED_GROUP_NAMES = Collections.unmodifiableSet(
            Arrays.stream(Role.values())
                .map(Role::getLinuxGroupName)
                .collect(Collectors.toSet()));

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        validateIpAddress(authentication);
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());
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

