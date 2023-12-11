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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.util.StringUtils;

/**
 * Use two csrf repositories:
 * {@link org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository HttpSessionCsrfTokenRepository}
 * and {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository CookieCsrfTokenRepository}.
 * This way we get the same token in session and csrf cookie
 */
@Slf4j
public class CookieAndSessionCsrfTokenRepository implements CsrfTokenRepository {
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    public static final String SESSION_CSRF_TOKEN_ATTR_NAME = CookieAndSessionCsrfTokenRepository.class.getName()
            .concat(".CSRF_TOKEN");

    private CookieCsrfTokenRepository cookieCsrfTokenRepository;
    private HttpSessionCsrfTokenRepository httpSessionCsrfTokenRepository;

    /**
     * Creates an instance of CookieAndSessionCsrfTokenRepository which holds instances of
     * {@link HttpSessionCsrfTokenRepository} and {@link CookieCsrfTokenRepository} with <code>cookieHttpOnly</code>
     * set to <code>false</code>. Also sets the CSRF header name to ensure it does not change in future Spring updates
     */
    public CookieAndSessionCsrfTokenRepository(final String sameSite) {
        cookieCsrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        cookieCsrfTokenRepository.setHeaderName(CSRF_HEADER_NAME);
        cookieCsrfTokenRepository.setCookieCustomizer(customizer -> customizer.sameSite(sameSite));
        httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        httpSessionCsrfTokenRepository.setSessionAttributeName(SESSION_CSRF_TOKEN_ATTR_NAME);
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return httpSessionCsrfTokenRepository.generateToken(request);
    }

    /**
     * Saves a new token in session and adds that token in to a response cookie
     * @param token
     * @param request
     * @param response
     */
    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        cookieCsrfTokenRepository.saveToken(token, request, response);
        httpSessionCsrfTokenRepository.saveToken(token, request, response);
    }

    /**
     * Validate and load the token if there is a session. If there is no session -> return null
     */
    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        // validate csrf only if a session exists
        if (session != null) {
            return validateAndLoadToken(request);
        }
        return null;
    }

    /**
     * Validate that the token exists in header, cookie and session and that they all match
     * @param request
     * @return {@link CsrfToken} if validation passes - otherwise {@code null}
     */
    private CsrfToken validateAndLoadToken(HttpServletRequest request) {
        String headerCsrfTokenValue = getHeaderCsrfTokenValue(request);
        if (headerCsrfTokenValue == null) {
            String headerCsrfError = "CSRF token not found in header";
            log.error(headerCsrfError);
            return null;
        }

        String cookieCsrfTokenValue = getCookieCsrfTokenValue(request);
        if (cookieCsrfTokenValue == null) {
            String cookieCsrfError = "CSRF token not found in request cookie";
            log.error(cookieCsrfError);
            return null;
        }

        String sessionCsrfTokenValue = getSessionCsrfTokenValue(request);
        if (sessionCsrfTokenValue == null) {
            String sessionCsrfError = "CSRF token not found in session";
            log.error(sessionCsrfError);
            return null;
        }

        if (!sessionCsrfTokenValue.equals(headerCsrfTokenValue)) {
            String headerCsrfComparisonError = "Header CSRF value does not match with session";
            log.error(headerCsrfComparisonError);
            return null;
        }

        if (!sessionCsrfTokenValue.equals(cookieCsrfTokenValue)) {
            String cookieCsrfComparisonError = "Cookie CSRF value does not match with session";
            log.error(cookieCsrfComparisonError);
            return null;
        }
        return cookieCsrfTokenRepository.loadToken(request);
    }

    private String getSessionCsrfTokenValue(HttpServletRequest servletRequest) {
        CsrfToken csrfToken = httpSessionCsrfTokenRepository.loadToken(servletRequest);
        if (csrfToken == null || StringUtils.isEmpty(csrfToken.getToken())) {
            return null;
        }
        return csrfToken.getToken();
    }

    private String getCookieCsrfTokenValue(HttpServletRequest servletRequest) {
        CsrfToken csrfToken = cookieCsrfTokenRepository.loadToken(servletRequest);
        if (csrfToken == null || StringUtils.isEmpty(csrfToken.getToken())) {
            return null;
        }
        return csrfToken.getToken();
    }

    private String getHeaderCsrfTokenValue(HttpServletRequest servletRequest) {
        return servletRequest.getHeader(CSRF_HEADER_NAME);
    }
}
