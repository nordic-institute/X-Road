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

package org.niis.xroad.restapi.auth.securityconfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.niis.xroad.restapi.auth.PamAuthenticationProvider.FORM_LOGIN_PAM_AUTHENTICATION;
import static org.niis.xroad.restapi.auth.securityconfigurer.Customizers.csrfTokenRequestAttributeHandler;
import static org.niis.xroad.restapi.auth.securityconfigurer.Customizers.headerPolicyDirectives;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.FORM_LOGOUT;

/**
 * form login / session cookie authentication
 * matching any url (but denying /api/*** since 2 other configurations are used
 * for those)
 * has order 100 (@Order(100))
 */
@Configuration
@Slf4j
public class FormLoginWebSecurityConfig {
    public static final String LOGIN_URL = "/login";

    @Autowired
    private AuditEventLoggingFacade auditEventLoggingFacade;

    @Bean
    @Order(MultiAuthWebSecurityConfig.FORM_LOGIN_SECURITY_ORDER)
    public SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http,
                                                            @Qualifier(FORM_LOGIN_PAM_AUTHENTICATION)
                                                            AuthenticationProvider authenticationProvider,
                                                            @Value("${server.servlet.session.cookie.same-site:Strict}") String sameSite)
            throws Exception {

        return http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(customizer -> customizer
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(LOGIN_URL).permitAll()
                        .requestMatchers("/logout").fullyAuthenticated()
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().denyAll()
                )
                .csrf(customizer -> customizer
                        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(LOGIN_URL)
                        .csrfTokenRepository(new CookieAndSessionCsrfTokenRepository(sameSite))
                )
                .headers(headerPolicyDirectives("default-src 'self' 'unsafe-inline'"))
                .formLogin(customizer -> customizer
                        .loginPage(LOGIN_URL)
                        .successHandler(formLoginStatusCodeSuccessHandler())
                        .failureHandler(statusCode401AuthenticationFailureHandler())
                        .permitAll()
                )
                .logout(customizer -> customizer
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
                        .addLogoutHandler(new AuditLoggingLogoutHandler())
                        .permitAll())
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .build();
    }

    class AuditLoggingLogoutHandler implements LogoutHandler {
        @Override
        public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
            try {
                auditEventLoggingFacade.auditLogSuccess(FORM_LOGOUT);
            } catch (Exception e) {
                log.error("failed to audit log logout", e);
            }
        }
    }

    /**
     * authentication failure handler which does not redirect but just returns a http status code
     * @return the constructed AuthenticationFailureHandler handler
     */
    private static AuthenticationFailureHandler statusCode401AuthenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request,
                    HttpServletResponse response, AuthenticationException exception)
                    throws IOException {
                response.setContentType("application/json;charset=UTF-8");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication Failed");
            }
        };
    }

    /**
     * form login success handler which does not redirect but just returns a http status code
     * @return the constructed AuthenticationSuccessHandler handler
     */
    private static AuthenticationSuccessHandler formLoginStatusCodeSuccessHandler() {
        return new SimpleUrlAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request,
                    HttpServletResponse response, Authentication authentication)
                    throws IOException {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().println("OK");
            }
        };
    }


    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            // Render the token value to a cookie by causing the deferred token to be loaded
            csrfToken.getToken();

            filterChain.doFilter(request, response);
        }
    }
}
