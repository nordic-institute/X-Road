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
package org.niis.xroad.common.vault.spring;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.ResourceAccessException;

import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SpringVaultFailuresTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("ioFailures")
    void shouldRetryAnIoFailureExceptATlsHandshakeFailure(String name, Throwable failure, boolean retryable) {
        assertThat(SpringVaultFailures.isRetryable(failure)).isEqualTo(retryable);
    }

    private static Stream<Arguments> ioFailures() {
        var handshake = new SSLHandshakeException("PKIX path building failed");

        return Stream.of(
                Arguments.of("connection failure",
                        new ResourceAccessException("I/O error", new IOException("Connection refused")), true),
                Arguments.of("unknown host",
                        new ResourceAccessException("I/O error", new UnknownHostException("openbao")), true),
                Arguments.of("bare handshake failure", handshake, false),
                Arguments.of("handshake failure inside an IOException", new IOException("tls", handshake), false),
                Arguments.of("handshake failure inside a ResourceAccessException",
                        new ResourceAccessException("I/O error", handshake), false));
    }
}
