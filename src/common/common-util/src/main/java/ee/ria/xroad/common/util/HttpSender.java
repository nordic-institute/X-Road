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

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.InputStream;
import java.net.URI;

/**
 * This class encapsulates the sending and receiving of content via HTTP POST
 * method synchronously.
 */
@Slf4j
public class HttpSender extends AbstractHttpSender {
    private final HttpClient client;
    private static final String DO_POST_LOG = "doPost(address = {}, connectionTimeout = {}, socketTimeout = {})";
    /**
     * Configures a HTTP sender using the given HTTP client.
     * @param httpClient HTTP client this sender should use
     */
    public HttpSender(HttpClient httpClient) {
        client = httpClient;
    }

    /**
     * Sends data using POST method to some address.
     * Method blocks until response becomes available, after which
     * {@link #getResponseContent()} and {@link #getResponseContentType()}
     * can be used to retrieve the response.
     *
     * @param address the address to send
     * @param content the content to send
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    @Override
    public void doPost(URI address, String content, String contentType) throws Exception {
        log.trace(DO_POST_LOG, address, connectionTimeout,
                socketTimeout);

        HttpPost post = new HttpPost(address);
        post.setConfig(getRequestConfig());
        post.setEntity(createStringEntity(content, contentType));

        doRequest(post);
    }

    /**
     * Sends an input stream of data using POST method to some address.
     * Method blocks until response becomes available, after which
     * {@link #getResponseContent()} and {@link #getResponseContentType()}
     * can be used to retrieve the response.
     *
     * @param address the address to send
     * @param content the content to send
     * @param contentLength length of the content in bytes
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    @Override
    public void doPost(URI address, InputStream content, long contentLength, String contentType) throws Exception {
        log.trace(DO_POST_LOG, address, connectionTimeout,
                socketTimeout);

        HttpPost post = new HttpPost(address);
        post.setConfig(getRequestConfig());
        post.setEntity(createInputStreamEntity(content, contentLength, contentType));

        doRequest(post);
    }

    /**
     * REST support
     * @param address
     * @param entity
     * @throws Exception
     */
    public void doPost(URI address, HttpEntity entity) throws Exception {
        log.trace(DO_POST_LOG, address, connectionTimeout,
                socketTimeout);

        HttpPost post = new HttpPost(address);
        post.setConfig(getRequestConfig());
        post.setEntity(entity);

        doRequest(post);
    }

    @Override
    public void doGet(URI address) throws Exception {
        log.trace("doGet(address = {}, connectionTimeout = {}, socketTimeout = {})", address, connectionTimeout,
                socketTimeout);

        HttpGet get = new HttpGet(address);
        get.setConfig(getRequestConfig());

        doRequest(get);
    }

    private void doRequest(HttpRequestBase request) throws Exception {
        this.request = request;

        addAdditionalHeaders();

        if (log.isTraceEnabled()) {
            log.trace("Log request headers");
            for (Header header : request.getAllHeaders()) {
                log.trace(String.format("%s : %s", header.getName(), header.getValue()));
            }
        }

        try {
            HttpResponse response = client.execute(request, context);
            handleResponse(response);
        } catch (Exception ex) {
            log.debug("Request failed", ex);

            request.abort();

            throw ex;
        }
    }
}
