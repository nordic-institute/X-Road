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

package org.niis.xroad.edc.protocol.assetaccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityServerAuthorityTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://SS0.Example.org/p                       | ss0.example.org:443",
            "http://ss0.example.org/p                        | ss0.example.org:80",
            "https://ss0.example.org:8183/api/dsp            | ss0.example.org:8183",
            "http://SS0.example.org:8183/api/dsp             | ss0.example.org:8183",
            "https://ss0.example.org:443/p                   | ss0.example.org:443",
            "https://ss_a:8183/api/dsp                       | ss_a:8183",
            "https://10.0.0.1:8183/api/dsp                   | 10.0.0.1:8183",
            "https://[2001:DB8::1]:8183/p                    | [2001:db8::1]:8183",
            "https://[2001:db8::1]/p                         | [2001:db8::1]:443",
            "https://[2001:db8:0:0:0:0:0:1]:8183/p           | [2001:db8::1]:8183",
            "https://xrd-ss0.lxd:8183/api/dsp/DEV:COM:222/p  | xrd-ss0.lxd:8183"
    })
    void anHttpUrlHasTheLowercaseHostAndPortWithTheSchemeDefaultFilledIn(String address, String expected) {
        assertThat(SecurityServerAuthority.of(address)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss-a.example:8183/p", "not a url", "ftp://ss-a.example/p", "https://", "http://[::1"})
    void anAddressThatIsNotAnHttpUrlIsItsOwnKey(String address) {
        assertThat(SecurityServerAuthority.of(address)).isEqualTo(address);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void aBlankAddressIsItsOwnKey(String address) {
        assertThat(SecurityServerAuthority.of(address)).isEqualTo(address);
    }

    @Test
    void theSameServerOnAnotherPortHasAnotherKey() {
        assertThat(SecurityServerAuthority.of("https://ss-a.example:8183/p"))
                .isNotEqualTo(SecurityServerAuthority.of("https://ss-a.example:9999/p"));
    }

    @Test
    void theSameServerSpelledWithAndWithoutTheDefaultPortHasOneKey() {
        assertThat(SecurityServerAuthority.of("https://ss-a.example/p"))
                .isEqualTo(SecurityServerAuthority.of("HTTPS://SS-A.example:443/q"));
    }
}
