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

package org.niis.xroad.configuration.migration;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns the X-Road 7.8 {@code [op-monitor] host} value into X-Road 8 rows: the op-monitor channel host, the
 * listener address and the local gRPC certificate subject alternative names.
 * <p>
 * Result keys use the legacy shape, WITHOUT the downstream {@code "xroad."} prefix.
 */
final class OpMonitorHostRule {

    static final String CHANNEL_HOST = "common-rpc.channel.op-monitor.host";
    static final String LISTEN_ADDRESS = "op-monitor.rpc.listen-address";
    static final String ALT_NAMES = "common-rpc.certificate-provisioning.alt-names";
    static final String IP_SUBJECT_ALT_NAMES = "common-rpc.certificate-provisioning.ip-subject-alt-names";

    private static final String LOOPBACK_IPV4 = "127.0.0.1";
    private static final String LOCAL_FIX_COMMAND =
            "/usr/share/xroad/scripts/db_property.sh set xroad.common-rpc.channel.op-monitor.host 127.0.0.1 --yes";
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final LocalAddressResolver resolver;

    OpMonitorHostRule(LocalAddressResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * @param host effective 7.8 host value, {@code null} or blank when absent; an IPv6 address in brackets is used
     *             without the brackets
     */
    Result derive(String host) {
        if (host == null || host.isBlank()) {
            return new Result(Map.of(), null, false);
        }
        var value = withoutIpv6Brackets(host.trim());
        try {
            if (isIpLiteral(value)) {
                return deriveFromAddress(value, InetAddress.getByName(value));
            }
            return deriveFromName(value);
        } catch (UnknownHostException _) {
            return ambiguous(value, "cannot be resolved");
        }
    }

    private Result deriveFromAddress(String value, InetAddress address) {
        var rows = new LinkedHashMap<String, String>();
        if (address.isAnyLocalAddress()) {
            rows.put(LISTEN_ADDRESS, value);
            return new Result(rows, null, false);
        }
        rows.put(CHANNEL_HOST, value);
        if (LOOPBACK_IPV4.equals(address.getHostAddress())) {
            return new Result(rows, null, false);
        }
        if (address.isLoopbackAddress() || resolver.isLocalAddress(address)) {
            rows.put(LISTEN_ADDRESS, value);
            rows.put(IP_SUBJECT_ALT_NAMES, String.join(",", LOOPBACK_IPV4, value));
            return new Result(rows, null, false);
        }
        return new Result(rows, null, true);
    }

    private Result deriveFromName(String value) throws UnknownHostException {
        var addresses = resolver.resolve(value);
        var hasOwn = addresses.stream().anyMatch(a -> !a.isLoopbackAddress() && resolver.isLocalAddress(a));
        var hasLoopback = addresses.stream().anyMatch(InetAddress::isLoopbackAddress);
        var hasRemote = addresses.stream().anyMatch(this::isRemote);
        if (hasRemote && hasOwn) {
            return ambiguous(value, "resolves to this server and to another host");
        }
        if (hasRemote && hasLoopback) {
            return ambiguous(value, "resolves to a loopback address and to another host");
        }
        var rows = new LinkedHashMap<String, String>();
        rows.put(CHANNEL_HOST, value);
        if (hasOwn) {
            rows.put(LISTEN_ADDRESS, value);
            rows.put(ALT_NAMES, value);
        } else if (hasLoopback) {
            if (addresses.stream().allMatch(InetAddress::isLoopbackAddress)
                    && addresses.stream().noneMatch(a -> LOOPBACK_IPV4.equals(a.getHostAddress()))) {
                rows.put(LISTEN_ADDRESS, value);
            }
            rows.put(ALT_NAMES, value);
        }
        return new Result(rows, null, hasRemote);
    }

    private boolean isRemote(InetAddress address) {
        return !address.isLoopbackAddress() && !address.isAnyLocalAddress() && !resolver.isLocalAddress(address);
    }

    private static Result ambiguous(String value, String reason) {
        var warning = ("WARNING: op-monitor host '%1$s' %2$s. %3$s was kept as '%1$s'; no listener or certificate "
                + "name rows were written. For a local op-monitor run: %4$s, then restart xroad-proxy and "
                + "xroad-proxy-ui-api. For an external op-monitor no change is needed here.")
                .formatted(value, reason, "xroad." + CHANNEL_HOST, LOCAL_FIX_COMMAND);
        return new Result(Map.of(CHANNEL_HOST, value), warning, false);
    }

    private static String withoutIpv6Brackets(String value) {
        if (value.startsWith("[") && value.endsWith("]") && value.indexOf(':') >= 0) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isIpLiteral(String value) {
        return value.indexOf(':') >= 0 || IPV4_LITERAL.matcher(value).matches();
    }

    /**
     * @param warning non-null for an ambiguous host: the full operator-facing text, starting with {@code WARNING:}
     * @param remote  the host resolves only to addresses of other hosts
     */
    record Result(Map<String, String> rows, String warning, boolean remote) {
    }

    interface LocalAddressResolver {

        /** Resolves a host name to all of its addresses. */
        List<InetAddress> resolve(String name) throws UnknownHostException;

        /** Tells whether the address is assigned to a network interface of this host. */
        boolean isLocalAddress(InetAddress address);

        static LocalAddressResolver system() {
            return new LocalAddressResolver() {
                @Override
                public List<InetAddress> resolve(String name) throws UnknownHostException {
                    return List.of(InetAddress.getAllByName(name));
                }

                @Override
                public boolean isLocalAddress(InetAddress address) {
                    try {
                        return NetworkInterface.getByInetAddress(address) != null;
                    } catch (SocketException _) {
                        return false;
                    }
                }
            };
        }
    }
}
