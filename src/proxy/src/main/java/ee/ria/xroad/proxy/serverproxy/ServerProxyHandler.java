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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.PerformanceLogger;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoring;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SecurityServerType.PRODUCER;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_MESSAGE_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.VALUE_MESSAGE_TYPE_REST;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

@Slf4j
class ServerProxyHandler extends HandlerBase {

    private final HttpClient client;
    private final HttpClient opMonitorClient;
    private final long idleTimeout = SystemProperties.getServerProxyConnectorMaxIdleTime();

    ServerProxyHandler(HttpClient client, HttpClient opMonitorClient) {
        this.client = client;
        this.opMonitorClient = opMonitorClient;
    }

    @Override
    public void handle(String target, Request baseRequest, final HttpServletRequest request,
            final HttpServletResponse response) throws IOException, ServletException {
        OpMonitoringData opMonitoringData = new OpMonitoringData(PRODUCER, getEpochMillisecond());

        long start = PerformanceLogger.log(log, "Received request from " + request.getRemoteAddr());

        if (!SystemProperties.isServerProxySupportClientsPooledConnections()) {
            // if the header is added, the connections are closed and cannot be reused on the client side
            response.addHeader("Connection", "close");
        }

        try {
            if (!request.getMethod().equalsIgnoreCase("POST")) {
                throw new CodedException(X_INVALID_HTTP_METHOD, "Must use POST request method instead of %s",
                        request.getMethod());
            }

            GlobalConf.verifyValidity();

            ClientProxyVersionVerifier.check(request);
            baseRequest.getHttpChannel().setIdleTimeout(idleTimeout);
            final MessageProcessorBase processor = createRequestProcessor(request, response, opMonitoringData);
            processor.process();
        } catch (Throwable e) { // We want to catch serious errors as well
            CodedException cex = translateWithPrefix(SERVER_SERVERPROXY_X, e);

            log.error("Request processing error ({})", cex.getFaultDetail(), e);

            opMonitoringData.setFaultCodeAndString(cex);
            opMonitoringData.setResponseOutTs(getEpochMillisecond(), false);

            failure(request, response, cex);
        } finally {
            baseRequest.setHandled(true);

            opMonitoringData.setResponseOutTs(getEpochMillisecond(), false);
            OpMonitoring.store(opMonitoringData);

            PerformanceLogger.log(log, start, "Request handled");
        }
    }

    private MessageProcessorBase createRequestProcessor(HttpServletRequest request, HttpServletResponse response,
            OpMonitoringData opMonitoringData) {

        if (VALUE_MESSAGE_TYPE_REST.equals(request.getHeader(HEADER_MESSAGE_TYPE))) {
            return new ServerRestMessageProcessor(request, response, client, getClientSslCertChain(request),
                    opMonitoringData);
        } else {
            return new ServerMessageProcessor(request, response, client, getClientSslCertChain(request),
                    opMonitorClient, opMonitoringData);
        }
    }

    @Override
    protected void failure(HttpServletRequest request, HttpServletResponse response, CodedException e)
            throws IOException {
        sendErrorResponse(request, response, e);
    }

    private static X509Certificate[] getClientSslCertChain(HttpServletRequest request) {
        Object attribute = request.getAttribute("jakarta.servlet.request.X509Certificate");

        if (attribute != null) {
            return (X509Certificate[]) attribute;
        } else {
            return null;
        }
    }
}
