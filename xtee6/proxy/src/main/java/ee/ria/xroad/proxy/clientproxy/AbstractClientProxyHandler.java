/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.PerformanceLogger;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

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

    protected final HttpClient client;

    protected final boolean storeOpMonitoringData;

    abstract MessageProcessorBase createRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response,
            OpMonitoringData opMonitoringData) throws Exception;

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
        if (baseRequest.isHandled()) {
            // If some handler already processed the request, we do nothing.
            return;
        }

        boolean handled = false;
        OpMonitoringData opMonitoringData = storeOpMonitoringData
                ? new OpMonitoringData(CLIENT, getEpochMillisecond()) : null;

        long start = PerformanceLogger.log(log, "Received request from "
                + request.getRemoteAddr());
        log.info("Received request from {}", request.getRemoteAddr());

        MessageProcessorBase processor = null;

        try {
            processor = createRequestProcessor(target, request, response,
                    opMonitoringData);

            if (processor != null) {
                handled = true;
                start = logPerformanceBegin(request);
                processor.process();
                success(processor, start, opMonitoringData);

                if (log.isTraceEnabled()) {
                    log.info("Request successfully handled ({} ms)",
                            System.currentTimeMillis() - start);
                } else {
                    log.info("Request successfully handled");
                }
            }
        } catch (CodedException.Fault | ClientException e) {
            handled = true;

            String errorMessage = e instanceof ClientException
                    ? "Request processing error (" + e.getFaultDetail() + ")"
                    : "Request processing error";

            log.error(errorMessage, e);

            updateOpMonitoringSoapFault(opMonitoringData, e);

            // Exceptions caused by incoming message and exceptions
            // derived from faults sent by serverproxy already contain
            // full error code. Thus, we must not attach additional
            // error code prefixes to them.

            failure(processor, response, e);
        } catch (CodedExceptionWithHttpStatus e) {
            handled = true;

            // No need to log faultDetail hence not sent to client.
            log.error("Request processing error", e);

            // Respond with HTTP status code and plain text error message
            // instead of SOAP fault message. No need to update operational
            // monitoring fields here either.

            failure(response, e);
        } catch (Throwable e) { // We want to catch serious errors as well
            handled = true;

            // All the other exceptions get prefix Server.ClientProxy...
            CodedException cex = translateWithPrefix(SERVER_CLIENTPROXY_X, e);

            updateOpMonitoringSoapFault(opMonitoringData, cex);

            log.error("Request processing error ({})", cex.getFaultDetail(), e);

            failure(processor, response, cex);
        } finally {
            baseRequest.setHandled(handled);

            if (handled) {
                if (storeOpMonitoringData) {
                    opMonitoringData.setResponseOutTs(getEpochMillisecond());
                    OpMonitoring.store(opMonitoringData);
                }

                logPerformanceEnd(start);
            }
        }
    }

    protected static void success(MessageProcessorBase processor, long start,
            OpMonitoringData opMonitoringData) {
        updateOpMonitoringSucceeded(opMonitoringData);

        MonitorAgent.success(
            processor.createRequestMessageInfo(),
            new Date(start),
            new Date()
        );
    }

    protected void failure(MessageProcessorBase processor,
            HttpServletResponse response, CodedException e)
            throws IOException {
        MessageInfo info = processor != null
                ? processor.createRequestMessageInfo()
                : null;

        MonitorAgent.failure(info, e.getFaultCode(), e.getFaultString());

        sendErrorResponse(response, e);
    }

    @Override
    protected void failure(HttpServletResponse response, CodedException e)
            throws IOException {
        MonitorAgent.failure(null, e.getFaultCode(), e.getFaultString());

        sendErrorResponse(response, e);
    }

    protected void failure(HttpServletResponse response,
            CodedExceptionWithHttpStatus e) throws IOException {
        MonitorAgent.failure(null,
            e.withPrefix(SERVER_CLIENTPROXY_X).getFaultCode(),
            e.getFaultString()
        );

        sendPlainTextErrorResponse(response, e.getStatus(), e.getFaultString());
    }

    protected static boolean isGetRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("GET");
    }

    protected static boolean isPostRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("POST");
    }

    protected static final String stripSlash(String str) {
        return str != null && str.startsWith("/")
                ? str.substring(1) : str; // Strip '/'
    }

    protected static IsAuthenticationData getIsAuthenticationData(
            HttpServletRequest request) {
        X509Certificate[] certs =
                (X509Certificate[]) request.getAttribute(
                        "javax.servlet.request.X509Certificate");
        return new IsAuthenticationData(
            certs != null && certs.length != 0 ? certs[0] : null,
            !"https".equals(request.getScheme()) // if not HTTPS, it's plaintext
        );
    }

    private static long logPerformanceBegin(HttpServletRequest request) {
        long start = PerformanceLogger.log(log, "Received request from "
                + request.getRemoteAddr());
        log.info("Received request from {}", request.getRemoteAddr());

        return start;
    }

    private static void logPerformanceEnd(long start) {
        PerformanceLogger.log(log, start, "Request handled");
    }

    private static void updateOpMonitoringSoapFault(
            OpMonitoringData opMonitoringData, CodedException e) {
        if (opMonitoringData != null) {
            opMonitoringData.setSoapFault(e);
        }
    }

    private static void updateOpMonitoringSucceeded(
            OpMonitoringData opMonitoringData) {
        if (opMonitoringData != null) {
            opMonitoringData.setSucceeded(true);
        }
    }
}
