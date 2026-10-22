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

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import com.codahale.metrics.MetricRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CONTENT_TYPE;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_HEALTH_DATA;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_OPERATIONAL_DATA;

/**
 * The processor class for operational monitoring query requests.
 * Processes getSecurityServerOperationalData and getSecurityServerHealthData
 * SOAP requests.
 */
@Slf4j
class QueryRequestProcessor {
    private final OperationalDataRequestHandler operationalDataHandler;
    private final HealthDataRequestHandler healthDataHandler;

    QueryRequestProcessor(GlobalConfProvider globalConfProvider,
                          MetricRegistry healthMetricRegistry,
                          OperationalDataRecordManager operationalDataRecordManager,
                          OpMonitorProperties opMonitorProperties) {
        this.operationalDataHandler = new OperationalDataRequestHandler(globalConfProvider, operationalDataRecordManager,
                opMonitorProperties);
        this.healthDataHandler = new HealthDataRequestHandler(
                healthMetricRegistry);

        globalConfProvider.verifyValidity();
    }

    /**
     * Checks that the query content type is text/xml.
     *
     * @param contentType the content type of the query
     */
    static void checkContentType(String contentType) {
        if (!MimeTypes.TEXT_XML.equalsIgnoreCase(MimeUtils.getBaseContentType(contentType))) {
            throw XrdRuntimeException.systemException(INVALID_CONTENT_TYPE,
                    "Invalid content type " + contentType + ", expecting " + MimeTypes.TEXT_XML);
        }
    }

    /**
     * Processes the incoming message.
     *
     * @param requestIn            the query message, closed when processing ends
     * @param contentType          the content type of the query message
     * @param responseOut          opens the stream the response is written to, called once the query is parsed;
     *                             the stream is closed when the response is written
     * @param contentTypeAssigner  receives the content type of the response before it is written
     * @throws Exception in case of any errors
     */
    @ArchUnitSuppressed("NoVanillaExceptions")
    void process(InputStream requestIn, String contentType, Supplier<OutputStream> responseOut,
                 Consumer<String> contentTypeAssigner) throws Exception {
        try (var handler = new QueryRequestHandler(responseOut, contentTypeAssigner); requestIn) {
            SoapMessageDecoder soapMessageDecoder = new SoapMessageDecoder(contentType, handler, new SoapParserImpl());

            soapMessageDecoder.parse(requestIn);
        }
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    @RequiredArgsConstructor
    private final class QueryRequestHandler implements SoapMessageDecoder.Callback {
        private final Supplier<OutputStream> responseOut;
        private final Consumer<String> contentTypeAssigner;

        @Override
        public void soap(SoapMessage message, Map<String, String> headers) throws Exception {
            if (log.isTraceEnabled()) {
                log.trace("soap({})", message.getXml());
            }

            SoapMessageImpl requestSoap = (SoapMessageImpl) message;

            try (var out = responseOut.get()) {
                switch (requestSoap.getService().getServiceCode()) {
                    case GET_SECURITY_SERVER_OPERATIONAL_DATA:
                        operationalDataHandler.handle(requestSoap, out, contentTypeAssigner);
                        break;
                    case GET_SECURITY_SERVER_HEALTH_DATA:
                        healthDataHandler.handle(requestSoap, out, contentTypeAssigner);
                        break;
                    default:
                        throw XrdRuntimeException.systemInternalError(
                                "Unknown service: '%s'".formatted(requestSoap.getService()));
                }
            }
        }

        @Override
        public void attachment(String contentType, InputStream content,
                               Map<String, String> additionalHeaders) {
            // Discard.
        }

        @Override
        public void onCompleted() {
            // Do nothing.
        }

        @Override
        public void onError(Exception t) throws Exception {
            throw t;
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            log.error("Received fault {}", fault.getXml());

            throw fault.toXrdRuntimeException();
        }
    }
}
