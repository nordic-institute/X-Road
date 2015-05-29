package ee.ria.xroad.asyncdb.messagequeue;

import java.io.InputStream;
import java.util.Map;

import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageConsumer;
import ee.ria.xroad.common.message.SoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageImpl;

class AsyncDBSoapMessageConsumer implements
        SoapMessageConsumer {
    private SoapMessageEncoder encoder;
    private SoapMessageImpl message;

    AsyncDBSoapMessageConsumer(SoapMessageEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public void soap(SoapMessage soapMessage) throws Exception {
        this.message = (SoapMessageImpl) soapMessage;
        encoder.soap(soapMessage);
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        encoder.attachment(contentType, content, additionalHeaders);
    }

    public SoapMessageImpl getMessage() {
        return message;
    }

}
