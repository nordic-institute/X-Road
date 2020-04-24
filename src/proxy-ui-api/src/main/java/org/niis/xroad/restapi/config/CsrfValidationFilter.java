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
package org.niis.xroad.restapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Filter which adds some additional csrf token validation. The default CsrfFilter does not match the token with
 * the session's csrf
 */
@Configuration
@Order(TraceWebServletAutoConfiguration.TRACING_FILTER_ORDER + 2)
@Slf4j
public class CsrfValidationFilter extends OncePerRequestFilter {
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    private HttpSessionCsrfTokenRepository httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
    private CookieCsrfTokenRepository cookieCsrfTokenRepository = new CookieCsrfTokenRepository();
    private AccessDeniedHandler accessDeniedHandler = new AccessDeniedHandlerImpl();

    // TODO: SUPPORT ONLY FORM LOGIN AUTH
    @Override
    protected void doFilterInternal(HttpServletRequest servletRequest,
            HttpServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = servletRequest.getSession(false);
        // if there is no session -> move along
        if (session == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String headerCsrfTokenValue = getHeaderCsrfTokenValue(servletRequest);
        if (headerCsrfTokenValue == null) {
            String headerCsrfError = "CSRF token not found in header";
            log.error(headerCsrfError);
            this.accessDeniedHandler.handle(servletRequest, servletResponse,
                    new CsrfException(headerCsrfError));
            return;
        }

        String cookieCsrfTokenValue = getCookieCsrfTokenValue(servletRequest);
        if (cookieCsrfTokenValue == null) {
            String cookieCsrfError = "CSRF token not found in request cookie";
            log.error(cookieCsrfError);
            this.accessDeniedHandler.handle(servletRequest, servletResponse,
                    new CsrfException(cookieCsrfError));
            return;
        }

        String sessionCsrfTokenValue = getSessionCsrfTokenValue(servletRequest);
        if (sessionCsrfTokenValue == null) {
            String sessionCsrfError = "CSRF token not found in session";
            log.error(sessionCsrfError);
            this.accessDeniedHandler.handle(servletRequest, servletResponse,
                    new CsrfException(sessionCsrfError));
            return;
        }

        if (!sessionCsrfTokenValue.equals(headerCsrfTokenValue)) {
            String headerCsrfComparisonError = "Header CSRF value does not match with session";
            log.error(headerCsrfComparisonError);
            this.accessDeniedHandler.handle(servletRequest, servletResponse,
                    new CsrfException(headerCsrfComparisonError));
            return;
        }

        if (!sessionCsrfTokenValue.equals(cookieCsrfTokenValue)) {
            String cookieCsrfComparisonError = "Cookie CSRF value does not match with session";
            log.error(cookieCsrfComparisonError);
            this.accessDeniedHandler.handle(servletRequest, servletResponse,
                    new CsrfException(cookieCsrfComparisonError));
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String getSessionCsrfTokenValue(HttpServletRequest servletRequest) {
        CsrfToken sessionCsrfToken = httpSessionCsrfTokenRepository.loadToken(servletRequest);
        if (sessionCsrfToken == null || StringUtils.isEmpty(sessionCsrfToken.getToken())) {
            return null;
        }
        return sessionCsrfToken.getToken();
    }

    private String getCookieCsrfTokenValue(HttpServletRequest servletRequest) {
        CsrfToken cookieCsrfToken = cookieCsrfTokenRepository.loadToken(servletRequest);
        if (cookieCsrfToken == null || StringUtils.isEmpty(cookieCsrfToken.getToken())) {
            return null;
        }
        return cookieCsrfToken.getToken();
    }

    private String getHeaderCsrfTokenValue(HttpServletRequest servletRequest) {
        return servletRequest.getHeader(CSRF_HEADER_NAME);
    }
}
