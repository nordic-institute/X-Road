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
package org.niis.xroad.restapi.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * Helper for working with security and authorization
 */
@Slf4j
@Component
public class SecurityHelper {

    private final RequestHelper requestHelper;

    @Autowired
    public SecurityHelper(RequestHelper requestHelper) {
        this.requestHelper = requestHelper;
    }

    /**
     * Tells if current user / authentication has been granted given authority
     * @param authority
     * @return
     */
    public boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    /**
     * Verifies that current user / authentication has been granted given authority.
     * If not, throws {@link AccessDeniedException}
     * @param authority
     * @throws AccessDeniedException if given authority has not been granted
     */
    public void verifyAuthority(String authority) throws AccessDeniedException {
        if (!hasAuthority(authority)) {
            throw new AccessDeniedException("Missing authority: " + authority);
        }
    }

    /**
     * Returns current authentication scheme as a string. Possible values:
     * - ApiKey
     * - Session
     * - HttpBasicPam
     */
    public String getCurrentAuthenticationScheme() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication authentication = securityContext.getAuthentication();
        if (authentication == null) {
            return null;
        } else if (authentication instanceof PreAuthenticatedAuthenticationToken) {
            return "ApiKey";
        } else if (authentication instanceof UsernamePasswordAuthenticationToken) {
            if (hasSecurityContextInSession()) {
                return "Session";
            } else {
                return "HttpBasicPam";
            }
        } else {
            return authentication.getClass().getSimpleName();
        }
    }

    private boolean hasSecurityContextInSession() {
        HttpServletRequest request = requestHelper.getCurrentHttpRequest();
        boolean hasSessionContext = false;
        if (request != null) {
            hasSessionContext = new HttpSessionSecurityContextRepository().containsContext(request);
        }
        return hasSessionContext;
    }
}
