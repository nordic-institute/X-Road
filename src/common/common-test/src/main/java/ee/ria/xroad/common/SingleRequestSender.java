/**
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
package ee.ria.xroad.common;

import ee.ria.xroad.common.util.AsyncHttpSender;

import org.apache.http.HttpHeaders;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import java.io.InputStream;
import java.net.URI;

import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;

/**
 * Performs a single request at a time and returns a response.
 *
 * FUTURE Only 'extra' project uses it (subproject 'testclient')
 */
public class SingleRequestSender {

    private static final Logger LOG =
            LoggerFactory.getLogger(SingleRequestSender.class);

    private static final int DEFAULT_CLIENT_TIMEOUT_SEC = 30;

    private static MessageFactory messageFactory = null;

    static {
        try {
            messageFactory = MessageFactory.newInstance();
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private CloseableHttpAsyncClient client;
    private Integer timeoutSec;

    /**
     * Construct a request sender that uses the given HTTP client and
     * the specified timeout.
     * @param client the HTTP client this sender should use
     * @param timeoutSec timeout of the request in seconds
     */
    public SingleRequestSender(CloseableHttpAsyncClient client,
            Integer timeoutSec) {
        this(client);
        this.timeoutSec = timeoutSec;
    }

    /**
     * Construct a request sender that uses the given HTTP client and
     * the default timeout (30 seconds).
     * @param client the HTTP client this sender should use
     */
    public SingleRequestSender(CloseableHttpAsyncClient client) {
        this.client = client;
    }

    /**
     * Seconds the request in the given input stream to the specified address
     * and returns a response.
     * @param address address to which to send the request
     * @param contentType content type of the data in the input stream
     * @param content input stream containing the request data
     * @return response SOAP message
     * @throws Exception in case of any errors
     */
    public SOAPMessage sendRequestAndReceiveResponse(String address,
            String contentType, InputStream content) throws Exception {
        try (AsyncHttpSender sender = new AsyncHttpSender(client)) {
            sender.doPost(new URI(address), content, CHUNKED_LENGTH,
                    contentType);

            sender.waitForResponse(getTimeoutSec());

            String responseContentType = sender.getResponseContentType();
            MimeHeaders mimeHeaders = getMimeHeaders(responseContentType);

            LOG.debug("Received response with content type {}",
                    responseContentType);

            return messageFactory.createMessage(mimeHeaders,
                    sender.getResponseContent());
        }
    }

    private Integer getTimeoutSec() {
        if (timeoutSec == null) {
            return DEFAULT_CLIENT_TIMEOUT_SEC;
        }

        return timeoutSec;
    }

    private static MimeHeaders getMimeHeaders(String contentType) {
        MimeHeaders mimeHeaders = new MimeHeaders();
        mimeHeaders.addHeader(HttpHeaders.CONTENT_TYPE, contentType);
        return mimeHeaders;
    }

}
