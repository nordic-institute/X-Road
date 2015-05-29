package ee.ria.xroad.proxy.testsuite;

import java.io.IOException;
import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
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
public class Message {

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
            parser.setContentHandler(new ContentHandler(parser));
            parser.parse(inputStream);
        } catch (Exception ex) {
            // Ignore errors, because we may be dealing with tests with
            // invalid messages.
            log.error("Error when parsing message", ex);
        }
    }

    /**
     * @return the SOAP message
     */
    public Soap getSoap() {
        return soap;
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
        private int nextPart = 0;
        private MimeStreamParser parser;

        ContentHandler(MimeStreamParser parser) {
            this.parser = parser;
        }

        @Override
        public void startMultipart(BodyDescriptor bd) throws MimeException {
            parser.setFlat();
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
                    numAttachments++;
                default:
                    break;
            }
        }
    }
}
