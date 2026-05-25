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
package org.niis.xroad.edc.extension.assetaccess.grpc;

import org.eclipse.edc.spi.EdcException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

class DspFailureClassifierTest {

    static Stream<Arguments> mappingTable() {
        return Stream.of(
                Arguments.of(
                        new EdcException("No dataset found for asset ID: my-asset"),
                        UNKNOWN_MEMBER),
                Arguments.of(
                        new EdcException("No offers found for asset ID: my-asset"),
                        UNKNOWN_MEMBER),
                Arguments.of(
                        new EdcException("No PULL distribution found for asset ID: my-asset"),
                        SERVICE_FAILED),
                Arguments.of(
                        new EdcException("Failed to fetch catalog: connection refused"),
                        NETWORK_ERROR),
                Arguments.of(
                        new EdcException("Failed to parse catalog: unexpected token"),
                        NETWORK_ERROR),
                Arguments.of(
                        new EdcException("Error parsing catalog response", new RuntimeException()),
                        NETWORK_ERROR),
                Arguments.of(
                        new EdcException("Asset access acquisition timed out after 60s", new TimeoutException()),
                        NETWORK_ERROR),
                Arguments.of(
                        new EdcException("Failed to resolve participant context: ctx not found"),
                        INTERNAL_ERROR),
                Arguments.of(
                        new EdcException("Some unknown EDC error"),
                        SERVICE_FAILED),
                Arguments.of(
                        new RuntimeException((String) null),
                        SERVICE_FAILED)
        );
    }

    @ParameterizedTest
    @MethodSource("mappingTable")
    void classifyMapsToExpectedErrorCode(Throwable cause, ErrorCode expectedCode) {
        var result = DspFailureClassifier.classify(cause);

        assertThat(result).isInstanceOf(XrdRuntimeException.class);
        assertThat(result.getCode()).isEqualTo(expectedCode.code());
    }

    @ParameterizedTest
    @MethodSource("mappingTable")
    void classifyPreservesOriginalCauseInChain(Throwable cause, ErrorCode ignored) {
        var result = DspFailureClassifier.classify(cause);

        if (!(cause instanceof XrdRuntimeException)) {
            assertThat(result.getCause()).isSameAs(cause);
        }
    }

    @Test
    void classifyPassesThroughXrdRuntimeException() {
        var original = XrdRuntimeException.systemException(UNKNOWN_MEMBER, "already classified");

        var result = DspFailureClassifier.classify(original);

        assertThat(result).isSameAs(original);
    }
}
