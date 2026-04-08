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
package org.niis.xroad.proxy.core.addon;

import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.exception.XrdRuntimeHttpException;
import org.niis.xroad.proxy.core.util.AddonRequestContext;

import java.io.IOException;
import java.util.Optional;

/**
 * Shared base for addon request handlers (AsicContainer, Metadata).
 * Provides the common handle() template with error handling and the isGetRequest() check.
 */
@Slf4j
public abstract class AddonHandlerBase extends HandlerBase {

    private static final String REQUEST_PROCESSING_ERROR = "Request processing error";

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws IOException {
        var ctxOpt = createRequestContext(RequestWrapper.of(request), ResponseWrapper.of(response));
        if (ctxOpt.isEmpty()) {
            return false;
        }
        var ctx = ctxOpt.get();
        try {
            processRequest(ctx);
            callback.succeeded();
        } catch (XrdRuntimeHttpException e) {
            log.error(REQUEST_PROCESSING_ERROR, e);
            sendPlainTextErrorResponse(response, callback, e.getHttpStatus().get().getCode(), e.getDetails());
        } catch (XrdRuntimeException e) {
            log.error(REQUEST_PROCESSING_ERROR, e);
            sendErrorResponse(request, response, callback, e);
        } catch (Exception e) {
            log.error(REQUEST_PROCESSING_ERROR, e);
            XrdRuntimeException cex = XrdRuntimeException.systemException(e);
            sendErrorResponse(request, response, callback, cex);
        }
        return true;
    }

    /**
     * Creates the per-request context if this handler can process the request,
     * or returns an empty Optional if this handler does not own the request.
     */
    protected abstract Optional<AddonRequestContext> createRequestContext(RequestWrapper request, ResponseWrapper response);

    /**
     * Processes the request using the given context. Called only when createRequestContext returns non-empty Optional.
     */
    @ArchUnitSuppressed("NoVanillaExceptions")
    protected abstract void processRequest(AddonRequestContext ctx) throws Exception;

    protected boolean isGetRequest(RequestWrapper request) {
        return request.getMethod().equalsIgnoreCase("GET");
    }
}
