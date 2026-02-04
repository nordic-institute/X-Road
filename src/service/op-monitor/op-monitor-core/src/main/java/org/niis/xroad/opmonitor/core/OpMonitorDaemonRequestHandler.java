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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.StoreOpMonitoringDataResponse;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVER_PROXY_OPMONITOR_X;
import static ee.ria.xroad.common.util.JettyUtils.getContentType;
import static ee.ria.xroad.common.util.JettyUtils.setContentLength;
import static ee.ria.xroad.common.util.JettyUtils.setContentType;
import static org.eclipse.jetty.http.MimeTypes.Type.APPLICATION_JSON_UTF_8;
import static org.eclipse.jetty.server.Request.getRemoteAddr;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CONTENT_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_HTTP_METHOD;
import static org.niis.xroad.opmonitor.api.OpMonitoringDaemonEndpoints.QUERY_DATA_PATH;
import static org.niis.xroad.opmonitor.api.OpMonitoringDaemonEndpoints.STORE_DATA_PATH;

/**
 * Query handler for operational data and health data requests.
 */
@Slf4j
@RequiredArgsConstructor
class OpMonitorDaemonRequestHandler extends HandlerBase {

    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();

    private static final byte[] OK_RESPONSE_BYTES = getOkResponseBytes();

    private final OpMonitorProperties opMonitorProperties;
    private final GlobalConfProvider globalConfProvider;
    private final MetricRegistry healthMetricRegistry;
    private final OperationalDataRecordManager operationalDataRecordManager;
    private final HealthDataMetrics healthDataMetrics;

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws IOException {
        final var target = request.getHttpURI().getPath();
        try {
            if (STORE_DATA_PATH.equals(target)) {
                handleStoreRequest(request, response, callback);
            } else if (QUERY_DATA_PATH.equals(target)) {
                handleQueryRequest(request, response, callback);
            } else {
                handleBadRequest(response);
            }
        } finally {
            callback.succeeded();
        }
        return true;
    }

    // Queries for operational data are SOAP messages. Errors must be
    // reported via SOAP faults, not plain HTTP responses.
    private void handleQueryRequest(Request request,
                                    Response response,
                                    Callback callback) throws IOException {
        try {
            if (!isPostRequest(request)) {
                throw XrdRuntimeException.systemException(INVALID_HTTP_METHOD, invalidMethodError(request));
            }

            String contentType = MimeUtils.getBaseContentType(
                    getContentType(request));

            if (!MimeTypes.TEXT_XML.equalsIgnoreCase(contentType)) {
                throw XrdRuntimeException.systemException(INVALID_CONTENT_TYPE,
                        invalidContentTypeError(request,
                                MimeTypes.TEXT_XML));
            }

            log.info("Received query request from {}", getRemoteAddr(request));

            new QueryRequestProcessor(globalConfProvider, RequestWrapper.of(request), ResponseWrapper.of(response),
                    healthMetricRegistry, operationalDataRecordManager, opMonitorProperties).process();
        } catch (Throwable t) { // We want to catch serious errors as well
            log.error("Error while handling query request", t);

            XrdRuntimeException ex = XrdRuntimeException.systemException(t).withPrefix(SERVER_SERVER_PROXY_OPMONITOR_X);
            sendErrorResponse(request, response, callback, ex);
        }
    }

    // Requests to store data are HTTP requests with JSON payload. Errors
    // must be reported in JSON format.
    private void handleStoreRequest(Request request,
                                    Response response,
                                    Callback callback) throws IOException {
        try {
            if (!isPostRequest(request)) {
                throw XrdRuntimeException.systemInternalError(invalidMethodError(request));
            }

            String contentType = MimeUtils.getBaseContentType(
                    getContentType(request));

            if (!MimeTypes.JSON.equalsIgnoreCase(contentType)) {
                throw XrdRuntimeException.systemInternalError(invalidContentTypeError(request,
                        MimeTypes.JSON));
            }

            log.info("Received store request from {}", getRemoteAddr(request));

            new StoreRequestProcessor(
                    RequestWrapper.of(request), healthMetricRegistry, healthDataMetrics, operationalDataRecordManager).process();
        } catch (Throwable t) { // We want to catch serious errors as well
            log.error("Error while handling data store request", t);

            sendJsonErrorResponse(response, callback, t.getMessage());

            return;
        }

        sendJsonOkResponse(response, callback);
    }

    private static void handleBadRequest(Response response) {
        response.setStatus(HttpStatus.BAD_REQUEST_400);
    }

    private static boolean isPostRequest(Request request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    private static String invalidMethodError(Request request) {
        return "Must use POST request method instead of "
                + request.getMethod();
    }

    private static String invalidContentTypeError(Request request,
                                                  String expectedContentType) {
        return "Invalid content type " + getContentType(request)
                + ", expecting " + expectedContentType;
    }

    private static void sendJsonErrorResponse(Response response,
                                              Callback callback,
                                              String errorMessage) throws IOException {
        byte[] messageBytes = OBJECT_WRITER.writeValueAsString(
                new StoreOpMonitoringDataResponse(errorMessage)).getBytes(
                MimeUtils.UTF8);

        sendJsonResponse(response, callback, messageBytes);
    }

    private static void sendJsonOkResponse(Response response, Callback callback) {
        sendJsonResponse(response, callback, OK_RESPONSE_BYTES);
    }

    private static byte[] getOkResponseBytes() {
        try {
            return OBJECT_WRITER.writeValueAsString(new StoreOpMonitoringDataResponse()).getBytes(MimeUtils.UTF8);
        } catch (JsonProcessingException | UnsupportedEncodingException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private static void sendJsonResponse(Response response,
                                         Callback callback,
                                         byte[] messageBytes) {
        response.setStatus(HttpStatus.OK_200);
        setContentType(response, APPLICATION_JSON_UTF_8);
        setContentLength(response, messageBytes.length);
        response.write(true, ByteBuffer.wrap(messageBytes), callback);
    }
}
