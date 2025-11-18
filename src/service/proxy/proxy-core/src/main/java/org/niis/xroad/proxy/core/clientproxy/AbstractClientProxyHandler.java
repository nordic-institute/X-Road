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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.exception.XrdRuntimeHttpException;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.util.MessageProcessorBase;
import org.niis.xroad.proxy.core.util.MessageProcessorFactory;
import org.niis.xroad.proxy.core.util.PerformanceLogger;

import java.io.IOException;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.eclipse.jetty.server.Request.getRemoteAddr;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;

/**
 * Base class for client proxy handlers.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractClientProxyHandler extends HandlerBase {

    private static final String DEFAULT_ERROR_MESSAGE = "Request processing error";
    private static final String START_TIME_ATTRIBUTE = AbstractClientProxyHandler.class.getName() + ".START_TIME";

    protected final MessageProcessorFactory messageProcessorFactory;
    protected final boolean storeOpMonitoringData;
    protected final OpMonitoringBuffer opMonitoringBuffer;

    protected abstract MessageProcessorBase createRequestProcessor(RequestWrapper request,
                                                                   ResponseWrapper response,
                                                                   OpMonitoringData opMonitoringData) throws IOException;

    @Override
    @WithSpan
    @SuppressWarnings({"java:S3776"}) //TODO XRDDEV-2962 cognitive complexity should drop after refactoring
    public boolean handle(Request request, Response response, Callback callback) throws IOException {
        boolean handled = false;

        long start = logPerformanceBegin(request);
        OpMonitoringData opMonitoringData = storeOpMonitoringData ? new OpMonitoringData(CLIENT, start) : null;

        try {
            MessageProcessorBase processor = createRequestProcessor(
                    RequestWrapper.of(request),
                    ResponseWrapper.of(response),
                    opMonitoringData);

            if (processor != null) {
                handled = true;
                processor.process();
                success(processor, opMonitoringData);
                callback.succeeded();
                if (log.isTraceEnabled()) {
                    log.info("Request successfully handled ({} ms)", System.currentTimeMillis() - start);
                } else {
                    log.info("Request successfully handled");
                }
            }
        } catch (XrdRuntimeHttpException e) {
            handled = true;

            // No need to log faultDetail hence not sent to client.
            log.error(DEFAULT_ERROR_MESSAGE, e);

            // Respond with HTTP status code and plain text error message instead of SOAP fault message.
            // No need to update operational monitoring fields here either.

            failure(response, callback, e, opMonitoringData);
        } catch (XrdRuntimeException e) {
            handled = true;

            String errorMessage;
            XrdRuntimeException exception = e;
            if (!e.originatesFrom(ErrorOrigin.CLIENT)) {
                errorMessage = "Request processing error (" + e.getFaultDetail() + ")";
                exception = e.withPrefix(SERVER_CLIENTPROXY_X);
            } else {
                errorMessage = "Request processing error (" + e.getDetails() + ")";
            }

            log.error(errorMessage, exception);

            updateOpMonitoringSoapFault(opMonitoringData, exception);

            // Exceptions caused by incoming message and exceptions derived from faults sent by serverproxy already
            // contain full error code. Thus, we must not attach additional error code prefixes to them.

            failure(request, response, callback, exception, opMonitoringData);
        } catch (CodedException.Fault e) {
            handled = true;

            log.error(DEFAULT_ERROR_MESSAGE, e);

            updateOpMonitoringSoapFault(opMonitoringData, e);

            // Exceptions caused by incoming message and exceptions derived from faults sent by serverproxy already
            // contain full error code. Thus, we must not attach additional error code prefixes to them.

            failure(request, response, callback, e, opMonitoringData);
        } catch (Throwable e) { // We want to catch serious errors as well
            handled = true;

            // All the other exceptions get prefix Server.ClientProxy...
            XrdRuntimeException cex = XrdRuntimeException.systemException(e).withPrefix(SERVER_CLIENTPROXY_X);

            log.error("Request processing error ({})", cex.getFaultDetail(), e);

            updateOpMonitoringSoapFault(opMonitoringData, cex);

            failure(request, response, callback, cex, opMonitoringData);
        } finally {
            if (handled) {
                if (storeOpMonitoringData) {
                    updateOpMonitoringResponseOutTs(opMonitoringData);
                    opMonitoringBuffer.store(opMonitoringData);
                }
                logPerformanceEnd(start);
            }
        }
        return handled;
    }

    private static void success(MessageProcessorBase processor, OpMonitoringData opMonitoringData) {
        final boolean success = processor.verifyMessageExchangeSucceeded();

        updateOpMonitoringSucceeded(opMonitoringData, success);
    }

    protected void failure(Request request, Response response, Callback callback,
                           CodedException e, OpMonitoringData opMonitoringData) throws IOException {

        updateOpMonitoringResponseOutTs(opMonitoringData);

        sendErrorResponse(request, response, callback, e);
    }

    protected void failure(Response response, Callback callback, XrdRuntimeHttpException e,
                           OpMonitoringData opMonitoringData) {

        updateOpMonitoringResponseOutTs(opMonitoringData);

        sendPlainTextErrorResponse(response, callback, e.getHttpStatus().get().getCode(), e.getFaultString());
    }

    protected boolean isGetRequest(RequestWrapper request) {
        return request.getMethod().equalsIgnoreCase("GET");
    }

    static boolean isPostRequest(RequestWrapper request) {
        return request.getMethod().equalsIgnoreCase("POST");
    }

    private static long logPerformanceBegin(Request request) {
        long start;
        Object obj = request.getAttribute(START_TIME_ATTRIBUTE);
        if (obj instanceof Long l) {
            start = l;
        } else {
            start = PerformanceLogger.log(log, "Received request from " + getRemoteAddr(request));
            log.info("Received request from {}", getRemoteAddr(request));
            request.setAttribute(START_TIME_ATTRIBUTE, start);
        }
        return start;
    }

    private static void logPerformanceEnd(long start) {
        PerformanceLogger.log(log, start, "Request handled");
    }

    private static void updateOpMonitoringResponseOutTs(OpMonitoringData opMonitoringData) {
        if (opMonitoringData != null) {
            opMonitoringData.setResponseOutTs(getEpochMillisecond(), false);
        }
    }

    private static void updateOpMonitoringSoapFault(OpMonitoringData opMonitoringData, CodedException e) {
        if (opMonitoringData != null) {
            opMonitoringData.setFaultCodeAndString(e);
        }
    }

    private static void updateOpMonitoringSucceeded(OpMonitoringData opMonitoringData, boolean success) {
        if (opMonitoringData != null) {
            opMonitoringData.setSucceeded(success);
        }
    }
}
