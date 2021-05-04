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
package org.niis.xroad.restapi.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Utility for extracting currently logged in user's username from security context
 */
@Component
@Slf4j
public class UsernameHelper {

    public static final String UNKNOWN_USERNAME = null;

    /**
     * String that represents value for unknown username
     * @return
     */
    public String getUnknownUsername() {
        return UNKNOWN_USERNAME;
    }

    /**
     * Returns optional that holds currently logged in user's username, if it could be determined.
     * Exceptions are logged, not thrown, and empty optional is returned if they happen.
     * Other Throwables are thrown.
     */
    public Optional<String> getOptionalUsername() {
        String username = null;
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof String) {
                    username = (String) principal;
                }
            }
        } catch (Exception e) {
            log.error("exception while determining username", e);
        }
        if (username == null) {
            return Optional.empty();
        } else {
            return Optional.of(username);
        }
    }

    /**
     * Returns String with currently logged in user's username, or UNKNOWN_USERNAME if username could not be determined.
     * Any Exceptions are caught and UNKNOWN_USERNAME is returned.
     * Throwables are thrown.
     */
    public String getUsername() {
        return getOptionalUsername().orElse(UNKNOWN_USERNAME);
    }
}
