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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataResponse;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;

import java.io.IOException;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVER_PROXY_OPMONITOR_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CONTENT_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonEndpoints.QUERY_DATA_PATH;
import static ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonEndpoints.STORE_DATA_PATH;

/**
 * Query handler for operational data and health data requests.
 */
@Slf4j
class OpMonitorDaemonRequestHandler extends HandlerBase {

    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();

    private static final byte[] OK_RESPONSE_BYTES = getOkResponseBytes();

    private final MetricRegistry healthMetricRegistry;

    OpMonitorDaemonRequestHandler(MetricRegistry healthMetricRegistry) {
        this.healthMetricRegistry = healthMetricRegistry;
    }

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            if (STORE_DATA_PATH.equals(target)) {
                handleStoreRequest(request, response);
            } else if (QUERY_DATA_PATH.equals(target)) {
                handleQueryRequest(request, response);
            } else {
                handleBadRequest(response);
            }
        } finally {
            baseRequest.setHandled(true);
        }
    }

    // Queries for operational data are SOAP messages. Errors must be
    // reported via SOAP faults, not plain HTTP responses.
    private void handleQueryRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            if (!isPostRequest(request)) {
                throw new CodedException(X_INVALID_HTTP_METHOD,
                        invalidMethodError(request));
            }

            String contentType = MimeUtils.getBaseContentType(
                    request.getContentType());

            if (!MimeTypes.TEXT_XML.equalsIgnoreCase(contentType)) {
                throw new CodedException(X_INVALID_CONTENT_TYPE,
                        invalidContentTypeError(request,
                                MimeTypes.TEXT_XML));
            }

            log.info("Received query request from {}", request.getRemoteAddr());

            new QueryRequestProcessor(request, response,
                    healthMetricRegistry).process();
        } catch (Throwable t) { // We want to catch serious errors as well
            log.error("Error while handling query request", t);

            sendErrorResponse(request, response, translateWithPrefix(
                    SERVER_SERVER_PROXY_OPMONITOR_X, t));
        }
    }

    // Requests to store data are HTTP requests with JSON payload. Errors
    // must be reported in JSON format.
    private void handleStoreRequest(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            if (!isPostRequest(request)) {
                throw new RuntimeException(invalidMethodError(request));
            }

            String contentType = MimeUtils.getBaseContentType(
                    request.getContentType());

            if (!MimeTypes.JSON.equalsIgnoreCase(contentType)) {
                throw new RuntimeException(invalidContentTypeError(request,
                        MimeTypes.JSON));
            }

            log.info("Received store request from {}", request.getRemoteAddr());

            new StoreRequestProcessor(
                    request, healthMetricRegistry).process();
        } catch (Throwable t) { // We want to catch serious errors as well
            log.error("Error while handling data store request", t);

            sendJsonErrorResponse(response, t.getMessage());

            return;
        }

        sendJsonOkResponse(response);
    }

    private static void handleBadRequest(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    private static boolean isPostRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    private static String invalidMethodError(HttpServletRequest request) {
        return "Must use POST request method instead of "
                + request.getMethod();
    }

    private static String invalidContentTypeError(HttpServletRequest request,
            String expectedContentType) {
        return "Invalid content type " + request.getContentType()
                + ", expecting " + expectedContentType;
    }

    private static void sendJsonErrorResponse(HttpServletResponse response,
            String errorMessage) throws IOException {
        byte[] messageBytes = OBJECT_WRITER.writeValueAsString(
                new StoreOpMonitoringDataResponse(errorMessage)).getBytes(
                MimeUtils.UTF8);

        sendJsonResponse(response, messageBytes);
    }

    private static void sendJsonOkResponse(HttpServletResponse response)
            throws IOException {
        sendJsonResponse(response, OK_RESPONSE_BYTES);
    }

    @SneakyThrows
    private static byte[] getOkResponseBytes() {
        return OBJECT_WRITER.writeValueAsString(new StoreOpMonitoringDataResponse()).getBytes(
                MimeUtils.UTF8);
    }

    private static void sendJsonResponse(HttpServletResponse response,
            byte[] messageBytes) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MimeTypes.JSON);
        response.setContentLength(messageBytes.length);
        response.setCharacterEncoding(MimeUtils.UTF8);
        response.getOutputStream().write(messageBytes);
    }
}
