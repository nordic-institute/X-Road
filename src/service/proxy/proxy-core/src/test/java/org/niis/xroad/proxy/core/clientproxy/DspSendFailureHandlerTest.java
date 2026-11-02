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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;

import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;
import static org.niis.xroad.common.core.exception.ErrorCode.HTTP_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;

@ExtendWith(MockitoExtension.class)
class DspSendFailureHandlerTest {

    private static final String WINNER_ID = "did:web:ss-a.example%3A7183:v1:DEV:COM:222";
    private static final String WINNER_ADDRESS = "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final AssetAccessResponse WON_BY_SS_A =
            new AssetAccessResponse("https://ss-a.example:8183/data", "token", WINNER_ID, WINNER_ADDRESS);

    @Mock
    private DspRequestProcessor dspRequestProcessor;

    private DspSendFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DspSendFailureHandler(dspRequestProcessor);
    }

    @ParameterizedTest
    @MethodSource("transportFaults")
    void aTransportFaultDropsTheAccessWonByTheWinnerAndReportsIt(Throwable fault) {
        handler.handle(fault, WON_BY_SS_A);

        verify(dspRequestProcessor).invalidateAssetAccessWonBy(WINNER_ADDRESS);
        verify(dspRequestProcessor).reportCounterPartyFailure(WINNER_ID, WINNER_ADDRESS);
    }

    @ParameterizedTest
    @MethodSource("otherFailures")
    void aFailureThatIsNoTransportFaultChangesNothing(Throwable failure) {
        handler.handle(failure, WON_BY_SS_A);

        verifyNoInteractions(dspRequestProcessor);
    }

    @Test
    void aTransportFaultWithoutAWinnerChangesNothing() {
        assertThatCode(() -> handler.handle(new ConnectException("refused"),
                new AssetAccessResponse("https://ss-a.example:8183/data", "token"))).doesNotThrowAnyException();

        verifyNoInteractions(dspRequestProcessor);
    }

    @Test
    void aTransportFaultWithoutAnAssetAccessChangesNothing() {
        assertThatCode(() -> handler.handle(new ConnectException("refused"), null)).doesNotThrowAnyException();

        verifyNoInteractions(dspRequestProcessor);
    }

    @Test
    void aFailingRemovalDoesNotStopTheReport() {
        doThrow(new IllegalStateException("cache broken")).when(dspRequestProcessor).invalidateAssetAccessWonBy(any());

        assertThatCode(() -> handler.handle(new ConnectException("refused"), WON_BY_SS_A)).doesNotThrowAnyException();

        verify(dspRequestProcessor).reportCounterPartyFailure(WINNER_ID, WINNER_ADDRESS);
    }

    @Test
    void aFailingReportStillLeavesTheRemovalDone() {
        doThrow(new IllegalStateException("report broken")).when(dspRequestProcessor)
                .reportCounterPartyFailure(any(), any());

        assertThatCode(() -> handler.handle(new ConnectException("refused"), WON_BY_SS_A)).doesNotThrowAnyException();

        verify(dspRequestProcessor).invalidateAssetAccessWonBy(WINNER_ADDRESS);
    }

    @Test
    void twoConcurrentFaultsOnTheSameServerSendTwoReportsAndFailNowhere() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var startGate = new CountDownLatch(1);
        try {
            var futures = Stream.of(1, 2)
                    .map(_ -> executor.submit(() -> {
                        startGate.await();
                        handler.handle(new SocketTimeoutException("Read timed out"), WON_BY_SS_A);
                        return null;
                    }))
                    .toList();
            startGate.countDown();
            for (var future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        verify(dspRequestProcessor, times(2)).invalidateAssetAccessWonBy(WINNER_ADDRESS);
        verify(dspRequestProcessor, times(2)).reportCounterPartyFailure(WINNER_ID, WINNER_ADDRESS);
        verify(dspRequestProcessor, never()).execute(any());
    }

    static Stream<Throwable> transportFaults() {
        return Stream.of(
                new ConnectException("refused"),
                new SSLHandshakeException("handshake_failure"),
                new SocketTimeoutException("Read timed out"),
                XrdRuntimeException.systemException(IO_ERROR, new ConnectException("refused")));
    }

    static Stream<Throwable> otherFailures() {
        return Stream.of(
                XrdRuntimeException.systemException(HTTP_ERROR, "Server responded with error %s: %s", 503,
                        "Service Unavailable"),
                XrdRuntimeException.systemException(SERVICE_FAILED, "service failed"),
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "token rejected"),
                new IOException("broken"));
    }
}
