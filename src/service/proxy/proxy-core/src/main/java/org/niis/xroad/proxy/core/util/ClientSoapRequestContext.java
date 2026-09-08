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

import org.niis.xroad.opmonitor.api.OpMonitoringData;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * ProxyRequestContext for client-side SOAP request paths. Carries the two CountDownLatches used to coordinate
 * the main thread and the SOAP handler thread during message processing, as well as piped streams for streaming
 * the SOAP request body.
 *
 * <p>Latches are initialized with count=1 and used once per request. Piped streams are created internally.
 * Implements {@link AutoCloseable} to ensure piped streams are released even on exceptional paths.
 */
public final class ClientSoapRequestContext implements ProxyRequestContext, AutoCloseable {

    private final RequestWrapper request;
    private final ResponseWrapper response;
    private final OpMonitoringData opMonitoringData;
    private final CountDownLatch requestHandlerGate;
    private final CountDownLatch httpSenderGate;
    private final PipedInputStream reqIns;
    private final PipedOutputStream reqOuts;

    /**
     * Creates a context with internally constructed piped streams and latches.
     * Handles partial-construction failure: if {@link PipedOutputStream} creation fails,
     * the already-opened {@link PipedInputStream} is closed before rethrowing.
     */
    public ClientSoapRequestContext(RequestWrapper request, ResponseWrapper response,
                                    OpMonitoringData opMonitoringData) throws IOException {
        this.request = request;
        this.response = response;
        this.opMonitoringData = opMonitoringData;
        this.requestHandlerGate = new CountDownLatch(1);
        this.httpSenderGate = new CountDownLatch(1);
        this.reqIns = new PipedInputStream();
        try {
            this.reqOuts = new PipedOutputStream(this.reqIns);
        } catch (IOException e) {
            this.reqIns.close();
            throw e;
        }
    }

    @Override
    public RequestWrapper request() {
        return request;
    }

    @Override
    public ResponseWrapper response() {
        return response;
    }

    @Override
    public OpMonitoringData opMonitoringData() {
        return opMonitoringData;
    }

    public CountDownLatch requestHandlerGate() {
        return requestHandlerGate;
    }

    public CountDownLatch httpSenderGate() {
        return httpSenderGate;
    }

    public PipedInputStream reqIns() {
        return reqIns;
    }

    public PipedOutputStream reqOuts() {
        return reqOuts;
    }

    @Override
    public void close() throws IOException {
        reqOuts.close();
        reqIns.close();
    }
}
