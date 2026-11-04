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

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HostIdentityTest {

    private static final String FQDN = "cs1.example.org";
    private static final String SHORT = "cs1";

    @Test
    void mapsHostFactsToCommonNameDnsNamesAndIpNames() throws UnknownHostException {
        var identity = HostIdentity.of(FQDN, SHORT, List.of(address("10.0.2.15"), address("fd17:625c:f037:2:a00:27ff:fe7f:dfb6")));

        assertThat(identity.commonName()).isEqualTo(FQDN);
        assertThat(identity.dnsNames()).containsExactly(FQDN, SHORT);
        assertThat(identity.ipAddresses()).containsExactly("10.0.2.15", "fd17:625c:f037:2:a00:27ff:fe7f:dfb6");
    }

    @Test
    void leavesLoopbackLinkLocalWildcardAndMulticastAddressesOut() throws UnknownHostException {
        var identity = HostIdentity.of(FQDN, SHORT, List.of(
                address("127.0.0.1"),
                address("::1"),
                address("169.254.10.1"),
                address("fe80::a00:27ff:fe7f:dfb6"),
                address("0.0.0.0"),
                address("224.0.0.1"),
                address("192.168.1.20")));

        assertThat(identity.ipAddresses()).containsExactly("192.168.1.20");
    }

    @Test
    void listsARepeatedAddressOnce() throws UnknownHostException {
        var identity = HostIdentity.of(FQDN, SHORT, List.of(address("10.0.2.15"), address("10.0.2.15")));

        assertThat(identity.ipAddresses()).containsExactly("10.0.2.15");
    }

    @Test
    void listsASingleDnsNameWhenTheHostNameHasNoDomain() {
        var identity = HostIdentity.of(SHORT, SHORT, List.of());

        assertThat(identity.commonName()).isEqualTo(SHORT);
        assertThat(identity.dnsNames()).containsExactly(SHORT);
        assertThat(identity.ipAddresses()).isEmpty();
    }

    @Test
    void usesTheShortNameAsCommonNameWhenTheFullyQualifiedNameExceedsTheCommonNameLimit() {
        var longName = "h".repeat(HostIdentity.MAX_COMMON_NAME_LENGTH - 3) + ".example.org";

        var identity = HostIdentity.of(longName, SHORT, List.of());

        assertThat(identity.commonName()).isEqualTo(SHORT);
        assertThat(identity.dnsNames()).containsExactly(longName, SHORT);
    }

    @Test
    void keepsTheFullyQualifiedNameAsCommonNameAtTheLimit() {
        var name = "h".repeat(HostIdentity.MAX_COMMON_NAME_LENGTH);

        assertThat(HostIdentity.of(name, SHORT, List.of()).commonName()).isEqualTo(name);
    }

    @Test
    void detectsALocalIdentityWithANonEmptyCommonName() {
        var identity = HostIdentity.local();

        assertThat(identity.commonName()).isNotBlank();
        assertThat(identity.dnsNames()).isNotEmpty().doesNotContain("");
        assertThat(identity.ipAddresses()).noneMatch(ip -> ip.startsWith("127.") || ip.startsWith("169.254.") || ip.equals("::1"));
    }

    private static InetAddress address(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }
}
