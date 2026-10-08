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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVER_PROXY_OPMONITOR_X;

/**
 * Answers operational data and health data queries given as SOAP messages. A failure is answered with a SOAP fault
 * in the same way as the HTTP query handler does.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
class OperationalDataQueryService {
    private static final String FAULT_CONTENT_TYPE = "text/xml;charset=utf-8";

    private final GlobalConfProvider globalConfProvider;
    private final HealthDataRegistry healthDataRegistry;
    private final OperationalDataRecordManager operationalDataRecordManager;
    private final OpMonitorProperties opMonitorProperties;

    record Response(byte[] body, String contentType) {
    }

    Response query(byte[] soapQuery, String contentType) {
        var responseBody = new ByteArrayOutputStream();
        var responseContentType = new AtomicReference<String>();
        try {
            QueryRequestProcessor.checkContentType(contentType);

            new QueryRequestProcessor(globalConfProvider, healthDataRegistry.getRegistry(), operationalDataRecordManager,
                    opMonitorProperties)
                    .process(new ByteArrayInputStream(soapQuery), contentType, () -> responseBody, responseContentType::set);

            return new Response(responseBody.toByteArray(), responseContentType.get());
        } catch (Throwable t) { // We want to catch serious errors as well
            log.error("Error while handling query request", t);

            return fault(XrdRuntimeException.systemException(t).withPrefix(SERVER_SERVER_PROXY_OPMONITOR_X));
        }
    }

    private static Response fault(XrdRuntimeException ex) {
        return new Response(SoapFault.createFaultXml(ex).getBytes(StandardCharsets.UTF_8), FAULT_CONTENT_TYPE);
    }
}
