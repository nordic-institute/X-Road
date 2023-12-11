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
package org.niis.xroad.restapi.auth.securityconfigurer;

import jakarta.servlet.http.HttpServletRequest;
import org.niis.xroad.restapi.auth.PamAuthenticationProvider;
import org.niis.xroad.restapi.controller.CommonModuleEndpointPaths;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.LazyCsrfTokenRepository;

import static org.niis.xroad.restapi.auth.securityconfigurer.Customizers.csrfTokenRequestAttributeHandler;
import static org.niis.xroad.restapi.auth.securityconfigurer.Customizers.headerPolicyDirectives;

/**
 * basic authentication configuration for managing api keys
 * matching url /api/api-keys/**
 */
@Configuration
public class ManageApiKeysWebSecurityConfig {



    @Bean
    @Order(MultiAuthWebSecurityConfig.API_KEY_MANAGEMENT_SECURITY_ORDER)
    public SecurityFilterChain manageApiSecurityFilterChain(HttpSecurity http,
                                                            CommonModuleEndpointPaths commonModuleEndpointPaths,
                                                            @Qualifier(PamAuthenticationProvider.KEY_MANAGEMENT_PAM_AUTHENTICATION)
                                                                AuthenticationProvider authenticationProvider,
                                                            @Value("${server.servlet.session.cookie.same-site:Strict}") String sameSite)
            throws Exception {

        return http
                .securityMatcher(commonModuleEndpointPaths.getApiKeysPath() + "/**")
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(customizer -> customizer
                        .anyRequest()
                        .authenticated()
                )
                .sessionManagement(customizer -> customizer
                        .sessionCreationPolicy(SessionCreationPolicy.NEVER)
                )
                .httpBasic(Customizer.withDefaults())
                .anonymous(AbstractHttpConfigurer::disable)
                .headers(headerPolicyDirectives("default-src 'none'"))
                .csrf(customizer -> customizer
                        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(ManageApiKeysWebSecurityConfig::sessionExists)
                        .csrfTokenRepository(new LazyCsrfTokenRepository(new CookieAndSessionCsrfTokenRepository(sameSite)))
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Check if an alive session exists
     */
    private static boolean sessionExists(HttpServletRequest request) {
        return request.getSession(false) != null;
    }

}
