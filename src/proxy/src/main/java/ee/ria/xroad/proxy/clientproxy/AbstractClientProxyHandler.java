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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.PerformanceLogger;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SecurityServerType.CLIENT;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.eclipse.jetty.server.Request.getRemoteAddr;

/**
 * Base class for client proxy handlers.
 */
@Slf4j
@RequiredArgsConstructor
abstract class AbstractClientProxyHandler extends HandlerBase {

    private static final String START_TIME_ATTRIBUTE = AbstractClientProxyHandler.class.getName() + ".START_TIME";
    protected final HttpClient client;

    protected final boolean storeOpMonitoringData;

    abstract MessageProcessorBase createRequestProcessor(Request request, Response response,
                                                         OpMonitoringData opMonitoringData) throws Exception;

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        boolean handled = false;

        long start = logPerformanceBegin(request);
        OpMonitoringData opMonitoringData = storeOpMonitoringData ? new OpMonitoringData(CLIENT, start) : null;
        MessageProcessorBase processor = null;

        try {
            processor = createRequestProcessor(request, response, opMonitoringData);

            if (processor != null) {
                handled = true;
                processor.process();
                success(processor, start, opMonitoringData);
                callback.succeeded();
                if (log.isTraceEnabled()) {
                    log.info("Request successfully handled ({} ms)", System.currentTimeMillis() - start);
                } else {
                    log.info("Request successfully handled");
                }
            }
        } catch (CodedException.Fault | ClientException e) {
            handled = true;

            String errorMessage = e instanceof ClientException
                    ? "Request processing error (" + e.getFaultDetail() + ")" : "Request processing error";

            log.error(errorMessage, e);

            updateOpMonitoringSoapFault(opMonitoringData, e);

            // Exceptions caused by incoming message and exceptions derived from faults sent by serverproxy already
            // contain full error code. Thus, we must not attach additional error code prefixes to them.

            failure(processor, request, response, callback, e, opMonitoringData);
            callback.failed(e);
        } catch (CodedExceptionWithHttpStatus e) {
            handled = true;

            // No need to log faultDetail hence not sent to client.
            log.error("Request processing error", e);

            // Respond with HTTP status code and plain text error message instead of SOAP fault message.
            // No need to update operational monitoring fields here either.

            failure(response, callback, e, opMonitoringData);
            callback.failed(e);
        } catch (Throwable e) { // We want to catch serious errors as well
            handled = true;

            // All the other exceptions get prefix Server.ClientProxy...
            CodedException cex = translateWithPrefix(SERVER_CLIENTPROXY_X, e);

            log.error("Request processing error ({})", cex.getFaultDetail(), e);

            updateOpMonitoringSoapFault(opMonitoringData, cex);

            failure(processor, request, response, callback, cex, opMonitoringData);
            callback.failed(e);
        } finally {
            if (handled) {
                if (storeOpMonitoringData) {
                    updateOpMonitoringResponseOutTs(opMonitoringData);

                    OpMonitoring.store(opMonitoringData);
                }

                logPerformanceEnd(start);
            }
        }
        return handled;
    }

    private static void success(MessageProcessorBase processor, long start, OpMonitoringData opMonitoringData) {
        final boolean success = processor.verifyMessageExchangeSucceeded();

        updateOpMonitoringSucceeded(opMonitoringData, success);
    }

    protected void failure(MessageProcessorBase processor, Request request, Response response, Callback callback,
                           CodedException e, OpMonitoringData opMonitoringData) throws IOException {

        updateOpMonitoringResponseOutTs(opMonitoringData);

        sendErrorResponse(request, response, callback, e);
    }

    protected void failure(Response response, Callback callback, CodedExceptionWithHttpStatus e,
                           OpMonitoringData opMonitoringData) throws IOException {

        updateOpMonitoringResponseOutTs(opMonitoringData);

        sendPlainTextErrorResponse(response, callback, e.getStatus(), e.getFaultString());
    }

    static boolean isGetRequest(Request request) {
        return request.getMethod().equalsIgnoreCase("GET");
    }

    static boolean isPostRequest(Request request) {
        return request.getMethod().equalsIgnoreCase("POST");
    }

    static IsAuthenticationData getIsAuthenticationData(Request request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

        return new IsAuthenticationData(certs != null && certs.length != 0 ? certs[0] : null,
                !"https".equals(request.getHttpURI().getScheme())); // if not HTTPS, it's plaintext
    }

    private static long logPerformanceBegin(Request request) {
        long start;
        Object obj = request.getAttribute(START_TIME_ATTRIBUTE);
        if (obj instanceof Long) {
            start = (Long) obj;
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
