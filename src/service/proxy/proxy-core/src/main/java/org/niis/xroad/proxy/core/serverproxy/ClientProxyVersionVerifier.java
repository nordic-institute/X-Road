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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.semver4j.Semver;

import java.util.Optional;

import static org.eclipse.jetty.server.Request.getRemoteAddr;
import static org.niis.xroad.common.core.exception.ErrorCode.CLIENT_PROXY_VERSION_NOT_SUPPORTED;

@Slf4j
public final class ClientProxyVersionVerifier {
    private static final String UNKNOWN_VERSION = "unknown";

    private final Semver minSupportedClientVersion;

    public ClientProxyVersionVerifier(String minSupportedClientVersion) {
        this.minSupportedClientVersion = Optional.ofNullable(minSupportedClientVersion)
                .map(Semver::new)
                .orElse(null);
    }

    public void check(Request request) {
        String clientVersion = getVersion(request.getHeaders().get(MimeUtils.HEADER_PROXY_VERSION));

        log.info("Received request from {} (security server version: {})", getRemoteAddr(request), clientVersion);
        if (minSupportedClientVersion != null && minSupportedClientVersion.isGreaterThan(new Semver(clientVersion))) {
            throw XrdRuntimeException.systemException(CLIENT_PROXY_VERSION_NOT_SUPPORTED,
                    "The minimum supported version for client security server is: %s ".formatted(minSupportedClientVersion.toString()));
        }

        String thisVersion = getVersion(Version.XROAD_VERSION);
        if (!clientVersion.equals(thisVersion)) {
            log.warn("Peer security server version ({}) does not match host security server version ({})", clientVersion, thisVersion);
        }
    }

    private String getVersion(String value) {
        return !StringUtils.isBlank(value) ? value : UNKNOWN_VERSION;
    }
}
