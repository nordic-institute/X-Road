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
package ee.ria.xroad.common.message;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.MimeUtils;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.MimeTypes.MULTIPART_RELATED;
import static ee.ria.xroad.common.util.MimeUtils.*;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Decodes SOAP messages from an input stream.
 */
@Slf4j
public class SoapMessageDecoder {

    private final String contentType;
    private final Callback callback;
    private final String baseContentType;
    private final SoapParser parser;

    /**
     * Callback interface for handling the outcome of the decoding process.
     */
    public interface Callback extends SoapMessageConsumer, Closeable {

        /**
         * Called when SoapFault has been completely read.
         * @param fault SOAP fault that's been read from the stream
         * @throws Exception in case of any errors
         */
        void fault(SoapFault fault) throws Exception;

        /**
         * Called when the message has been completely read.
         */
        void onCompleted();

        /**
         * Called when an error occurred during soap or attachment part.
         * @param t the exception that occurred
         * @throws Exception if any errors occur
         */
        void onError(Exception t) throws Exception;

        @Override
        default void close() { };
    }

    /**
     * Creates a new SOAP message decoder of the given content type and with
     * the provided callback.
     * @param contentType the expected content type
     * @param callback the callback to handle completion
     */
    public SoapMessageDecoder(String contentType, Callback callback) {
        this(contentType, callback, new SoapParserImpl());
    }

    /**
     * Creates a new SOAP message decoder of the given content type and with
     * the provided callback and SOAP parser implementation.
     * @param contentType the expected content type
     * @param callback the callback to handle completion
     * @param parserImpl SOAP parser implementation to use
     */
    public SoapMessageDecoder(String contentType, Callback callback,
            SoapParser parserImpl) {
        this.contentType = contentType;
        this.callback = callback;
        this.parser = parserImpl;

        this.baseContentType = getBaseContentType(contentType);
    }

    /**
     * Decodes the SOAP message from the given input stream.
     * @param soapStream input stream with the SOAP message data
     * @throws Exception if any errors occur
     */
    public void parse(InputStream soapStream) throws Exception {
        if (baseContentType == null) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Could not get content type from request");
        }

        try {
            switch (baseContentType) {
                case TEXT_XML:
                    readSoapMessage(soapStream);
                    break;
                case MULTIPART_RELATED:
                    readMultipart(soapStream);
                    break;
                default:
                    throw new CodedException(X_INVALID_CONTENT_TYPE,
                            "Invalid content type: %s", baseContentType);
            }
        } catch (Exception e) {
            callback.onError(e);
        }

        callback.onCompleted();
    }

    private void readSoapMessage(InputStream is) throws Exception {
        log.trace("readSoapMessage");

        Soap soap = parser.parse(baseContentType, getCharset(contentType), is);
        if (soap instanceof SoapFault) {
            callback.fault((SoapFault) soap);
            return;
        }

        if (!(soap instanceof SoapMessage)) {
            log.error("Expected SoapMessage, but got: {}", soap.getXml());
            throw new CodedException(
                    X_INTERNAL_ERROR, "Unexpected SOAP message");
        }

        callback.soap((SoapMessage) soap);
    }

    private void readMultipart(InputStream is) throws Exception {
        log.trace("readMultipart");

        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(contentType);

        MimeStreamParser mimeStreamParser = new MimeStreamParser(config);
        mimeStreamParser.setContentHandler(new MultipartHandler());
        // Parse the request.
        try {
            mimeStreamParser.parse(is);
        } catch (MimeException ex) {
            // We catch the mime parsing separately because this indicates
            // invalid request from client and we want to report it as that.
            throw new CodedException(X_MIME_PARSING_FAILED, ex);
        }
    }

    private class MultipartHandler extends AbstractContentHandler {
        private Map<String, String> headers;
        private String partContentType;
        private SoapMessage soapMessage;

        @Override
        public void startHeader() throws MimeException {
            headers = new HashMap<>();
            partContentType = null;
        }

        @Override
        public void field(Field field) throws MimeException {
            if (field.getName().toLowerCase().equals(HEADER_CONTENT_TYPE)) {
                partContentType = field.getBody();
            } else {
                headers.put(field.getName(), field.getBody());
            }
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            if (!headers.isEmpty()) {
                log.trace("headers: {}", headers);
            }

            if (partContentType == null) {
                throw new CodedException(X_INVALID_CONTENT_TYPE,
                        "Could not get content type for part");
            }

            try {
                if (soapMessage == null) {
                    // First part, consisting of the SOAP message.
                    log.trace("Read SOAP from multipart: {}", partContentType);
                    try {
                        Soap soap = parser.parse(
                                MimeUtils.getBaseContentType(partContentType),
                                MimeUtils.getCharset(partContentType), is);
                        if (!(soap instanceof SoapMessage)) {
                            throw new CodedException(X_INTERNAL_ERROR,
                                    "Unexpected SOAP message");
                        }

                        soapMessage = (SoapMessage) soap;
                        callback.soap(soapMessage);
                    } catch (Exception e) {
                        throw translateException(e);
                    }
                } else {
                    // Attachment
                    log.trace("Read attachment from multipart: {}",
                            partContentType);
                    callback.attachment(partContentType, is, headers);
                }
            } catch (Exception ex) {
                throw translateException(ex);
            }
        }
    }
}
