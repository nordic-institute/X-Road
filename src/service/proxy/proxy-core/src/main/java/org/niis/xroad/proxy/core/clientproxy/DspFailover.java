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

import ee.ria.xroad.common.util.HttpSender;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.ClientFacingErrorPolicy;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;

import java.util.function.BooleanSupplier;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATA_PLANE_UNREACHABLE;
import static org.niis.xroad.common.core.exception.ErrorOrigin.DATASPACE;

/**
 * Sends a request over the DSP data plane and moves it to another candidate Security Server when the chosen one
 * never received it. A failure after the request was sent is never retried.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class DspFailover {

    private final DspRequestProcessor dspRequestProcessor;
    private final HttpSenderProvider httpSenderProvider;
    private final DspSendFailureHandler dspSendFailureHandler;

    /**
     * One send of the request to the winner of an acquisition.
     *
     * @param <T> the outcome of a successful send
     */
    @FunctionalInterface
    @ArchUnitSuppressed("NoVanillaExceptions")
    public interface Send<T> {
        T send(HttpSender httpSender, AssetAccessResponse access) throws Exception;
    }

    /**
     * Acquires asset access and sends. When the send fails, the sender is closed and the failure handler
     * runs. The failure is then thrown, as {@code DSP_DATA_PLANE_UNREACHABLE} when nothing was sent, unless
     * nothing was sent and an untried candidate remains, request retry is enabled and the body can be resent.
     * Then the winner is excluded and the loop acquires again.
     *
     * @param bodyResendable evaluated last, only when every other condition holds, because it may have side effects
     * @return the outcome of the successful send
     */
    public <T> T run(DspRequest request, String xRequestId, boolean retryEnabled, BooleanSupplier bodyResendable,
                     Send<T> send) throws Exception {
        var current = request;
        while (true) {
            var acquisition = dspRequestProcessor.execute(current);
            var access = acquisition.access();
            var httpSender = httpSenderProvider.createClientHttpSender();
            try {
                return send.send(httpSender, access);
            } catch (Exception failure) {
                httpSender.close();
                dspSendFailureHandler.handle(failure, access);
                var nothingSent = TransportFaultClassifier.nothingWasSent(failure);
                if (!nothingSent || acquisition.candidateCount() <= 1 || !retryEnabled || !bodyResendable.getAsBoolean()) {
                    throw nothingSent ? dataPlaneUnreachable(failure) : failure;
                }
                log.warn("Sending the request over the data plane failed before it was sent, acquiring access again"
                        + " (xRequestId: {}, candidates: {})", xRequestId, acquisition.candidateCount(), failure);
                current = current.withTriedServer(access.winner().counterPartyAddress());
            }
        }
    }

    private static XrdRuntimeException dataPlaneUnreachable(Throwable failure) {
        return ClientFacingErrorPolicy.sanitizeForConsumer(XrdRuntimeException.systemException(DSP_DATA_PLANE_UNREACHABLE)
                .origin(DATASPACE)
                .cause(failure)
                .details("No provider security server could be reached on the data plane")
                .build());
    }
}
