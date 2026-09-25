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
package org.niis.xroad.common.agreementtoken;

import org.niis.xroad.serverconf.PathGlob;
import org.niis.xroad.serverconf.model.BaseEndpoint;

/**
 * One allowed HTTP method and path pattern, using the exact same glob syntax and sentinel values
 * ({@link BaseEndpoint#ANY_METHOD}, {@link BaseEndpoint#ANY_PATH}) as the security server's ACL endpoints, so a
 * token restates an ACL grant without a second, drifting notion of "matches". The path must compile as a
 * glob; checking it here means a token carrying an uncompilable pattern is rejected as malformed when its
 * claims are decoded, instead of throwing out of the match.
 */
public record AgreementTokenScope(String method, String path) {

    public AgreementTokenScope {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        try {
            PathGlob.compile(path);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("path is not a valid path glob: " + path, e);
        }
    }

    /**
     * @return true if {@code requestMethod}/{@code requestPath} fall within this scope entry, using the same
     *         matching rules as {@link BaseEndpoint#matches(String, String)}
     */
    public boolean matches(String requestMethod, String requestPath) {
        return (BaseEndpoint.ANY_METHOD.equals(method) || method.equalsIgnoreCase(requestMethod))
                && (BaseEndpoint.ANY_PATH.equals(path) || PathGlob.matches(path, requestPath));
    }
}
