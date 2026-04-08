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

import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.exception.XrdRuntimeHttpException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ProxyMessageUtils;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_HTTP_METHOD;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;

/**
 * Handles client SOAP messages by delegating to the CDI singleton {@link ClientSoapMessageProcessor}.
 * This handler must be the last handler in the handler collection, since it will not pass handling
 * of the request to the next handler if it cannot process the request itself.
 */
@Slf4j
public class ClientSoapMessageHandler extends HandlerBase {

    private final ClientSoapMessageProcessor clientSoapMessageProcessor;
    private final ProxyProperties proxyProperties;
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final OpMonitoringBuffer opMonitoringBuffer;
    private final OpMonitoringDataHelper opMonitoringDataHelper;

    /** Creates a new client SOAP message handler. */
    public ClientSoapMessageHandler(ClientSoapMessageProcessor clientSoapMessageProcessor,
                                    ProxyProperties proxyProperties, GlobalConfProvider globalConfProvider,
                                    KeyConfProvider keyConfProvider, OpMonitoringBuffer opMonitoringBuffer,
                                    OpMonitoringDataHelper opMonitoringDataHelper) {
        this.clientSoapMessageProcessor = clientSoapMessageProcessor;
        this.proxyProperties = proxyProperties;
        this.globalConfProvider = globalConfProvider;
        this.keyConfProvider = keyConfProvider;
        this.opMonitoringBuffer = opMonitoringBuffer;
        this.opMonitoringDataHelper = opMonitoringDataHelper;
    }

    @Override
    @WithSpan
    @SuppressWarnings({"java:S3776"})
    public boolean handle(Request request, Response response, Callback callback) throws IOException {
        boolean handled = false;
        long start = ProxyMessageUtils.logPerformanceBegin(request);
        OpMonitoringData opMonitoringData = new OpMonitoringData(CLIENT, start);

        try {
            var requestWrapper = RequestWrapper.of(request);
            verifyCanProcess(requestWrapper);

            var reqIns = new PipedInputStream();
            var reqOuts = new PipedOutputStream(reqIns);
            var ctx = new ClientSoapRequestContext(
                    requestWrapper, ResponseWrapper.of(response), opMonitoringData, null,
                    new CountDownLatch(1), new CountDownLatch(1), reqIns, reqOuts);

            handled = true;
            boolean success = clientSoapMessageProcessor.process(ctx);
            opMonitoringData.setSucceeded(success);
            callback.succeeded();
            if (log.isTraceEnabled()) {
                log.info("Request successfully handled ({} ms)", System.currentTimeMillis() - start);
            } else {
                log.info("Request successfully handled");
            }
        } catch (XrdRuntimeHttpException e) {
            handled = true;
            log.error("Request processing error", e);
            failure(response, callback, e, opMonitoringData);
        } catch (XrdRuntimeException e) {
            handled = true;
            String errorMessage;
            XrdRuntimeException exception = e;
            if (e.hasSoapFault()) {
                errorMessage = "Request processing error";
            } else {
                errorMessage = "Request processing error (" + e.getDetails() + ")";
                if (!e.originatesFrom(ErrorOrigin.CLIENT)) {
                    exception = e.withPrefix(SERVER_CLIENTPROXY_X);
                }
            }
            log.error(errorMessage, exception);
            opMonitoringDataHelper.updateOpMonitoringSoapFault(opMonitoringData, exception);
            failure(request, response, callback, exception, opMonitoringData);
        } catch (Throwable e) {
            handled = true;
            XrdRuntimeException cex = XrdRuntimeException.systemException(e).withPrefix(SERVER_CLIENTPROXY_X);
            log.error("Request processing error ({})", cex.getIdentifier(), e);
            opMonitoringDataHelper.updateOpMonitoringSoapFault(opMonitoringData, cex);
            failure(request, response, callback, cex, opMonitoringData);
        } finally {
            if (handled) {
                opMonitoringDataHelper.updateOpMonitoringResponseOutTs(opMonitoringData);
                opMonitoringBuffer.store(opMonitoringData);
                ProxyMessageUtils.logPerformanceEnd(start);
            }
        }
        return handled;
    }

    private void failure(Request request, Response response, Callback callback,
                         XrdRuntimeException e, OpMonitoringData opMonitoringData) throws IOException {
        opMonitoringDataHelper.updateOpMonitoringResponseOutTs(opMonitoringData);
        sendErrorResponse(request, response, callback, e);
    }

    private void failure(Response response, Callback callback, XrdRuntimeHttpException e,
                         OpMonitoringData opMonitoringData) {
        opMonitoringDataHelper.updateOpMonitoringResponseOutTs(opMonitoringData);
        sendPlainTextErrorResponse(response, callback, e.getHttpStatus().get().getCode(), e.getDetails());
    }

    private static boolean isPostRequest(RequestWrapper request) {
        return request.getMethod().equalsIgnoreCase("POST");
    }

    private void verifyCanProcess(RequestWrapper request) {
        if (!isPostRequest(request)) {
            throw XrdRuntimeException.systemException(INVALID_HTTP_METHOD)
                    .details("Must use POST request method instead of %s".formatted(request.getMethod()))
                    .origin(ErrorOrigin.CLIENT)
                    .build();
        }

        globalConfProvider.verifyValidity();

        if (!proxyProperties.sslEnabled()) {
            return;
        }

        AuthKey authKey = keyConfProvider.getAuthKey();
        if (authKey.certChain() == null) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                    "Security server has no valid authentication certificate");
        }
    }
}
