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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

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
}
