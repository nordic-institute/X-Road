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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;

/**
 * AsicContainerHandler
 */
@Slf4j
public class AsicContainerHandler extends AbstractClientProxyHandler {

    /**
     * Constructor
     */
    public AsicContainerHandler(HttpClient client) {
        super(client);
    }

    @Override
    MessageProcessorBase createRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response)
                    throws Exception {
        log.trace("createRequestProcessor({})", target);
        if (!isGetRequest(request)) {
            return null;
        }

        if (target == null) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Target must not be null");
        }

        AsicContainerClientRequestProcessor processor =
                new AsicContainerClientRequestProcessor(target, request, response);
        if (processor.canProcess()) {
            log.trace("Processing with AsicContainerRequestProcessor");
            return processor;
        }

        return null;
    }
}
