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
package org.niis.xroad.ds.identity;

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.IDN;
import java.util.Locale;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * The canonical form of a DID host and port, the only implementation of the host rules: lowercase
 * host, an international host in its ASCII form, an IPv6 literal in brackets, and no explicit port
 * when it is the HTTPS default.
 */
@UtilityClass
public class DidAuthority {

    /** The port a {@code did:web} authority omits. */
    public static final int DEFAULT_HTTPS_PORT = 443;

    private static final int NON_ASCII_START = 0x80;

    /**
     * Checks that a registered address, as stored and before any conversion, contains only ASCII characters.
     *
     * @param address the registered address, without a port
     * @throws XrdRuntimeException if the address contains a non-ASCII character
     */
    public static void requireAscii(String address) {
        if (!address.chars().allMatch(c -> c < NON_ASCII_START)) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "address '%s' must contain only ASCII characters", address);
        }
    }

    /**
     * Canonical form of a host: lowercase, an international name converted to its ASCII ({@code xn--})
     * form, a bare IPv6 literal bracketed.
     *
     * @param host a hostname, an IPv4 literal, or an IPv6 literal with or without brackets; never {@code host:port}
     * @return the canonical host
     * @throws XrdRuntimeException if the host is blank, or is a name that cannot be converted to ASCII; an IPv6
     *         literal is not validated
     */
    public static String canonicalHost(String host) {
        if (host == null || host.isBlank()) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "host must not be blank");
        }
        var lowerCase = host.toLowerCase(Locale.ROOT);
        if (lowerCase.indexOf(':') >= 0) {
            return lowerCase.startsWith("[") ? lowerCase : "[" + lowerCase + "]";
        }
        try {
            return IDN.toASCII(lowerCase);
        } catch (IllegalArgumentException e) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "host '%s' is not a valid name: %s",
                    host, e.getMessage());
        }
    }

    /**
     * Canonical form of a host and port: {@link #canonicalHost(String)}, followed by {@code :port}
     * unless the port is {@value #DEFAULT_HTTPS_PORT}.
     *
     * @param host a hostname, an IPv4 literal, or an IPv6 literal with or without brackets
     * @param port the port
     * @return the canonical authority, e.g. {@code ss0.example.org:7183}
     * @throws XrdRuntimeException as {@link #canonicalHost(String)}
     */
    public static String canonical(String host, int port) {
        var canonicalHost = canonicalHost(host);
        return port == DEFAULT_HTTPS_PORT ? canonicalHost : canonicalHost + ":" + port;
    }
}
