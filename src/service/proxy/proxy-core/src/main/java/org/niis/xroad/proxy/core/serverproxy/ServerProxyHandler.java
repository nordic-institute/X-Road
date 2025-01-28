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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.opmonitoring.OpMonitoring;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.proxy.core.util.MessageProcessorBase;
import org.niis.xroad.proxy.core.util.PerformanceLogger;

import java.io.IOException;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_MESSAGE_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.VALUE_MESSAGE_TYPE_REST;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.eclipse.jetty.server.Request.getRemoteAddr;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;

@Slf4j
class ServerProxyHandler extends HandlerBase {
    private final CommonBeanProxy commonBeanProxy;

    private final HttpClient client;
    private final HttpClient opMonitorClient;
    private final long idleTimeout = SystemProperties.getServerProxyConnectorMaxIdleTime();

    ServerProxyHandler(CommonBeanProxy commonBeanProxy, HttpClient client, HttpClient opMonitorClient) {
        this.commonBeanProxy = commonBeanProxy;
        this.client = client;
        this.opMonitorClient = opMonitorClient;
    }

    @Override
    @WithSpan
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        OpMonitoringData opMonitoringData = new OpMonitoringData(PRODUCER, getEpochMillisecond());

        long start = PerformanceLogger.log(log, "Received request from " + getRemoteAddr(request));

        if (!SystemProperties.isServerProxySupportClientsPooledConnections()) {
            // if the header is added, the connections are closed and cannot be reused on the client side
            response.getHeaders().add("Connection", "close");
        }

        try {
            if (!request.getMethod().equalsIgnoreCase("POST")) {
                throw new CodedException(X_INVALID_HTTP_METHOD, "Must use POST request method instead of %s",
                        request.getMethod());
            }

            commonBeanProxy.globalConfProvider.verifyValidity();

            ClientProxyVersionVerifier.check(request);
            final MessageProcessorBase processor = createRequestProcessor(RequestWrapper.of(request),
                    ResponseWrapper.of(response), opMonitoringData);
            processor.process();
        } catch (Throwable e) { // We want to catch serious errors as well
            CodedException cex = translateWithPrefix(SERVER_SERVERPROXY_X, e);

            log.error("Request processing error ({})", cex.getFaultDetail(), e);

            opMonitoringData.setFaultCodeAndString(cex);
            opMonitoringData.setResponseOutTs(getEpochMillisecond(), false);

            failure(request, response, callback, cex);
        } finally {
            callback.succeeded();

            opMonitoringData.setResponseOutTs(getEpochMillisecond(), false);
            OpMonitoring.store(opMonitoringData);

            PerformanceLogger.log(log, start, "Request handled");
        }
        return true;
    }

    private MessageProcessorBase createRequestProcessor(RequestWrapper request, ResponseWrapper response,
                                                        OpMonitoringData opMonitoringData) {

        if (VALUE_MESSAGE_TYPE_REST.equals(request.getHeaders().get(HEADER_MESSAGE_TYPE))) {
            return new ServerRestMessageProcessor(commonBeanProxy,
                    request, response, client, request.getPeerCertificates()
                    .orElse(null),
                    opMonitoringData);
        } else {
            return new ServerMessageProcessor(commonBeanProxy,
                    request, response, client, request.getPeerCertificates()
                    .orElse(null),
                    opMonitorClient, opMonitoringData);
        }
    }

    @Override
    protected void failure(Request request, Response response, Callback callback, CodedException e)
            throws IOException {
        sendErrorResponse(request, response, callback, e);
    }
}
