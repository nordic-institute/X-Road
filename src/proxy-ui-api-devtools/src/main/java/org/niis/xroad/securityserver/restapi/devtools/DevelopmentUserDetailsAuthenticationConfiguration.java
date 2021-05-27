/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.devtools;

import org.niis.xroad.restapi.auth.GrantedAuthorityMapper;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.niis.xroad.restapi.auth.PamAuthenticationProvider.FORM_LOGIN_PAM_AUTHENTICATION;
import static org.niis.xroad.restapi.auth.PamAuthenticationProvider.KEY_MANAGEMENT_PAM_AUTHENTICATION;

/**
 * Development time authentication provider, which uses hard coded users
 */
@Configuration
@Profile("devtools-test-auth")
public class DevelopmentUserDetailsAuthenticationConfiguration {

    public static final String PASSWORD = "password";

    @Autowired
    private GrantedAuthorityMapper grantedAuthorityMapper;

    /**
     * Create a development-time in-memory authentication provider
     * Label with names LOCALHOST_PAM_AUTHENTICATION_BEAN and REGULAR_PAM_AUTHENTICATION_BEAN
     * so that proxy-ui-api injections are satisfied
     * @return
     */
    @Bean({FORM_LOGIN_PAM_AUTHENTICATION, KEY_MANAGEMENT_PAM_AUTHENTICATION})
    public AuthenticationProvider createDevelopmentInMemoryProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        return provider;
    }

    private UserDetailsService userDetailsService() {
        Collection<UserDetails> users = new ArrayList<>();
        users.add(User.withDefaultPasswordEncoder()
                .username("security-officer")
                .password(PASSWORD)
                .authorities(grantedAuthorityMapper.getAuthorities(
                        Collections.singletonList(Role.XROAD_SECURITY_OFFICER)))
                .build());

        users.add(User.withDefaultPasswordEncoder()
                .username("registration-officer")
                .password(PASSWORD)
                .authorities(grantedAuthorityMapper.getAuthorities(
                        Collections.singletonList(Role.XROAD_REGISTRATION_OFFICER)))
                .build());

        users.add(User.withDefaultPasswordEncoder()
                .username("service-admin")
                .password(PASSWORD)
                .authorities(grantedAuthorityMapper.getAuthorities(
                        Collections.singletonList(Role.XROAD_SERVICE_ADMINISTRATOR)))
                .build());

        users.add(User.withDefaultPasswordEncoder()
                .username("system-admin")
                .password(PASSWORD)
                .authorities(grantedAuthorityMapper.getAuthorities(
                        Collections.singletonList(Role.XROAD_SYSTEM_ADMINISTRATOR)))
                .build());

        users.add(User.withDefaultPasswordEncoder()
                .username("observer")
                .password(PASSWORD)
                .authorities(grantedAuthorityMapper.getAuthorities(
                        Collections.singletonList(Role.XROAD_SECURITYSERVER_OBSERVER)))
                .build());

        users.add(User.withDefaultPasswordEncoder()
                .username("full-admin")
                .password(PASSWORD)
                .authorities(grantedAuthorityMapper.getAuthorities(Arrays.asList(Role.values())))
                .build());

        users.add(User.withDefaultPasswordEncoder()
                .username("roleless")
                .password(PASSWORD)
                .roles("NON_EXISTING_ROLE")
                .build());

        return new InMemoryUserDetailsManager(users);
    }

}
