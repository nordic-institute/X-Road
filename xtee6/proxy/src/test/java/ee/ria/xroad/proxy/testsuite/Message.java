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
package ee.ria.xroad.proxy.testsuite;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;

import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.message.SoapUtils;

/**
 * Encapsulates a test SOAP message.
 */
@Slf4j
@Getter
public class Message {

    private final List<Map<String, String>> multipartHeaders =
            new ArrayList<>();

    private int numAttachments = 0;

    private Soap soap;

    /**
     * Constructs a new message from the given input stream with the specified
     * content type.
     * @param inputStream the input stream
     * @param contentType the contect type
     * @throws Exception in case of any unexpected errors
     */
    public Message(InputStream inputStream, String contentType)
            throws Exception {
        log.debug("new Message({})", contentType);

        try {
            MimeConfig config = new MimeConfig();
            config.setHeadlessParsing(contentType);

            MimeStreamParser parser = new MimeStreamParser(config);
            parser.setContentHandler(new ContentHandler());

            parser.parse(inputStream);
        } catch (Exception ex) {
            // Ignore errors, because we may be dealing with tests with
            // invalid messages.
            log.error("Error when parsing message", ex);
        }
    }

    /**
     * @param anotherMessage the other message
     * @return true if this message is consistent with another message
     */
    public boolean checkConsistency(Message anotherMessage) {
        if (soap == null
                && (anotherMessage == null
                    || anotherMessage.soap == null)) {
            return true;
        }

        if (!(soap instanceof SoapMessageImpl)
                || !(anotherMessage.soap instanceof SoapMessageImpl)) {
            return false;
        }

        if (soap != null
                && anotherMessage.soap != null
                && numAttachments == anotherMessage.numAttachments) {
            try {
                SoapUtils.checkConsistency((SoapMessageImpl) soap,
                        (SoapMessageImpl) anotherMessage.soap);
                return true;
            } catch (Exception e) {
                log.error("Inconsistent messages", e);
            }
        }

        return false;
    }

    /**
     * @return true if this message is a fault
     */
    public boolean isFault() {
        return soap != null && soap instanceof SoapFault;
    }

    /**
     * @return true if this is a response message
     */
    public boolean isResponse() {
        return soap != null && soap instanceof SoapMessageImpl
                && ((SoapMessageImpl) soap).isResponse();
    }

    private class ContentHandler extends AbstractContentHandler {
        private Map<String, String> headers;
        private int nextPart = 0;

        @Override
        public void startHeader() throws MimeException {
            headers = new HashMap<>();
            multipartHeaders.add(headers);
        }

        @Override
        public void field(Field field) throws MimeException {
            headers.put(field.getName(), field.getBody());
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            switch (nextPart) {
                case 0:
                    try {
                        soap = new SoapParserImpl().parse(bd.getMimeType(),
                                bd.getCharset(), is);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    nextPart = 1;
                    break;
                case 1:
                default:
                    numAttachments++;
                    break;
            }
        }
    }
}
