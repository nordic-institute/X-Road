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
import java.net.Inet6Address;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.OptionalInt;

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
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int MAX_PORT_DIGITS = 5;

    /**
     * @param text the text to check
     * @return whether the text contains only ASCII characters
     */
    public static boolean isAscii(String text) {
        return text.chars().allMatch(c -> c < NON_ASCII_START);
    }

    /**
     * Checks that a registered address, as stored and before any conversion, contains only ASCII characters.
     *
     * @param address the registered address, without a port
     * @throws XrdRuntimeException if the address contains a non-ASCII character
     */
    public static void requireAscii(String address) {
        if (!isAscii(address)) {
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
     * @throws XrdRuntimeException if the host is blank, is a name that cannot be converted to ASCII, has a
     *         character other than a letter, digit, {@code -} or {@code .} after conversion, is not a host that
     *         {@link URI#getHost()} reads from an HTTPS URL, or contains a colon but is not a valid IPv6 literal
     */
    public static String canonicalHost(String host) {
        if (host == null || host.isBlank()) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "host must not be blank");
        }
        var lowerCase = host.toLowerCase(Locale.ROOT);
        if (lowerCase.indexOf(':') >= 0) {
            return canonicalIpv6(host, lowerCase);
        }
        String ascii;
        try {
            ascii = IDN.toASCII(lowerCase);
        } catch (IllegalArgumentException e) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "host '%s' is not a valid name: %s",
                    host, e.getMessage());
        }
        if (!ascii.chars().allMatch(DidAuthority::isHostChar)) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "host '%s' is not a valid name: it has a character that a host name cannot contain", host);
        }
        if (!isUriHost(ascii)) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "host '%s' is not a valid name: it is not a host that an HTTPS URL can carry", host);
        }
        return ascii;
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

    /**
     * An authority split into its host and optional port.
     *
     * @param host the host as written, case kept, without the brackets of an IPv6 literal
     * @param port the explicit port, empty when the authority has none
     */
    public record Parsed(String host, OptionalInt port) {
    }

    /**
     * Splits an authority, as the DID decoder returns it, into a host and an optional port. The forms are
     * {@code host}, {@code host:port}, {@code [v6]} and {@code [v6]:port}; an IPv6 literal without brackets
     * is taken as a host with no port. The host keeps its case and is not converted.
     *
     * @param authority the authority to split
     * @return the host and the explicit port, if any
     * @throws XrdRuntimeException if the host is empty, not ASCII or not valid as in
     *         {@link #canonicalHost(String)}, the brackets are malformed, or the port is not a number from
     *         {@value #MIN_PORT} to {@value #MAX_PORT}
     */
    public static Parsed parse(String authority) {
        if (authority == null || authority.isEmpty()) {
            throw emptyHost(authority);
        }
        String host;
        String portText = null;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) {
                throw malformedBrackets(authority, "has no closing bracket");
            }
            host = authority.substring(1, close);
            var rest = authority.substring(close + 1);
            if (!rest.isEmpty()) {
                if (rest.charAt(0) != ':') {
                    throw malformedBrackets(authority, "has text after the closing bracket that is not a port");
                }
                portText = rest.substring(1);
            }
        } else if (authority.indexOf(':') != authority.lastIndexOf(':')) {
            host = authority;
        } else {
            int colon = authority.indexOf(':');
            host = colon < 0 ? authority : authority.substring(0, colon);
            portText = colon < 0 ? null : authority.substring(colon + 1);
        }
        if (hasBracket(host) || (portText != null && hasBracket(portText))) {
            throw malformedBrackets(authority, "has a bracket that does not enclose the whole host");
        }
        if (host.isEmpty()) {
            throw emptyHost(authority);
        }
        requireAscii(host);
        if (authority.startsWith("[") && !isIpv6Literal(host)) {
            throw malformedBrackets(authority, "does not enclose an IPv6 literal");
        }
        canonicalHost(host);
        return new Parsed(host, portText == null ? OptionalInt.empty() : OptionalInt.of(parsePort(authority, portText)));
    }

    /**
     * Whether an authority is already in canonical form: it equals {@link #canonical(String, int)} of its own
     * parsed host and port, with a missing port read as {@value #DEFAULT_HTTPS_PORT}. An authority that cannot
     * be parsed is not canonical.
     *
     * @param authority the authority to check, e.g. as decoded from a DID
     * @return {@code true} if the authority needs no conversion
     */
    public static boolean isCanonical(String authority) {
        try {
            var parsed = parse(authority);
            return authority.equals(canonical(parsed.host(), parsed.port().orElse(DEFAULT_HTTPS_PORT)));
        } catch (XrdRuntimeException e) {
            return false;
        }
    }

    private static String canonicalIpv6(String host, String lowerCase) {
        var bracketed = lowerCase.startsWith("[") && lowerCase.endsWith("]");
        var literal = bracketed ? lowerCase.substring(1, lowerCase.length() - 1) : lowerCase;
        if (!isIpv6Literal(literal)) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR, "host '%s' is not a valid IPv6 literal", host);
        }
        return "[" + literal + "]";
    }

    private static boolean isIpv6Literal(String literal) {
        if (literal.indexOf('%') >= 0 || hasBracket(literal)) {
            return false;
        }
        try {
            Inet6Address.ofLiteral(literal);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Whether {@link URI#getHost()} reads a host from an HTTPS URL that carries the given ASCII text as its
     * authority. This is the single rule for what an HTTPS URL can carry as a host: it rejects, for example,
     * an IPv6 literal without brackets.
     *
     * @param authority an ASCII host, or host and port
     * @return {@code true} if the authority has a server-based host
     */
    public static boolean isUriHost(String authority) {
        try {
            return new URI("https://" + authority).getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isHostChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.';
    }

    private static boolean hasBracket(String text) {
        return text.indexOf('[') >= 0 || text.indexOf(']') >= 0;
    }

    private static int parsePort(String authority, String portText) {
        boolean digits = !portText.isEmpty() && portText.length() <= MAX_PORT_DIGITS
                && portText.chars().allMatch(c -> c >= '0' && c <= '9');
        int port = digits ? Integer.parseInt(portText) : -1;
        if (port < MIN_PORT || port > MAX_PORT) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "authority '%s' has port '%s' outside %d to %d", authority, portText, MIN_PORT, MAX_PORT);
        }
        return port;
    }

    private static XrdRuntimeException emptyHost(String authority) {
        return XrdRuntimeException.systemException(VALIDATION_ERROR, "authority '%s' has an empty host", authority);
    }

    private static XrdRuntimeException malformedBrackets(String authority, String problem) {
        return XrdRuntimeException.systemException(VALIDATION_ERROR, "authority '%s' has malformed brackets: it %s",
                authority, problem);
    }
}
