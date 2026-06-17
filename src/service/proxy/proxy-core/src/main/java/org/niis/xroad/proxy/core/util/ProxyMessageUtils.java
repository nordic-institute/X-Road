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
package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.HttpSender;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;

/**
 * Static utility methods shared across proxy message processors and handlers.
 */
@Slf4j
public final class ProxyMessageUtils {

    private ProxyMessageUtils() {
    }

    /**
     * Extracts the hash algorithm ID from the response headers of an HttpSender.
     *
     * @param httpSender the HTTP sender whose response headers are inspected
     * @return the digest algorithm identified by the response header
     */
    public static DigestAlgorithm getHashAlgoId(HttpSender httpSender) {
        return DigestAlgorithm.ofName(httpSender.getResponseHeaders().get(HEADER_HASH_ALGO_ID));
    }

    public static long logPerformanceBegin(Request request) {
        String remoteAddr = org.eclipse.jetty.server.Request.getRemoteAddr(request);
        log.info("Received request from {}", remoteAddr);
        return PerformanceLogger.log(log, "Received request from " + remoteAddr);
    }

    public static void logPerformanceEnd(long start) {
        PerformanceLogger.log(log, start, "Request handled");
    }

    /**
     * Token used to pin HTTP client connection pool entries to a set of target hosts.
     * Equality is based on the set of target host URIs, enabling connection pool reuse
     * per security server group.
     */
    @EqualsAndHashCode
    public static final class TargetHostsUserToken {
        private final Set<URI> targetHosts;

        public TargetHostsUserToken(URI[] uris) {
            if (uris == null || uris.length == 0) {
                this.targetHosts = Collections.emptySet();
            } else {
                if (uris.length == 1) {
                    this.targetHosts = Collections.singleton(uris[0]);
                } else {
                    this.targetHosts = new HashSet<>(java.util.Arrays.asList(uris));
                }
            }
        }
    }
}
