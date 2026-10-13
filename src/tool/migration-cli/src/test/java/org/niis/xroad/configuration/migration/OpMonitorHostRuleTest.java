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

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.configuration.migration.OpMonitorHostRule.ALT_NAMES;
import static org.niis.xroad.configuration.migration.OpMonitorHostRule.CHANNEL_HOST;
import static org.niis.xroad.configuration.migration.OpMonitorHostRule.IP_SUBJECT_ALT_NAMES;
import static org.niis.xroad.configuration.migration.OpMonitorHostRule.LISTEN_ADDRESS;

class OpMonitorHostRuleTest {

    private static final String LOCAL_IPV4 = "192.0.2.10";
    private static final String LOCAL_IPV6 = "2001:db8::10";
    private static final String REMOTE_IPV4 = "198.51.100.7";
    private static final String LOCAL_NAME = "ss1.example";
    private static final String REMOTE_NAME = "opmon.example";
    private static final String MIXED_NAME = "ss-and-remote.example";
    private static final String LOOPBACK_AND_REMOTE_NAME = "loopback-and-remote.example";
    private static final String DEBIAN_NAME = "debian-host.example";
    private static final String IPV4_LOOPBACK_NAME = "loopback-v4.example";
    private static final String IPV6_LOOPBACK_NAME = "loopback-v6.example";
    private static final String OTHER_AND_IPV6_LOOPBACK_NAME = "loopback-other-and-v6.example";
    private static final String UNRESOLVABLE_NAME = "missing.example";

    private final OpMonitorHostRule rule = new OpMonitorHostRule(new FakeResolver());

    @Test
    void absentHostWritesNothing() {
        assertThat(rule.derive(null, none()).rows()).isEmpty();
        assertThat(rule.derive("", none()).rows()).isEmpty();
        assertThat(rule.derive("  ", none()).rows()).isEmpty();
    }

    @Test
    void loopbackIpv4WritesChannelOnly() {
        assertThat(rule.derive("127.0.0.1", none()).rows()).containsExactlyEntriesOf(Map.of(CHANNEL_HOST, "127.0.0.1"));
    }

    @Test
    void localhostWritesChannelAndDnsSan() {
        assertThat(rule.derive("localhost", none()).rows())
                .containsOnly(entry(CHANNEL_HOST, "localhost"), entry(ALT_NAMES, "localhost"));
    }

    @Test
    void ipv6LoopbackWritesChannelListenerAndIpSan() {
        assertThat(rule.derive("::1", none()).rows())
                .containsOnly(entry(CHANNEL_HOST, "::1"), entry(LISTEN_ADDRESS, "::1"),
                        entry(IP_SUBJECT_ALT_NAMES, "127.0.0.1,::1"));
    }

    @Test
    void otherLoopbackIpWritesChannelListenerAndIpSan() {
        assertThat(rule.derive("127.0.0.2", none()).rows())
                .containsOnly(entry(CHANNEL_HOST, "127.0.0.2"), entry(LISTEN_ADDRESS, "127.0.0.2"),
                        entry(IP_SUBJECT_ALT_NAMES, "127.0.0.1,127.0.0.2"));
    }

    @Test
    void wildcardWritesListenerOnly() {
        assertThat(rule.derive("0.0.0.0", none()).rows()).containsExactlyEntriesOf(Map.of(LISTEN_ADDRESS, "0.0.0.0"));
        assertThat(rule.derive("::", none()).rows()).containsExactlyEntriesOf(Map.of(LISTEN_ADDRESS, "::"));
    }

    @Test
    void localIpv4WritesChannelListenerAndIpSan() {
        assertThat(rule.derive(LOCAL_IPV4, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, LOCAL_IPV4), entry(LISTEN_ADDRESS, LOCAL_IPV4),
                        entry(IP_SUBJECT_ALT_NAMES, "127.0.0.1," + LOCAL_IPV4));
    }

    @Test
    void localIpv6WritesChannelListenerAndIpSan() {
        assertThat(rule.derive(LOCAL_IPV6, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, LOCAL_IPV6), entry(LISTEN_ADDRESS, LOCAL_IPV6),
                        entry(IP_SUBJECT_ALT_NAMES, "127.0.0.1," + LOCAL_IPV6));
    }

    @Test
    void localHostnameWritesChannelListenerAndDnsSan() {
        assertThat(rule.derive(LOCAL_NAME, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, LOCAL_NAME), entry(LISTEN_ADDRESS, LOCAL_NAME),
                        entry(ALT_NAMES, LOCAL_NAME));
    }

    @Test
    void nameResolvingToLocalAndRemoteCountsAsLocal() {
        assertThat(rule.derive(MIXED_NAME, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, MIXED_NAME), entry(LISTEN_ADDRESS, MIXED_NAME),
                        entry(ALT_NAMES, MIXED_NAME));
    }

    @Test
    void nameResolvingToLoopbackAndRemoteWritesChannelAndDnsSanOnly() {
        assertThat(rule.derive(LOOPBACK_AND_REMOTE_NAME, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, LOOPBACK_AND_REMOTE_NAME),
                        entry(ALT_NAMES, LOOPBACK_AND_REMOTE_NAME));
    }

    @Test
    void nameResolvingOnlyToOtherLoopbackWritesChannelListenerAndDnsSan() {
        assertThat(rule.derive(DEBIAN_NAME, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, DEBIAN_NAME), entry(LISTEN_ADDRESS, DEBIAN_NAME),
                        entry(ALT_NAMES, DEBIAN_NAME));
    }

    @Test
    void nameResolvingToIpv4LoopbackWritesChannelAndDnsSan() {
        assertThat(rule.derive(IPV4_LOOPBACK_NAME, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, IPV4_LOOPBACK_NAME), entry(ALT_NAMES, IPV4_LOOPBACK_NAME));
    }

    @Test
    void nameResolvingOnlyToIpv6LoopbackWritesChannelListenerAndDnsSan() {
        assertThat(rule.derive(IPV6_LOOPBACK_NAME, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, IPV6_LOOPBACK_NAME), entry(LISTEN_ADDRESS, IPV6_LOOPBACK_NAME),
                        entry(ALT_NAMES, IPV6_LOOPBACK_NAME));
    }

    @Test
    void nameResolvingToOtherLoopbackAndIpv6LoopbackWritesChannelListenerAndDnsSan() {
        assertThat(rule.derive(OTHER_AND_IPV6_LOOPBACK_NAME, none()).rows())
                .containsOnly(entry(CHANNEL_HOST, OTHER_AND_IPV6_LOOPBACK_NAME),
                        entry(LISTEN_ADDRESS, OTHER_AND_IPV6_LOOPBACK_NAME),
                        entry(ALT_NAMES, OTHER_AND_IPV6_LOOPBACK_NAME));
    }

    @Test
    void remoteIpv4AndHostnameWriteChannelOnly() {
        assertThat(rule.derive(REMOTE_IPV4, none()).rows())
                .containsExactlyEntriesOf(Map.of(CHANNEL_HOST, REMOTE_IPV4));
        assertThat(rule.derive(REMOTE_NAME, none()).rows())
                .containsExactlyEntriesOf(Map.of(CHANNEL_HOST, REMOTE_NAME));
    }

    @Test
    void unresolvableHostWritesChannelOnlyAndWarns() {
        var result = rule.derive(UNRESOLVABLE_NAME, none());

        assertThat(result.rows()).containsExactlyEntriesOf(Map.of(CHANNEL_HOST, UNRESOLVABLE_NAME));
        assertThat(result.warning()).contains(UNRESOLVABLE_NAME);
    }

    @Test
    void resolvedHostHasNoWarning() {
        assertThat(rule.derive(LOCAL_NAME, none()).warning()).isNull();
    }

    @Test
    void rerunOnMigratedRowsChangesNothing() {
        var existing = Map.of(ALT_NAMES, LOCAL_NAME, IP_SUBJECT_ALT_NAMES, "127.0.0.1," + LOCAL_IPV4);

        assertThat(rule.derive(LOCAL_NAME, existing::get).rows())
                .containsEntry(ALT_NAMES, LOCAL_NAME);
        assertThat(rule.derive(LOCAL_IPV4, existing::get).rows())
                .containsEntry(IP_SUBJECT_ALT_NAMES, "127.0.0.1," + LOCAL_IPV4);
    }

    @Test
    void sanRowsAreMergedWithExistingValuesWithoutDuplicates() {
        var existing = Map.of(ALT_NAMES, "other.example,SS1.EXAMPLE", IP_SUBJECT_ALT_NAMES, "127.0.0.1,203.0.113.5");

        assertThat(rule.derive(LOCAL_NAME, existing::get).rows())
                .containsEntry(ALT_NAMES, "other.example,SS1.EXAMPLE");
        assertThat(rule.derive("::1", existing::get).rows())
                .containsEntry(IP_SUBJECT_ALT_NAMES, "127.0.0.1,203.0.113.5,::1");
        assertThat(rule.derive("localhost", existing::get).rows())
                .containsEntry(ALT_NAMES, "other.example,SS1.EXAMPLE,localhost");
    }

    private static Function<String, String> none() {
        return key -> null;
    }

    private static Map.Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }

    static final class FakeResolver implements OpMonitorHostRule.LocalAddressResolver {
        private final Map<String, List<InetAddress>> names = Map.of(
                "localhost", List.of(address("127.0.0.1"), address("::1")),
                LOCAL_NAME, List.of(address(LOCAL_IPV4)),
                REMOTE_NAME, List.of(address(REMOTE_IPV4)),
                MIXED_NAME, List.of(address(REMOTE_IPV4), address(LOCAL_IPV4)),
                LOOPBACK_AND_REMOTE_NAME, List.of(address("127.0.0.1"), address(REMOTE_IPV4)),
                DEBIAN_NAME, List.of(address("127.0.1.1")),
                IPV4_LOOPBACK_NAME, List.of(address("127.0.0.1")),
                IPV6_LOOPBACK_NAME, List.of(address("::1")),
                OTHER_AND_IPV6_LOOPBACK_NAME, List.of(address("127.0.1.1"), address("::1")));
        private final Set<InetAddress> localAddresses = Set.of(address(LOCAL_IPV4), address(LOCAL_IPV6));

        @Override
        public List<InetAddress> resolve(String name) throws UnknownHostException {
            var addresses = names.get(name);
            if (addresses == null) {
                throw new UnknownHostException(name);
            }
            return addresses;
        }

        @Override
        public boolean isLocalAddress(InetAddress address) {
            return localAddresses.contains(address);
        }

        private static InetAddress address(String literal) {
            try {
                return InetAddress.getByName(literal);
            } catch (UnknownHostException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
