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

import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.XmlUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpStatus;
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
import org.niis.xroad.proxy.core.util.RestRequestContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.util.JettyUtils.getTarget;
import static ee.ria.xroad.common.util.JettyUtils.setContentType;
import static org.eclipse.jetty.io.Content.Sink.asOutputStream;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;

/**
 * Handles client REST messages by delegating to the CDI singleton {@link ClientRestMessageProcessor}.
 * This handler must be the last handler in the handler collection, since it will not pass handling
 * of the request to the next handler if it cannot process the request itself.
 */
@Slf4j
@RequiredArgsConstructor
public class ClientRestMessageHandler extends HandlerBase {

    private static final String TEXT_XML = "text/xml";
    private static final String APPLICATION_XML = "application/xml";
    private static final String TEXT_ANY = "text/*";
    private static final String APPLICATION_JSON = "application/json";
    private static final List<String> XML_TYPES = Arrays.asList(TEXT_XML, APPLICATION_XML, TEXT_ANY);

    private final ClientRestMessageProcessor clientRestMessageProcessor;
    private final ProxyProperties proxyProperties;
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final OpMonitoringBuffer opMonitoringBuffer;
    private final OpMonitoringDataHelper opMonitoringDataHelper;

    @Override
    @WithSpan
    @SuppressWarnings({"java:S3776"})
    public boolean handle(Request request, Response response, Callback callback) throws IOException {
        boolean handled = false;
        long start = ProxyMessageUtils.logPerformanceBegin(request);
        OpMonitoringData opMonitoringData = new OpMonitoringData(CLIENT, start);

        try {
            final var target = getTarget(RequestWrapper.of(request));
            if (target != null && target.startsWith("/r" + RestMessage.PROTOCOL_VERSION + "/")) {
                verifyCanProcess();
                var ctx = new RestRequestContext(
                        RequestWrapper.of(request), ResponseWrapper.of(response), opMonitoringData);
                handled = true;
                boolean success = clientRestMessageProcessor.process(ctx);
                opMonitoringData.setSucceeded(success);
                callback.succeeded();
                if (log.isTraceEnabled()) {
                    log.info("Request successfully handled ({} ms)", System.currentTimeMillis() - start);
                }
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

    private void verifyCanProcess() {
        globalConfProvider.verifyValidity();

        if (!proxyProperties.sslEnabled()) {
            return;
        }

        AuthKey authKey = keyConfProvider.getAuthKey();
        if (authKey.certChain() == null) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED)
                    .details("Security server has no authentication certificate")
                    .build();
        }
    }

    @Override
    public void sendErrorResponse(Request request,
                                  Response response,
                                  Callback callback,
                                  XrdRuntimeException ex) throws IOException {
        if (ex.getErrorCode().startsWith("server.")) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
        } else {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
        }
        response.getHeaders().put("X-Road-Error", ex.getErrorCode());

        final String responseContentType = decideErrorResponseContentType(request.getHeaders().getValues("Accept"));
        setContentType(response, responseContentType, MimeUtils.UTF8);
        if (XML_TYPES.contains(responseContentType)) {
            try (var responseOut = asOutputStream(response)) {
                Document doc = XmlUtils.newDocumentBuilder(false).newDocument();
                Element errorRootElement = doc.createElement("error");
                doc.appendChild(errorRootElement);
                Element typeElement = doc.createElement("type");
                typeElement.appendChild(doc.createTextNode(ex.getErrorCode()));
                errorRootElement.appendChild(typeElement);
                Element messageElement = doc.createElement("message");
                messageElement.appendChild(doc.createTextNode(ex.getDetails()));
                errorRootElement.appendChild(messageElement);
                Element detailElement = doc.createElement("detail");
                detailElement.appendChild(doc.createTextNode(ex.getIdentifier()));
                errorRootElement.appendChild(detailElement);
                responseOut.write(XmlUtils.prettyPrintXml(doc, "UTF-8", 0).getBytes());
            } catch (Exception e) {
                log.error("Unable to generate XML document");
            } finally {
                callback.failed(ex);
            }
        } else {
            try (JsonGenerator jsonGenerator = JsonUtils.getObjectWriter()
                    .getFactory().createGenerator(new PrintWriter(asOutputStream(response)))) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("type", ex.getErrorCode());
                jsonGenerator.writeStringField("message", ex.getDetails());
                jsonGenerator.writeStringField("detail", ex.getIdentifier());
                jsonGenerator.writeEndObject();
            } finally {
                callback.succeeded();
            }
        }
    }

    private static String decideErrorResponseContentType(Enumeration<String> acceptHeaderValue) {
        return Collections.list(acceptHeaderValue).stream()
                .flatMap(h -> Arrays.stream(h.split(",")))
                .map(s -> s.split(";", 2)[0].trim().toLowerCase())
                .filter(XML_TYPES::contains)
                .findAny()
                .map(ClientRestMessageHandler::mapTextToXml)
                .orElse(APPLICATION_JSON);
    }

    private static String mapTextToXml(String orig) {
        return TEXT_ANY.equals(orig) ? TEXT_XML : orig;
    }
}
