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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.niis.xroad.common.core.exception.ClientFacingErrorPolicy;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.function.BooleanSupplier;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATA_PLANE_UNREACHABLE;
import static org.niis.xroad.common.core.exception.ErrorOrigin.DATASPACE;

/**
 * Decisions of the DSP data-plane failover loop shared by the REST and the SOAP client message processors.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DspFailover {

    /**
     * Tells whether a failed DSP send may be resent to another Security Server: the failure must be a transport
     * fault, fewer attempts than candidate Security Servers must have been made, request retry must be enabled
     * and the request body must be resendable.
     *
     * @param bodyResendable evaluated last, only when every other condition holds, because it may have side effects
     */
    static boolean canFailOver(Throwable failure, int attempt, int candidateCount, boolean retryEnabled,
                               BooleanSupplier bodyResendable) {
        return TransportFaultClassifier.isTransportFault(failure)
                && attempt < candidateCount
                && retryEnabled
                && bodyResendable.getAsBoolean();
    }

    /**
     * Translates a transport fault into the sanitized {@code DSP_DATA_PLANE_UNREACHABLE} error; any other failure
     * is returned unchanged.
     */
    static Exception translateTransportFault(Exception failure) {
        return TransportFaultClassifier.isTransportFault(failure) ? dataPlaneUnreachable(failure) : failure;
    }

    private static XrdRuntimeException dataPlaneUnreachable(Throwable failure) {
        return ClientFacingErrorPolicy.sanitizeForConsumer(XrdRuntimeException.systemException(DSP_DATA_PLANE_UNREACHABLE)
                .origin(DATASPACE)
                .cause(failure)
                .details("No provider security server could be reached on the data plane")
                .build());
    }
}
