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

package org.niis.xroad.proxy.core.clientproxy;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

/**
 * Decides when a data call that failed on a provider security server moves on to another one, and moves it.
 *
 * <p>A data call fails over only when it could not connect to the provider: connection refused, connect
 * timeout, no route to host, unknown host. Once the connection stands, the provider may already have processed
 * the request, so a read timeout, a TLS error, an HTTP error response and every other failure reach the client
 * unchanged and leave the cached asset access alone.
 */
@Slf4j
final class DataCallFailover {

    private DataCallFailover() {
    }

    /**
     * Tells whether the failure happened while connecting, by exception type rather than message. The fastest
     * connection selector wraps its connect failures in {@code NETWORK_ERROR}; a connect timeout there is a
     * {@link SocketTimeoutException} under that code, while a read timeout of an established connection is a
     * bare {@link SocketTimeoutException}.
     */
    static boolean isConnectionFailure(Throwable failure) {
        for (var cause : ExceptionUtils.getThrowableList(failure)) {
            if (cause instanceof ConnectException || cause instanceof NoRouteToHostException
                    || cause instanceof UnknownHostException || cause instanceof ConnectTimeoutException) {
                return true;
            }
            if (cause instanceof XrdRuntimeException xrdException && xrdException.isCausedBy(NETWORK_ERROR)
                    && ExceptionUtils.indexOfType(xrdException, SocketTimeoutException.class) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fails the data call over: drops the failed server's cached asset access and acquires asset access from
     * another candidate server.
     *
     * @return the asset access of another provider server, or empty when no other server is available or
     *         acquiring from the others fails; the acquisition failure is then attached to {@code failure} as
     *         suppressed, and the caller rethrows {@code failure}
     */
    static Optional<AssetAccessResponse> failOver(DspRequestProcessor processor, DspRequest request,
                                                  AssetAccessResponse failed, Exception failure) {
        try {
            var next = processor.failOver(request, failed);
            next.ifPresent(access -> log.warn("Data call to {} failed to connect, failing over to {}",
                    failed.endpoint(), access.endpoint(), failure));
            return next;
        } catch (RuntimeException acquisitionFailure) {
            failure.addSuppressed(acquisitionFailure);
            return Optional.empty();
        }
    }
}
