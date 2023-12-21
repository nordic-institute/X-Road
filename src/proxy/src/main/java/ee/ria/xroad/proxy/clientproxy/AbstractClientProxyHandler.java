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
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.PerformanceLogger;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SecurityServerType.CLIENT;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

/**
 * Base class for client proxy handlers.
 */
@Slf4j
@RequiredArgsConstructor
abstract class AbstractClientProxyHandler extends HandlerBase {

    private static final String START_TIME_ATTRIBUTE = AbstractClientProxyHandler.class.getName() + ".START_TIME";
    protected final HttpClient client;

    protected final boolean storeOpMonitoringData;
    private final long idleTimeout = SystemProperties.getClientProxyConnectorMaxIdleTime();

    abstract MessageProcessorBase createRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response,
            OpMonitoringData opMonitoringData) throws Exception;

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (baseRequest.isHandled()) {
            // If some handler already processed the request, we do nothing.
            return;
        }

        boolean handled = false;

        long start = logPerformanceBegin(request);
        OpMonitoringData opMonitoringData = storeOpMonitoringData ? new OpMonitoringData(CLIENT, start) : null;
        MessageProcessorBase processor = null;

        try {
            processor = createRequestProcessor(target, request, response, opMonitoringData);

            if (processor != null) {
                baseRequest.getHttpChannel().setIdleTimeout(idleTimeout);
                handled = true;
                processor.process();
                success(processor, start, opMonitoringData);

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

            failure(processor, request, response, e, opMonitoringData);
        } catch (CodedExceptionWithHttpStatus e) {
            handled = true;

            // No need to log faultDetail hence not sent to client.
            log.error("Request processing error", e);

            // Respond with HTTP status code and plain text error message instead of SOAP fault message.
            // No need to update operational monitoring fields here either.

            failure(response, e, opMonitoringData);
        } catch (Throwable e) { // We want to catch serious errors as well
            handled = true;

            // All the other exceptions get prefix Server.ClientProxy...
            CodedException cex = translateWithPrefix(SERVER_CLIENTPROXY_X, e);

            log.error("Request processing error ({})", cex.getFaultDetail(), e);

            updateOpMonitoringSoapFault(opMonitoringData, cex);

            failure(processor, request, response, cex, opMonitoringData);
        } finally {
            baseRequest.setHandled(handled);

            if (handled) {
                if (storeOpMonitoringData) {
                    updateOpMonitoringResponseOutTs(opMonitoringData);

                    OpMonitoring.store(opMonitoringData);
                }

                logPerformanceEnd(start);
            }
        }
    }

    private static void success(MessageProcessorBase processor, long start, OpMonitoringData opMonitoringData) {
        final boolean success = processor.verifyMessageExchangeSucceeded();

        updateOpMonitoringSucceeded(opMonitoringData, success);
    }

    protected void failure(MessageProcessorBase processor, HttpServletRequest request, HttpServletResponse response,
                           CodedException e, OpMonitoringData opMonitoringData) throws IOException {

        updateOpMonitoringResponseOutTs(opMonitoringData);

        sendErrorResponse(request, response, e);
    }

    protected void failure(HttpServletResponse response, CodedExceptionWithHttpStatus e,
            OpMonitoringData opMonitoringData) throws IOException {

        updateOpMonitoringResponseOutTs(opMonitoringData);

        sendPlainTextErrorResponse(response, e.getStatus(), e.getFaultString());
    }

    static boolean isGetRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("GET");
    }

    static boolean isPostRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("POST");
    }

    static IsAuthenticationData getIsAuthenticationData(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

        return new IsAuthenticationData(certs != null && certs.length != 0 ? certs[0] : null,
                !"https".equals(request.getScheme())); // if not HTTPS, it's plaintext
    }

    private static long logPerformanceBegin(HttpServletRequest request) {
        long start;
        Object obj = request.getAttribute(START_TIME_ATTRIBUTE);
        if (obj instanceof Long) {
            start = (Long) obj;
        } else {
            start = PerformanceLogger.log(log, "Received request from " + request.getRemoteAddr());
            log.info("Received request from {}", request.getRemoteAddr());
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
