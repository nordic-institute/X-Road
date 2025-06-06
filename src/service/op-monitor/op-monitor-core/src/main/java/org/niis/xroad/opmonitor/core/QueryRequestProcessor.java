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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import com.codahale.metrics.MetricRegistry;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_HEALTH_DATA;
import static org.niis.xroad.opmonitor.api.OpMonitoringRequests.GET_SECURITY_SERVER_OPERATIONAL_DATA;

/**
 * The processor class for operational monitoring query requests.
 * Processes getSecurityServerOperationalData and getSecurityServerHealthData
 * SOAP requests.
 */
@Slf4j
class QueryRequestProcessor {
    /**
     * The servlet request.
     */
    private final RequestWrapper request;

    /**
     * The servlet response.
     */
    private final ResponseWrapper response;

    private final OperationalDataRequestHandler operationalDataHandler;
    private final HealthDataRequestHandler healthDataHandler;

    QueryRequestProcessor(GlobalConfProvider globalConfProvider,
                          RequestWrapper request,
                          ResponseWrapper response,
                          MetricRegistry healthMetricRegistry) {
        this.request = request;
        this.response = response;

        this.operationalDataHandler = new OperationalDataRequestHandler(globalConfProvider);
        this.healthDataHandler = new HealthDataRequestHandler(
                healthMetricRegistry);

        globalConfProvider.verifyValidity();
    }

    /**
     * Processes the incoming message.
     *
     * @throws Exception in case of any errors
     */
    void process() throws Exception {
        try (var handler = new QueryRequestHandler(); var requestIn = request.getInputStream()) {
            SoapMessageDecoder soapMessageDecoder =
                    new SoapMessageDecoder(request.getContentType(),
                            handler, new SoapParserImpl());

            soapMessageDecoder.parse(requestIn);
        }
    }

    private final class QueryRequestHandler implements SoapMessageDecoder.Callback {
        @Override
        public void soap(SoapMessage message, Map<String, String> headers)
                throws Exception {
            if (log.isTraceEnabled()) {
                log.trace("soap({})", message.getXml());
            }

            SoapMessageImpl requestSoap = (SoapMessageImpl) message;

            response.putHeader("Connection", "close");

            try (var responseOut = response.getOutputStream()) {
                switch (requestSoap.getService().getServiceCode()) {
                    case GET_SECURITY_SERVER_OPERATIONAL_DATA:
                        operationalDataHandler.handle(requestSoap,
                                responseOut,
                                responseContentTypeAssigner());
                        break;
                    case GET_SECURITY_SERVER_HEALTH_DATA:
                        healthDataHandler.handle(requestSoap,
                                responseOut,
                                responseContentTypeAssigner());
                        break;
                    default:
                        throw new CodedException(ErrorCodes.X_INTERNAL_ERROR,
                                "Unknown service: '%s'", requestSoap.getService());
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

            throw fault.toCodedException();
        }
    }

    private Consumer<String> responseContentTypeAssigner() {
        return response::setContentType;
    }
}
