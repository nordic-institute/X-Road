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

package org.niis.xroad.common.properties;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The TLS certificate identity of this host: the common name, the DNS names and the IP addresses
 * a client may use to reach it.
 *
 * @param commonName  certificate common name
 * @param dnsNames    DNS subject alternative names
 * @param ipAddresses IP subject alternative names (global-scope addresses only)
 */
public record HostIdentity(String commonName, List<String> dnsNames, List<String> ipAddresses) {

    /** The longest common name a certificate may carry (RFC 5280 upper bound). */
    static final int MAX_COMMON_NAME_LENGTH = 64;

    private static final String FALLBACK_HOST_NAME = "localhost";

    /**
     * Derives the identity from host facts.
     *
     * @param fullyQualifiedName fully qualified host name
     * @param shortName          short host name
     * @param addresses          addresses of the host's network interfaces
     * @return the common name is the fully qualified name unless it is longer than
     *         {@value #MAX_COMMON_NAME_LENGTH} characters, then the short name; the DNS names are
     *         the fully qualified and the short name; loopback, link-local, wildcard and multicast
     *         addresses are left out of the IP names
     */
    public static HostIdentity of(String fullyQualifiedName, String shortName, Collection<InetAddress> addresses) {
        var commonName = fullyQualifiedName.length() > MAX_COMMON_NAME_LENGTH ? shortName : fullyQualifiedName;
        var dnsNames = Stream.of(fullyQualifiedName, shortName).distinct().toList();
        var ipAddresses = addresses.stream()
                .filter(HostIdentity::isGlobalScope)
                .map(HostIdentity::literal)
                .distinct()
                .toList();
        return new HostIdentity(commonName, dnsNames, ipAddresses);
    }

    /**
     * Detects the identity of the machine this process runs on. Detected on first use, once per process.
     *
     * @return the local host identity; the host name {@code localhost} and no IP names when the host cannot be inspected
     */
    public static HostIdentity local() {
        return Local.IDENTITY;
    }

    static HostIdentity detectOrFallback(Supplier<HostIdentity> detector) {
        try {
            return detector.get();
        } catch (RuntimeException _) {
            return of(FALLBACK_HOST_NAME, FALLBACK_HOST_NAME, List.of());
        }
    }

    private static boolean isGlobalScope(InetAddress address) {
        return !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isAnyLocalAddress()
                && !address.isMulticastAddress();
    }

    private static String literal(InetAddress address) {
        var text = address.getHostAddress();
        return address instanceof Inet6Address ? text.split("%", 2)[0] : text;
    }

    private static final class Local {
        static final HostIdentity IDENTITY = detectOrFallback(Local::detect);

        private static HostIdentity detect() {
            var host = localHost();
            var shortName = firstLabel(host == null ? FALLBACK_HOST_NAME : host.getHostName());
            var fullyQualifiedName = host == null ? shortName : canonicalName(host, shortName);
            return of(fullyQualifiedName, shortName, interfaceAddresses());
        }

        private static InetAddress localHost() {
            try {
                return InetAddress.getLocalHost();
            } catch (UnknownHostException _) {
                return null;
            }
        }

        private static String canonicalName(InetAddress host, String fallback) {
            var canonical = host.getCanonicalHostName();
            return canonical == null || canonical.isBlank() || Objects.equals(canonical, host.getHostAddress())
                    ? fallback
                    : canonical;
        }

        private static String firstLabel(String hostName) {
            var dot = hostName.indexOf('.');
            return dot > 0 ? hostName.substring(0, dot) : hostName;
        }

        private static List<InetAddress> interfaceAddresses() {
            try {
                var addresses = new ArrayList<InetAddress>();
                for (var networkInterface : NetworkInterface.networkInterfaces().toList()) {
                    if (networkInterface.isUp()) {
                        networkInterface.inetAddresses().forEach(addresses::add);
                    }
                }
                return addresses;
            } catch (SocketException _) {
                return List.of();
            }
        }
    }
}
