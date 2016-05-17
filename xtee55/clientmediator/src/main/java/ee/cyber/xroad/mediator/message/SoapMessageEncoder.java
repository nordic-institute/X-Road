package ee.cyber.xroad.mediator.message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.eclipse.jetty.http.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.message.SoapMessage;

/**
 * Message encoder that encodes only SOAP messages.
 */
public class SoapMessageEncoder implements MessageEncoder {

    private static final Logger LOG =
            LoggerFactory.getLogger(SoapMessageEncoder.class);

    private final OutputStream out;

    /**
     * Constructs a SOAP message encoder with the given output stream.
     * @param out the output stream
     */
    public SoapMessageEncoder(OutputStream out) {
        this.out = out;
    }

    @Override
    public String getContentType() {
        return MimeTypes.TEXT_XML;
    }

    @Override
    public void soap(SoapMessage soapMessage,
            Map<String, String> additionalHeaders) throws Exception {
        LOG.trace("soap()");

        out.write(soapMessage.getBytes());
    }

    @Override
    public void close() throws IOException {
        LOG.trace("close()");

        try {
            out.flush();
        } finally {
            out.close();
        }
    }

}
