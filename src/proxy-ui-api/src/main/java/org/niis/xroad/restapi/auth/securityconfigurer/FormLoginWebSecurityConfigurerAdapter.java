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
package org.niis.xroad.restapi.auth.securityconfigurer;

import ee.ria.xroad.common.AuditLogger;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.util.UsernameHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static org.niis.xroad.restapi.auth.PamAuthenticationProvider.FORM_LOGIN_PAM_AUTHENTICATION;

/**
 * form login / session cookie authentication
 * matching any url (but denying /api/*** since 2 other configurations are used
 * for those)
 * has order 100 (@Order(100))
 */
@Configuration
@Order(MultiAuthWebSecurityConfig.FORM_LOGIN_SECURITY_ORDER)
@Slf4j
public class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

    @Autowired
    @Qualifier(FORM_LOGIN_PAM_AUTHENTICATION)
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private UsernameHelper usernameHelper;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/error").permitAll()
                .antMatchers("/login").permitAll()
                .antMatchers("/logout").fullyAuthenticated()
                .antMatchers("/api/**").denyAll()
                .anyRequest().denyAll()
                .and()
            .csrf()
                .ignoringAntMatchers("/login")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .and()
            .formLogin()
                .loginPage("/login")
                .successHandler(formLoginStatusCodeSuccessHandler())
                .failureHandler(statusCode401AuthenticationFailureHandler())
                .permitAll()
                .and()
            .logout()
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
                .addLogoutHandler(new AuditLoggingLogoutHandler())
                .permitAll();
    }

    class AuditLoggingLogoutHandler implements LogoutHandler {
        @Override
        public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
            try {
                AuditLogger.log("Log out user", usernameHelper.getUsername(), null);
            } catch (Exception e) {
                log.error("failed to audit log logout", e);
            }
        }
    }


    @Override
    protected void configure(AuthenticationManagerBuilder builder) throws Exception {
        builder.authenticationProvider(authenticationProvider);
    }
    /**
     * authentication failure handler which does not redirect but just returns a http status code
     * @return
     */
    private static AuthenticationFailureHandler statusCode401AuthenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler() {
            public void onAuthenticationFailure(HttpServletRequest request,
                                                HttpServletResponse response, AuthenticationException exception)
                    throws IOException, ServletException {
                response.setContentType("application/json;charset=UTF-8");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication Failed");
            }
        };
    }

    /**
     * form login success handler which does not redirect but just returns a http status code
     * @return
     */
    private static AuthenticationSuccessHandler formLoginStatusCodeSuccessHandler() {
        return new SimpleUrlAuthenticationSuccessHandler() {
            public void onAuthenticationSuccess(HttpServletRequest request,
                                                HttpServletResponse response, Authentication authentication)
                    throws IOException, ServletException {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().println("OK");
            }
        };
    }
}
