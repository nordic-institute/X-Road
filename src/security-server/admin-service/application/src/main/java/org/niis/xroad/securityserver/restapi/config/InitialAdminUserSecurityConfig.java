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
package org.niis.xroad.securityserver.restapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.niis.xroad.restapi.auth.securityconfigurer.Customizers.headerPolicyDirectives;

/**
 * Permits unauthenticated access to the initial admin user bootstrap endpoints used during
 * Security Server initialization with database-based authentication. Service-level checks
 * close the bootstrap surface as soon as the first admin user exists.
 */
@Configuration
public class InitialAdminUserSecurityConfig {

    static final String[] BOOTSTRAP_ENDPOINTS = {
            "/api/v1/initialization/admin-user",
            "/api/v1/initialization/admin-user/status"
    };

    @Bean
    @Order(0)
    public SecurityFilterChain initialAdminUserSecurityFilterChain(HttpSecurity http) {
        return http
                .securityMatcher(BOOTSTRAP_ENDPOINTS)
                .sessionManagement(customizer -> customizer.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .authorizeHttpRequests(customizer -> customizer.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .headers(headerPolicyDirectives("default-src 'none'"))
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }
}
