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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DidAuthorityTest {

    @ParameterizedTest
    @CsvSource({
            "ss0.example.org,    7183, ss0.example.org:7183",
            "SS0.Example.ORG,    7183, ss0.example.org:7183",
            "bücher.example, 7183, xn--bcher-kva.example:7183",
            "BÜCHER.example, 7183, xn--bcher-kva.example:7183",
            "ss0.example.org,    443,  ss0.example.org",
            "SS0.Example.ORG,    443,  ss0.example.org",
            "ss0.example.org,    8443, ss0.example.org:8443",
            "2001:db8::1,        7183, [2001:db8::1]:7183",
            "2001:DB8::A,        7183, [2001:db8::a]:7183",
            "[2001:db8::1],      7183, [2001:db8::1]:7183",
            "2001:db8::1,        443,  [2001:db8::1]",
            "127.0.0.1,          7183, 127.0.0.1:7183",
            "xrd-ss0.lxd,        7183, xrd-ss0.lxd:7183"
    })
    void shouldBuildCanonicalAuthority(String host, int port, String expected) {
        assertThat(DidAuthority.canonical(host, port)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "SS0.Example.ORG,    ss0.example.org",
            "bücher.example, xn--bcher-kva.example",
            "2001:db8::1,        [2001:db8::1]",
            "[2001:db8::1],      [2001:db8::1]"
    })
    void shouldBuildCanonicalHost(String host, String expected) {
        assertThat(DidAuthority.canonicalHost(host)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void shouldRefuseBlankHost(String host) {
        assertThatThrownBy(() -> DidAuthority.canonicalHost(host)).isInstanceOf(XrdRuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"b\u00fccher.example", "ss0.example.org\u00e9", "\u4f8b\u3048.jp"})
    void shouldRefuseNonAsciiAddressNamingIt(String address) {
        assertThatThrownBy(() -> DidAuthority.requireAscii(address))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("'" + address + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss0.example.org", "xn--bcher-kva.example", "2001:db8::1"})
    void shouldAcceptAsciiAddress(String address) {
        assertThatCode(() -> DidAuthority.requireAscii(address)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "ss0.example.org,         ss0.example.org,   ",
            "ss0.example.org:7183,    ss0.example.org,   7183",
            "SS0.Example.ORG:7183,    SS0.Example.ORG,   7183",
            "xn--bcher-kva.example,   xn--bcher-kva.example, ",
            "ss0.example.org:443,     ss0.example.org,   443",
            "ss0.example.org:1,       ss0.example.org,   1",
            "ss0.example.org:65535,   ss0.example.org,   65535",
            "127.0.0.1:7183,          127.0.0.1,         7183",
            "2001:db8::1,             2001:db8::1,       ",
            "[2001:db8::1],           2001:db8::1,       ",
            "[2001:db8::1]:7183,      2001:db8::1,       7183",
            "[2001:DB8::A]:443,       2001:DB8::A,       443"
    })
    void shouldParseAuthority(String authority, String expectedHost, Integer expectedPort) {
        var parsed = DidAuthority.parse(authority);

        assertThat(parsed.host()).isEqualTo(expectedHost);
        assertThat(parsed.port()).isEqualTo(expectedPort == null ? OptionalInt.empty() : OptionalInt.of(expectedPort));
    }

    @ParameterizedTest
    @CsvSource({
            "'',                       has an empty host",
            "':7183',                  has an empty host",
            "'[]',                     has an empty host",
            "'[]:7183',                has an empty host",
            "b\u00fccher.example,        must contain only ASCII characters",
            "b\u00fccher.example:7183,   must contain only ASCII characters",
            "[2001:db8::1,             malformed brackets",
            "2001:db8::1],             malformed brackets",
            "[2001:db8::1]x,           malformed brackets",
            "[[2001:db8::1]],          malformed brackets",
            "ss0.example.org],         malformed brackets",
            "[ss0.example.org:7183,    malformed brackets",
            "ss0.example.org:,         outside 1 to 65535",
            "ss0.example.org:0,        outside 1 to 65535",
            "ss0.example.org:65536,    outside 1 to 65535",
            "ss0.example.org:999999,   outside 1 to 65535",
            "ss0.example.org:-1,       outside 1 to 65535",
            "ss0.example.org:http,     outside 1 to 65535",
            "[2001:db8::1]:,           outside 1 to 65535",
            "[2001:db8::1]:70000,      outside 1 to 65535"
    })
    void shouldRefuseMalformedAuthorityWithDistinctReason(String authority, String reason) {
        assertThatThrownBy(() -> DidAuthority.parse(authority))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(reason);
    }

    @Test
    void shouldRefuseNullAuthorityAsEmptyHost() {
        assertThatThrownBy(() -> DidAuthority.parse(null))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("has an empty host");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ss0.example.org",
            "ss0.example.org:7183",
            "ss0.example.org:8443",
            "xn--bcher-kva.example:7183",
            "127.0.0.1:7183",
            "[2001:db8::1]",
            "[2001:db8::1]:7183"
    })
    void shouldAcceptCanonicalAuthority(String authority) {
        assertThat(DidAuthority.isCanonical(authority)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "ss0.example.org,   7183",
            "SS0.Example.ORG,   7183",
            "bücher.example,    7183",
            "ss0.example.org,   443",
            "2001:db8::1,       7183",
            "[2001:DB8::A],     7183"
    })
    void shouldAcceptAuthorityBuiltByCanonicalBuilder(String host, int port) {
        assertThat(DidAuthority.isCanonical(DidAuthority.canonical(host, port))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SS0.example.org:7183",
            "ss0.Example.org",
            "b\u00fccher.example:7183",
            "ss0.example.org:443",
            "ss0.example.org:07183",
            "2001:db8::1",
            "2001:db8::1:7183",
            "[2001:DB8::1]:7183",
            "[2001:db8::1]:443",
            "",
            "ss0.example.org:70000",
            "[2001:db8::1"
    })
    void shouldRejectNonCanonicalAuthority(String authority) {
        assertThat(DidAuthority.isCanonical(authority)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "ss0.example.org:7183,  true",
            "SS0.example.org:7183,  false",
            "ss0.example.org:443,   false",
            "ss0.example.org,       true"
    })
    void shouldCheckAuthorityOfMemberDid(String ssHost, boolean expected) {
        var member = ClientId.Conf.create("DEV", "COM", "222");

        var decoded = ParticipantIdentifierScheme.decodeDid(ParticipantIdentifierScheme.memberDid(member, ssHost));

        assertThat(DidAuthority.isCanonical(decoded.ssHost())).isEqualTo(expected);
    }
}
