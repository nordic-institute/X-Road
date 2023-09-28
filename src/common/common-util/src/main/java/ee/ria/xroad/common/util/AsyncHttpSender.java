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
package ee.ria.xroad.common.util;

import ee.ria.xroad.common.CodedException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;
import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * Asynchronous HTTP sender.
 */
public class AsyncHttpSender extends AbstractHttpSender {

    public static final int DEFAULT_TIMEOUT_SEC = 60;

    private static final Logger LOG =
            LoggerFactory.getLogger(AsyncHttpSender.class);

    private final CloseableHttpAsyncClient client;

    private Future<HttpResponse> futureResponse;

    /**
     * Configures an asynchronous HTTP sender using the given HTTP client.
     * @param client asynchronous closeable HTTP client this sender should use
     */
    public AsyncHttpSender(CloseableHttpAsyncClient client) {
        super();
        this.client = client;
    }

    /**
     * Sends data using POST method to some address.
     * Method does not block. Use {@link #waitForResponse()} to get the
     * response handled after which you can use {@link #getResponseContent()}
     * and {@link #getResponseContentType()} to retrieve the response.
     *
     * @param address the address to send
     * @param content the content to send
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    @Override
    public void doPost(URI address, String content, String contentType)
            throws Exception {
        LOG.trace("doPost({})", address);

        HttpPost post = new HttpPost(address);
        post.setEntity(createStringEntity(content, contentType));

        PerformanceLogger.log(LOG, "doPost(" + address + ") done");

        doRequest(post);
    }

    /**
     * Sends an input stream of data using POST method to some address.
     * Method does not block. Use {@link #waitForResponse()} to get the
     * response handled after which you can use {@link #getResponseContent()}
     * and {@link #getResponseContentType()} to retrieve the response.
     *
     * @param address the address to send
     * @param content the content to send
     * @param contentLength length of the content in bytes
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    @Override
    public void doPost(URI address, InputStream content, long contentLength,
            String contentType) throws Exception {
        LOG.trace("doPost({})", address);

        HttpPost post = new HttpPost(address);
        post.setEntity(createInputStreamEntity(content, contentLength,
                contentType));

        PerformanceLogger.log(LOG, "doPost(" + address + ") done");

        doRequest(post);
    }

    @Override
    public void doGet(URI address) throws Exception {
        LOG.trace("doGet({})", address);

        PerformanceLogger.log(LOG, "doGet(" + address + ") done");

        doRequest(new HttpGet(address));
    }

    /**
     * Will block until response becomes available in the future.
     * @param timeoutSec number of seconds before a timeout exception is thrown
     * @throws Exception if response could not be retrieved in the alloted time
     */
    public void waitForResponse(int timeoutSec) throws Exception {
        if (futureResponse == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Request uninitialized");
        }

        LOG.trace("waitForResponse()");
        try {
            HttpResponse response =
                    futureResponse.get(timeoutSec, TimeUnit.SECONDS);
            handleResponse(response);
        } catch (TimeoutException e) {
            cancelRequest();
            throw new CodedException(X_NETWORK_ERROR, "Connection timed out");
        } catch (Exception e) {
            handleFailure(e);
        } finally {
            futureResponse = null;

            PerformanceLogger.log(LOG, "waitForResponse() done");
        }
    }

    private void handleFailure(Exception cause) {
        cancelRequest();
        throw translateException(cause);
    }

    private void consumeEntity() {
        if (request instanceof HttpPost) {
            try {
                EntityUtils.consume(((HttpPost) request).getEntity());
            } catch (IOException e) {
                LOG.error("Error when consuming entity", e);
            }
        }
    }

    private void doRequest(HttpRequestBase request) throws Exception {
        this.request = request;

        addAdditionalHeaders();
        try {
            futureResponse = client.execute(request, context, new Callback());
        } catch (Exception ex) {
            LOG.debug("Request failed", ex);
            request.abort();
            throw ex;
        }
    }

    private void cancelRequest() {
        if (futureResponse != null) {
            futureResponse.cancel(true);
        }
    }

    private class Callback implements FutureCallback<HttpResponse> {

        @Override
        public void cancelled() {
            consumeEntity();
        }

        @Override
        public void completed(HttpResponse arg0) {
            consumeEntity();
        }

        @Override
        public void failed(Exception e) {
            LOG.trace("failed()", e);
            consumeEntity();
        }
    }
}
