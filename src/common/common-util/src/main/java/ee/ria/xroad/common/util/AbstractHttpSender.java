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
import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.EofSensorInputStream;
import org.apache.http.conn.EofSensorWatcher;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static ee.ria.xroad.common.ErrorCodes.X_HTTP_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CONTENT_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_IO_ERROR;

/**
 * Base class for a closeable HTTP sender.
 */
/*
 TODO reimplement using Apache HttpClient 5, which is used by other libraries,
 and then remove older apache http client lib if possible
 */
@Slf4j
public abstract class AbstractHttpSender implements Closeable {
    public static final int CHUNKED_LENGTH = -1;

    private static final int DEFAULT_CONNECTION_TIMEOUT = 30000; // default 30 sec

    private static final int DEFAULT_SOCKET_TIMEOUT = 0; // default infinite

    private final Map<String, String> additionalHeaders = new HashMap<>();

    private String responseContentType;
    private InputStream responseContent;
    private Map<String, String> responseHeaders;

    protected final HttpContext context = new BasicHttpContext();

    protected HttpRequestBase request;
    protected HttpEntity responseEntity;

    protected int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    protected int socketTimeout = DEFAULT_SOCKET_TIMEOUT;

    /**
     * Sets the connection timeout in milliseconds.
     * @param newTimeout the new timeout value
     */
    public void setConnectionTimeout(int newTimeout) {
        this.connectionTimeout = newTimeout;
    }

    /**
     * Sets the socket timeout in milliseconds.
     * @param newTimeout the new timeout value
     */
    public void setSocketTimeout(int newTimeout) {
        this.socketTimeout = newTimeout;
    }

    /**
     * Sets the value of an attribute.
     * @param name attribute name
     * @param value attribute value
     */
    public void setAttribute(String name, Object value) {
        context.setAttribute(name, value);
    }

    /**
     * Adds an additional header to the request.
     * @param name header name
     * @param value header value
     */
    public void addHeader(String name, String value) {
        additionalHeaders.put(name, value);
    }

    /**
     * @return the response content type.
     */
    public String getResponseContentType() {
        return responseContentType;
    }

    /**
     * @return the response content input stream.
     */
    public InputStream getResponseContent() {
        return responseContent;
    }

    /**
     * @return all response headers returned in the response.
     */
    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    protected void handleResponse(HttpResponse response) throws Exception {
        log.trace("handleResponse()");

        checkResponseStatus(response);

        responseHeaders = getResponseHeaders(response);
        this.responseEntity = getResponseEntity(response);
        responseContentType = getResponseContentType(responseEntity, this.request instanceof HttpGet);

        // Wrap the response input stream in order to catch EOF errors.
        responseContent = new EofSensorInputStream(responseEntity.getContent(), new ResponseStreamWatcher());
    }

    /**
     * Perform a GET request to the given address.
     * @param address URI of the address for the GET request
     * @throws Exception if any errors occur
     */
    public abstract void doGet(URI address) throws Exception;

    /**
     * Sends data using POST method to the given address.
     * @param address the address to send
     * @param content the content to send
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    public abstract void doPost(URI address, String content, String contentType) throws Exception;

    /**
     * Sends data using POST method to the given address.
     * @param address the address to send
     * @param content the content to send
     * @param contentLength length of the content in bytes
     * @param contentType the content type of the input data
     * @throws Exception if an error occurs
     */
    public abstract void doPost(URI address, InputStream content, long contentLength, String contentType)
            throws Exception;

    @Override
    public void close() {
        if (!SystemProperties.isEnableClientProxyPooledConnectionReuse()) {
            if (request != null) {
                request.releaseConnection();
            }
        } else {
            try {
                // consume and close the stream, returning the connection as reusable into the pool
                EntityUtils.consume(responseEntity);
            } catch (IOException e) {
                // reading/closing the stream failed for whatever reason, the broken connection should be cleaned up by
                // a pool monitor. Keep the contract set by releaseConnection and don't throw checked exceptions.
                // Nothing really to be done here anyway.
                log.warn("Closing response entity nicely failed", e);
            }
        }
    }

    protected void addAdditionalHeaders() {
        for (Entry<String, String> header : additionalHeaders.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }
    }

    protected RequestConfig getRequestConfig() {
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(connectionTimeout);
        rb.setConnectionRequestTimeout(connectionTimeout);
        rb.setSocketTimeout(socketTimeout);

        return rb.build();
    }

    protected static InputStreamEntity createInputStreamEntity(InputStream content, long contentLength,
            String contentType) {
        InputStreamEntity entity = new InputStreamEntity(content, contentLength);

        if (contentLength < 0) {
            entity.setChunked(true); // Just in case
        }

        entity.setContentType(contentType);

        return entity;
    }

    protected static StringEntity createStringEntity(String content, String contentType) {
        return new StringEntity(content, ContentType.create(contentType, MimeUtils.UTF8));
    }

    protected void checkResponseStatus(HttpResponse response) {
        switch (response.getStatusLine().getStatusCode()) {
            case HttpStatus.OK_200: // FALL THROUGH
            // R1126 An INSTANCE MUST return a "500 Internal Server Error"
            // HTTP status code if the response envelope is a Fault.
            case HttpStatus.INTERNAL_SERVER_ERROR_500:
                return;
            default:
                throw new CodedException(X_HTTP_ERROR, "Server responded with error %s: %s",
                        response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        }
    }

    protected static Map<String, String> getResponseHeaders(HttpResponse response) {
        Map<String, String> headers = new HashMap<>();

        for (Header header : response.getAllHeaders()) {
            headers.put(header.getName(), header.getValue());
        }

        return headers;
    }

    protected static HttpEntity getResponseEntity(HttpResponse response) {
        HttpEntity entity = response.getEntity();

        if (entity == null) {
            throw new CodedException(X_HTTP_ERROR, "Could not get content from response");
        }

        return entity;
    }

    protected String getResponseContentType(HttpEntity entity, boolean isGetRequest) {
        Header contentType = entity.getContentType();

        if (contentType == null) {
            if (isGetRequest) {
                return null;
            }

            throw new CodedException(X_INVALID_CONTENT_TYPE, "Could not get content type from response");
        }

        return contentType.getValue();
    }

    protected class ResponseStreamWatcher implements EofSensorWatcher {
        @Override
        public boolean eofDetected(InputStream wrapped) throws IOException {
            return true;
        }

        @Override
        public boolean streamClosed(InputStream wrapped) throws IOException {
            log.warn("Stream was closed before EOF was detected");

            return true;
        }

        @Override
        public boolean streamAbort(InputStream wrapped) throws IOException {
            throw new CodedException(X_IO_ERROR, "Stream was aborted");
        }
    }
}
