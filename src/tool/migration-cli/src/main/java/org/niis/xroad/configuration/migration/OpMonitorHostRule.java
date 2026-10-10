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

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Turns the X-Road 7.8 {@code [op-monitor] host} value into X-Road 8 rows: the op-monitor channel host, the
 * listener address and the local gRPC certificate subject alternative names.
 * <p>
 * Result keys use the legacy shape, WITHOUT the downstream {@code "xroad."} prefix.
 */
@Slf4j
final class OpMonitorHostRule {

    static final String CHANNEL_HOST = "common-rpc.channel.op-monitor.host";
    static final String LISTEN_ADDRESS = "op-monitor.rpc.listen-address";
    static final String ALT_NAMES = "common-rpc.certificate-provisioning.alt-names";
    static final String IP_SUBJECT_ALT_NAMES = "common-rpc.certificate-provisioning.ip-subject-alt-names";

    private static final String LOOPBACK_IPV4 = "127.0.0.1";
    private static final String LOOPBACK_IPV6 = "0:0:0:0:0:0:0:1";
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final LocalAddressResolver resolver;

    OpMonitorHostRule(LocalAddressResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * @param host     effective 7.8 host value, {@code null} or blank when absent
     * @param existing returns the current value of a legacy-shaped key, or {@code null}; consulted only for the
     *                 certificate rows
     */
    Result derive(String host, Function<String, String> existing) {
        if (host == null || host.isBlank()) {
            return new Result(Map.of(), null);
        }
        var value = host.trim();
        var rows = new LinkedHashMap<String, String>();
        String warning = null;
        try {
            if (isIpLiteral(value)) {
                deriveFromAddress(value, InetAddress.getByName(value), existing, rows);
            } else {
                deriveFromName(value, existing, rows);
            }
        } catch (UnknownHostException e) {
            rows.clear();
            rows.put(CHANNEL_HOST, value);
            warning = "op-monitor host '%s' cannot be resolved; keeping it as the channel host only".formatted(value);
            log.warn(warning);
        }
        return new Result(rows, warning);
    }

    private void deriveFromAddress(String value, InetAddress address, Function<String, String> existing,
                                   Map<String, String> rows) {
        if (address.isAnyLocalAddress()) {
            rows.put(LISTEN_ADDRESS, value);
            return;
        }
        rows.put(CHANNEL_HOST, value);
        if (LOOPBACK_IPV4.equals(address.getHostAddress())) {
            return;
        }
        if (address.isLoopbackAddress() || resolver.isLocalAddress(address)) {
            rows.put(LISTEN_ADDRESS, value);
            rows.put(IP_SUBJECT_ALT_NAMES, merge(existing, IP_SUBJECT_ALT_NAMES, LOOPBACK_IPV4, value));
        }
    }

    private void deriveFromName(String value, Function<String, String> existing, Map<String, String> rows)
            throws UnknownHostException {
        var addresses = resolver.resolve(value);
        rows.put(CHANNEL_HOST, value);
        if (addresses.stream().anyMatch(a -> !a.isLoopbackAddress() && resolver.isLocalAddress(a))) {
            rows.put(LISTEN_ADDRESS, value);
            rows.put(ALT_NAMES, merge(existing, ALT_NAMES, value));
        } else if (addresses.stream().anyMatch(InetAddress::isLoopbackAddress)) {
            if (addresses.stream().allMatch(InetAddress::isLoopbackAddress)
                    && addresses.stream().noneMatch(OpMonitorHostRule::isDefaultLoopback)) {
                rows.put(LISTEN_ADDRESS, value);
            }
            rows.put(ALT_NAMES, merge(existing, ALT_NAMES, value));
        }
    }

    private static boolean isDefaultLoopback(InetAddress address) {
        var text = address.getHostAddress();
        return LOOPBACK_IPV4.equals(text) || LOOPBACK_IPV6.equals(text);
    }

    private static boolean isIpLiteral(String value) {
        return value.indexOf(':') >= 0 || IPV4_LITERAL.matcher(value).matches();
    }

    private static String merge(Function<String, String> existing, String key, String... additions) {
        var merged = new ArrayList<String>();
        var current = existing.apply(key);
        if (current != null) {
            for (String token : current.split(",")) {
                addIfAbsent(merged, token);
            }
        }
        for (String addition : additions) {
            addIfAbsent(merged, addition);
        }
        return String.join(",", merged);
    }

    private static void addIfAbsent(List<String> values, String candidate) {
        var token = candidate.trim();
        if (!token.isEmpty() && values.stream().noneMatch(token::equalsIgnoreCase)) {
            values.add(token);
        }
    }

    record Result(Map<String, String> rows, String warning) {
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
                    } catch (SocketException e) {
                        return false;
                    }
                }
            };
        }
    }
}
