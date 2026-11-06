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
package org.niis.xroad.proxy.core.clientproxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATA_PLANE_UNREACHABLE;
import static org.niis.xroad.common.core.exception.ErrorCode.HTTP_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;

class DspFailoverTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("failoverCases")
    void failsOverOnlyOnATransportFaultWithACandidateLeftRetryOnAndAResendableBody(
            String ignoredCase, Throwable failure, int attempt, int candidateCount, boolean retryEnabled,
            boolean bodyResendable, boolean expected) {
        assertThat(DspFailover.canFailOver(failure, attempt, candidateCount, retryEnabled, () -> bodyResendable))
                .isEqualTo(expected);
    }

    @Test
    void theBodyIsOnlyCompletedWhenEveryOtherConditionHolds() {
        var refused = new ConnectException("refused");
        var completions = new AtomicInteger();

        DspFailover.canFailOver(refused, 2, 2, true, () -> completions.incrementAndGet() > 0);
        DspFailover.canFailOver(refused, 1, 2, false, () -> completions.incrementAndGet() > 0);
        DspFailover.canFailOver(new IllegalStateException("not a transport fault"), 1, 2, true,
                () -> completions.incrementAndGet() > 0);
        assertThat(completions).hasValue(0);

        DspFailover.canFailOver(refused, 1, 2, true, () -> completions.incrementAndGet() > 0);
        assertThat(completions).hasValue(1);
    }

    @Test
    void aTransportFaultIsTranslatedToTheDataPlaneErrorAndAnyOtherFailureIsReturnedUnchanged() {
        var refused = new ConnectException("refused");
        var answer = XrdRuntimeException.systemException(HTTP_ERROR, "Server responded with error %s", 500);

        assertThat(DspFailover.translateTransportFault(refused)).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(IO_ERROR)).isTrue();
            assertThat(e.getErrorCodeMetadata()).contains("originalCode=" + DSP_DATA_PLANE_UNREACHABLE.code());
            assertThat(e.getCause()).isSameAs(refused);
        });
        assertThat(DspFailover.translateTransportFault(answer)).isSameAs(answer);
    }

    static Stream<Arguments> failoverCases() {
        var refused = new ConnectException("refused");
        return Stream.of(
                arguments("transport fault with a candidate left", refused, 1, 2, true, true, true),
                arguments("transport fault nested in another exception",
                        XrdRuntimeException.systemException(IO_ERROR, refused), 1, 2, true, true, true),
                arguments("every candidate tried", refused, 2, 2, true, true, false),
                arguments("more attempts than candidates", refused, 3, 2, true, true, false),
                arguments("a single candidate", refused, 1, 1, true, true, false),
                arguments("request retry disabled", refused, 1, 2, false, true, false),
                arguments("body no longer resendable", refused, 1, 2, true, false, false),
                arguments("answer from a live provider",
                        XrdRuntimeException.systemException(HTTP_ERROR, "Server responded with error %s", 500),
                        1, 2, true, true, false));
    }
}
