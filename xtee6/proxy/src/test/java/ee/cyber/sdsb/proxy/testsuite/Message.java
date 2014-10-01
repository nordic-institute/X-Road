package ee.cyber.sdsb.proxy.testsuite;

import java.io.IOException;
import java.io.InputStream;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;
import ee.cyber.sdsb.common.message.SoapUtils;

public class Message {
    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    private int numAttachments = 0;
    private Soap soap;

    public Message(InputStream inputStream, String contentType)
            throws Exception {
        LOG.debug("new Message({})", contentType);

        try {
            MimeConfig config = new MimeConfig();
            config.setHeadlessParsing(contentType);

            MimeStreamParser parser = new MimeStreamParser(config);
            parser.setContentHandler(new ContentHandler(parser));
            parser.parse(inputStream);
        } catch (Exception ex) {
            // Ignore errors, because we may be dealing with tests with
            // invalid messages.
            LOG.error("Error when parsing message", ex);
        }
    }

    public Soap getSoap() {
        return soap;
    }

    public boolean checkConsistency(Message anotherMessage) {
        if (soap == null &&
                (anotherMessage == null ||
                        anotherMessage.soap == null)) {
            return true;
        }

        if (!(soap instanceof SoapMessageImpl) ||
                !(anotherMessage.soap instanceof SoapMessageImpl)) {
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
                LOG.error("Inconsistent messages", e);
            }
        }

        return false;
    }

    public boolean isFault() {
        return soap != null && soap instanceof SoapFault;
    }

    public boolean isResponse() {
        return soap != null && soap instanceof SoapMessageImpl &&
                ((SoapMessageImpl) soap).isResponse();
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
            }
        }
    }
}
