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
package org.niis.xroad.authproto.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Security configuration follows https://spring.io/guides/gs/securing-web/
 * adapted to multiple security configs:
 * https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#multiple-httpsecurity
 *
 * Users are either PAM users (linux username and login, see PamAuthenticationProvider)
 * or dummy in-memory users (user/password etc), depending on if proto.pam property is true.
 *
 * Uses form login and session cookie based auth for
 * - simple server-side rendered spring mvc thymeleaf UI
 * - rest apis with Vue frontend
 *
 * Uses http basic authentication for create-api-key api.
 *
 * Uses authentication tokens for rest apis, when session cookies are not available.
 *
 * Built from three different WebSecurityConfigurerAdapters.
 * Authentication configurations are used in the following order:
 * - CreateApiKeyWebSecurityConfigurerAdapter, @Order(1), matches /test-api/create-api-key/**
 * - ApiWebSecurityConfigurerAdapter, @Order(2), matches /test-api/**
 * - FormLoginWebSecurityConfigurerAdapter, @Order(100), matches any URL (denies /test-api/**)
 */
@EnableWebSecurity(debug = true)
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MultiAuthWebSecurityConfig {

    static Logger logger = LoggerFactory.getLogger(WebSecurityConfigurerAdapter.class);

    /**
     * form login / session cookie authentication
     * matching any url (but denying /test-api/*** since 2 other configurations are used
     * for those)
     * has order 100 (@Order(100)) - see WebSecurityConfigurerAdapter
     */
    @Configuration
    public static class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

        @Value("${proto.pam}")
        private boolean pam;
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            logger.info("***** configuring security, pam = {}", pam);
            http
                .authorizeRequests()
                    .antMatchers("/", "/home").permitAll()
                    // to access h2 in-memory-db console, go to http://localhost:8020/h2-console/
                    // and login with username: sa, password: (empty)
                    .antMatchers("/h2-console/**").permitAll()
                    .antMatchers("/error").permitAll()
                    .antMatchers("/csrf").permitAll()
//CHECKSTYLE.OFF: TodoComment - need this todo and still want builds to succeed
                    // TODO: must change in actual implementation
//CHECKSTYLE.ON: TodoComment
                    // actuator endpoints are open to public, and
                    // even allow shutdown - so do not use this for production
                    .antMatchers("/actuator/**").permitAll()
                    .antMatchers("/admin/**").hasRole("ADMIN")
                    .antMatchers("/db/**").access("hasRole('ADMIN') and hasRole('DBA')")
                    .antMatchers("/test-api/**").denyAll()
                    .anyRequest().authenticated()
                    .and()
                  .csrf()
                    .ignoringAntMatchers("/login")
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .and()
//                .headers()
//                    .frameOptions()
//                    .disable() // uncomment if you want to make dev h2-console work
//                    .and()
                .formLogin()
                    .loginPage("/login")
                    .successHandler(formLoginStatusCodeSuccessHandler())
                    .failureHandler(statusCode401AuthenticationFailureHandler())
                    .permitAll()
                    .and()
//CHECKSTYLE.OFF: TodoComment - need this todo and still want builds to succeed
                    // TODO: should disable anonymous access in production
//CHECKSTYLE.ON: TodoComment
                    // keeping it here, since we want to show some
                    // unauthenticated thymeleaf views for debugging purposes
//                .anonymous()
//                    .disable()
                .logout()
                    .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
                    .permitAll();
        }


        @Override
        protected void configure(AuthenticationManagerBuilder builder) throws Exception {
            if (pam) {
                builder.authenticationProvider(new PamAuthenticationProvider());
            } else {
                super.configure(builder);
            }
        }
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

    /**
     * basic authentication for create-api-key
     * matching url /test-api/create-api-key/**
     */
    @Configuration
    @Order(1)
    public static class CreateApiKeyWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
        @Value("${proto.pam}")
        private boolean pam;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                .antMatcher("/test-api/create-api-key/**")
                .authorizeRequests()
                    .anyRequest().hasRole("XROAD-SYSTEM-ADMINISTRATOR")
                    .and()
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                .httpBasic()
                    .and()
                .anonymous()
                    .disable()
                .csrf()
                    .disable();

        }

        @Override
        protected void configure(AuthenticationManagerBuilder builder) throws Exception {
//CHECKSTYLE.OFF: TodoComment - need this todo and still want builds to succeed
            // TODO: remove non-pam authentication
//CHECKSTYLE.ON: TodoComment
            if (pam) {
                builder.authenticationProvider(new PamAuthenticationProvider());
            } else {
                super.configure(builder);
            }
        }
    }


    /**
     * custom token / session cookie authentication for rest apis
     * matching url /test-api/**
     */
    @Configuration
    @Order(2)
    public static class ApiWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
        private static final String PRINCIPAL_REQUEST_HEADER = "Authorization";

        @Autowired
        ApiKeyAuthenticationManager apiKeyAuthenticationManager;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            RequestHeaderAuthenticationFilter filter = new RequestHeaderAuthenticationFilter();
            filter.setPrincipalRequestHeader(PRINCIPAL_REQUEST_HEADER);
            filter.setAuthenticationManager(apiKeyAuthenticationManager);
            filter.setExceptionIfHeaderMissing(false); // exception at this point
            // would cause http 500, we want http 401
            http
                .antMatcher("/test-api/**")
                .addFilter(filter)
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.NEVER)
                    .and()
                .authorizeRequests()
                    .anyRequest().authenticated()
                    .and()
                .exceptionHandling()
                    .authenticationEntryPoint(new Http401AuthenticationEntryPoint())
                    .and()
                .csrf()
                    // we require csrf protection only if session cookie-authentication is used
                    .requireCsrfProtectionMatcher(request -> WebUtils.getCookie(request, "JSESSIONID") != null)
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .and()
                .anonymous()
                    .disable()
                .formLogin()
                    .disable();
        }
    }

    /**
     * Disable session request cache to prevent extra session creation when api
     * calls fail.
     * This breaks (access restricted thymeleaf page -> auth error -> login -> redirect to restricted page)
     * flow, but we do not need it.
     */
    @Bean
    RequestCache disableSessionRequestCache() {
        return new NullRequestCache();
    }
}
