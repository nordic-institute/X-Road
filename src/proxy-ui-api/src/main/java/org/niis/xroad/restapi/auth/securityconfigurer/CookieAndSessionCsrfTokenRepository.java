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

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Implements (and copies) functionalities from two csrf repositories:
 * {@link org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository HttpSessionCsrfTokenRepository}
 * and {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository CookieCsrfTokenRepository}.
 * This way we get the same token in session and csrf cookie
 */
public class CookieAndSessionCsrfTokenRepository implements CsrfTokenRepository {
    public static final String DEFAULT_CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String DEFAULT_CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    public static final String DEFAULT_CSRF_PARAMETER_NAME = "_csrf";

    private static final String DEFAULT_CSRF_TOKEN_ATTR_NAME = CookieAndSessionCsrfTokenRepository.class
            .getName().concat(".CSRF_TOKEN");

    private final Method setHttpOnlyMethod;

    private boolean cookieHttpOnly;
    private String cookieName = DEFAULT_CSRF_COOKIE_NAME;
    private String headerName = DEFAULT_CSRF_HEADER_NAME;
    private String parameterName = DEFAULT_CSRF_PARAMETER_NAME;
    private String sessionAttributeName = DEFAULT_CSRF_TOKEN_ATTR_NAME;

    public CookieAndSessionCsrfTokenRepository() {
        this.setHttpOnlyMethod = ReflectionUtils.findMethod(Cookie.class, "setHttpOnly", boolean.class);
        if (this.setHttpOnlyMethod != null) {
            this.cookieHttpOnly = true;
        }
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(this.headerName, this.parameterName,
                createNewToken());
    }

    /**
     * Saves a new token in session and adds that token in to a response cookie
     * @param token token to be saved. The value of the token should not be empty or null
     * @param request
     * @param response
     */
    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request,
            HttpServletResponse response) {
        String tokenValue = token == null ? "" : token.getToken();
        if (!StringUtils.isEmpty(tokenValue)) {
            Cookie cookie = new Cookie(this.cookieName, tokenValue);
            cookie.setSecure(request.isSecure());
            cookie.setPath(this.getRequestContext(request));
            if (token == null) {
                cookie.setMaxAge(0);
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.removeAttribute(this.sessionAttributeName);
                }
            } else {
                cookie.setMaxAge(-1);
                HttpSession session = request.getSession();
                session.setAttribute(this.sessionAttributeName, token);
            }
            if (cookieHttpOnly && setHttpOnlyMethod != null) {
                ReflectionUtils.invokeMethod(setHttpOnlyMethod, cookie, Boolean.TRUE);
            }
            response.addCookie(cookie);
        }
    }

    /**
     * The de facto token that gets loaded from the session
     */
    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (CsrfToken) session.getAttribute(this.sessionAttributeName);
        }
        return null;
    }

    private String getRequestContext(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return contextPath.length() > 0 ? contextPath : "/";
    }

    /**
     * Factory method to conveniently create an instance that has
     * {@link #setCookieHttpOnly(boolean)} set to false.
     * @return an instance of CookieCsrfTokenRepository with
     * {@link #setCookieHttpOnly(boolean)} set to false
     */
    public static CookieAndSessionCsrfTokenRepository withHttpOnlyFalse() {
        CookieAndSessionCsrfTokenRepository result = new CookieAndSessionCsrfTokenRepository();
        result.setCookieHttpOnly(false);
        return result;
    }

    private String createNewToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * @see org.springframework.security.web.csrf.CookieCsrfTokenRepository#setCookieHttpOnly(boolean)
     * CookieCsrfTokenRepository#setCookieHttpOnly(boolean)
     */
    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        if (cookieHttpOnly && setHttpOnlyMethod == null) {
            throw new IllegalArgumentException(
                    "Cookie will not be marked as HttpOnly because you are using a version of Servlet " +
                            "less than 3.0. NOTE: The Cookie#setHttpOnly(boolean) was introduced in Servlet 3.0.");
        }
        this.cookieHttpOnly = cookieHttpOnly;
    }
}
