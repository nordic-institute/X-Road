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

package org.niis.xroad.cs.admin.core.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.exception.BadRequestException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DnsNameValidatorTest {

    private final DnsNameValidator dnsNameValidator = new DnsNameValidator();

    @ParameterizedTest
    @MethodSource("validDnsNames")
    void acceptsValidDnsNames(String host) {
        assertThat(dnsNameValidator.isValid(host)).isTrue();
        assertThatCode(() -> dnsNameValidator.validate(host)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("invalidDnsNames")
    void rejectsInvalidDnsNames(String host) {
        assertThat(dnsNameValidator.isValid(host)).isFalse();
        assertThatThrownBy(() -> dnsNameValidator.validate(host)).isInstanceOf(BadRequestException.class);
    }

    private static Stream<Arguments> validDnsNames() {
        return Stream.of(
                Arguments.of("cs.example.org"),
                Arguments.of("centralserver"),
                Arguments.of("a".repeat(63) + "." + "b".repeat(63) + ".example.org")
        );
    }

    private static Stream<Arguments> invalidDnsNames() {
        return Stream.of(
                Arguments.of("192.0.2.1"),
                Arguments.of("2001:db8::1"),
                Arguments.of("localhost"),
                Arguments.of("LOCALHOST"),
                Arguments.of("a".repeat(64) + ".example.org"),
                Arguments.of(("a".repeat(63) + ".").repeat(4) + "example"),
                Arguments.of(""),
                Arguments.of("   "),
                // A trailing dot is valid DNS syntax for the root label, but it is rejected here:
                // see DnsNameValidator for why.
                Arguments.of("cs.example.org."),
                // Guava's InternetDomainName maps these Unicode full stops onto ASCII before validating.
                Arguments.of("localhost。"),
                Arguments.of("cs.example.org。"),
                Arguments.of("cs。example。org"),
                Arguments.of("cs.example.org．"),
                Arguments.of("cs.example.org｡"),
                Arguments.of("cs.exämple.org"),
                Arguments.of("my_host.example.org"),
                // Reserved domain, RFC 6761.
                Arguments.of("foo.localhost"),
                Arguments.of("a.b.localhost")
        );
    }
}
