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

package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import jakarta.annotation.Nullable;
import org.niis.xroad.opmonitor.api.OpMonitoringData;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * ProxyRequestContext for SOAP request paths. Carries the two CountDownLatches used to coordinate the main thread and
 * the SOAP handler thread during message processing, as well as piped streams for streaming the SOAP request body.
 *
 * <p>Both latches are initialized with count=1 and used once per request.
 */
public record SoapRequestContext(
        RequestWrapper request,
        ResponseWrapper response,
        @Nullable OpMonitoringData opMonitoringData,
        @Nullable String targetAddress,
        /**
         * The main thread waits on this latch until the SOAP handler thread has parsed the request SOAP message and
         * set requestServiceId. The handler thread calls countDown() once parsing is complete (success or failure).
         * Null for server-side SOAP processing where no latch coordination is needed.
         */
        @Nullable CountDownLatch requestHandlerGate,
        /**
         * The main thread waits on this latch after sending the HTTP request to ensure the handler thread has finished
         * writing to the piped output stream before the response is read. The handler thread calls countDown() when
         * writing is complete (success or failure).
         * Null for server-side SOAP processing where no latch coordination is needed.
         */
        @Nullable CountDownLatch httpSenderGate,
        /**
         * PipedInputStream connected to reqOuts — the main thread reads from this to stream the SOAP request body
         * to the HTTP sender.
         * Null for server-side SOAP processing where piped streams are not needed.
         */
        @Nullable PipedInputStream reqIns,
        /**
         * PipedOutputStream connected to reqIns — the handler thread writes decoded SOAP body to this stream.
         * Null for server-side SOAP processing where piped streams are not needed.
         */
        @Nullable PipedOutputStream reqOuts
) implements ProxyRequestContext {
}
