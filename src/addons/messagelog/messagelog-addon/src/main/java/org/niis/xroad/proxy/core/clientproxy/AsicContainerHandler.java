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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.proxy.core.util.MessageProcessorBase;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.util.JettyUtils.getTarget;

/**
 * AsicContainerHandler
 */
@Slf4j
public class AsicContainerHandler extends AbstractClientProxyHandler {

    /**
     * Constructor
     */
    public AsicContainerHandler(CommonBeanProxy commonBeanProxy, HttpClient client) {
        super(commonBeanProxy, client, false);
    }

    @Override
    MessageProcessorBase createRequestProcessor(RequestWrapper request, ResponseWrapper response,
                                                OpMonitoringData opMonitoringData) throws Exception {
        var target = getTarget(request);
        log.trace("createRequestProcessor({})", target);

        // opMonitoringData is null, do not use it.

        if (!isGetRequest(request)) {
            return null;
        }

        if (target == null) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Target must not be null");
        }

        AsicContainerClientRequestProcessor processor = new AsicContainerClientRequestProcessor(
                commonBeanProxy,
                target,
                request,
                response);

        if (processor.canProcess()) {
            log.trace("Processing with AsicContainerRequestProcessor");

            return processor;
        }

        return null;
    }
}
