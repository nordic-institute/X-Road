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
package org.niis.xroad.restapi.auth.securityconfigurer;

import org.niis.xroad.restapi.auth.ApiKeyAuthenticationManager;
import org.niis.xroad.restapi.auth.Http401AuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.csrf.LazyCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import javax.servlet.http.HttpServletRequest;

/**
 * custom token / session cookie authentication for rest apis
 * matching url /api/**
 */
@Configuration
@Order(MultiAuthWebSecurityConfig.API_SECURITY_ORDER)
public class ApiWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
    private static final String PRINCIPAL_REQUEST_HEADER = "Authorization";

    @Autowired
    ApiKeyAuthenticationManager apiKeyAuthenticationManager;

    @Autowired
    private Http401AuthenticationEntryPoint http401AuthenticationEntryPoint;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        RequestHeaderAuthenticationFilter filter = new RequestHeaderAuthenticationFilter();
        filter.setPrincipalRequestHeader(PRINCIPAL_REQUEST_HEADER);
        filter.setAuthenticationManager(apiKeyAuthenticationManager);
        filter.setExceptionIfHeaderMissing(false); // exception at this point
        // would cause http 500, we want http 401
        http
            .antMatcher("/api/**")
            .addFilter(filter)
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.NEVER)
                .and()
            .authorizeRequests()
                .anyRequest().authenticated()
                .and()
            .exceptionHandling()
                .authenticationEntryPoint(http401AuthenticationEntryPoint)
                .and()
            .csrf()
                // we require csrf protection only if there is a session alive
                .requireCsrfProtectionMatcher(ApiWebSecurityConfigurerAdapter::sessionExists)
                // CsrfFilter always generates a new token in the repo -> prevent with lazy
                .csrfTokenRepository(new LazyCsrfTokenRepository(new CookieAndSessionCsrfTokenRepository()))
                .and()
            .anonymous()
                .disable()
            .headers()
                .contentSecurityPolicy("default-src 'none'")
                .and()
                .and()
            .formLogin()
                .disable();
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/api/v1/openapi.yaml");
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
