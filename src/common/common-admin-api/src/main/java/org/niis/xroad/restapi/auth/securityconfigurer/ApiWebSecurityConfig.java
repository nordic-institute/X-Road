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
import org.niis.xroad.restapi.auth.ApiKeyAuthenticationManager;
import org.niis.xroad.restapi.auth.Http401AuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.LazyCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import static org.niis.xroad.restapi.auth.securityconfigurer.Customizers.csrfTokenRequestAttributeHandler;
import static org.niis.xroad.restapi.auth.securityconfigurer.Customizers.headerPolicyDirectives;

/**
 * custom token / session cookie authentication for rest apis
 * matching url /api/**
 */
@Configuration
public class ApiWebSecurityConfig {
    private static final String PRINCIPAL_REQUEST_HEADER = "Authorization";

    @Bean
    @Order(MultiAuthWebSecurityConfig.API_SECURITY_ORDER)
    public SecurityFilterChain apiWebSecurityFilterChain(HttpSecurity http,
                                                         ApiKeyAuthenticationManager apiKeyAuthenticationManager,
                                                         Http401AuthenticationEntryPoint http401AuthenticationEntryPoint,
                                                         @Value("${server.servlet.session.cookie.same-site:Strict}") String sameSite)
            throws Exception {
        RequestHeaderAuthenticationFilter filter = new RequestHeaderAuthenticationFilter();
        filter.setPrincipalRequestHeader(PRINCIPAL_REQUEST_HEADER);
        filter.setAuthenticationManager(apiKeyAuthenticationManager);
        filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
        filter.setExceptionIfHeaderMissing(false); // exception at this point
        // would cause http 500, we want http 401

        return http
                .securityMatcher("/api/**")
                .addFilter(filter)
                .sessionManagement(customizer -> customizer.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .authorizeHttpRequests(customizer -> customizer.anyRequest().authenticated())
                .exceptionHandling(customizer -> customizer.authenticationEntryPoint(http401AuthenticationEntryPoint))
                .csrf(customizer -> customizer
                        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler())
                        // we require csrf protection only if there is a session alive
                        .requireCsrfProtectionMatcher(ApiWebSecurityConfig::sessionExists)
                        // CsrfFilter always generates a new token in the repo -> prevent with lazy
                        .csrfTokenRepository(new LazyCsrfTokenRepository(new CookieAndSessionCsrfTokenRepository(sameSite)))
                )
                .anonymous(AbstractHttpConfigurer::disable)
                .headers(headerPolicyDirectives("default-src 'none'"))
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    @Order(MultiAuthWebSecurityConfig.API_SECURITY_ORDER)
    public WebSecurityCustomizer apiWebSecurityCustomizer() {
        return customizer -> customizer.ignoring().requestMatchers("/api/v1/openapi.yaml");
    }

    /**
     * Check if an alive session exists
     */
    private static boolean sessionExists(HttpServletRequest request) {
        return request.getSession(false) != null;
    }

    /**
     * Disable session request cache to prevent extra session creation when api
     * calls fail.
     * This breaks (access restricted thymeleaf page -> auth error -> login -> redirect to restricted page)
     * flow, but we do not need it in our app.
     */
    @Bean
    RequestCache disableSessionRequestCache() {
        return new NullRequestCache();
    }
}
