package ee.cyber.sdsb.asyncdb.messagequeue;

import java.io.InputStream;
import java.util.Map;

import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageConsumer;
import ee.cyber.sdsb.common.message.SoapMessageEncoder;
import ee.cyber.sdsb.common.message.SoapMessageImpl;

class AsyncDBSoapMessageConsumer implements
        SoapMessageConsumer {
    private SoapMessageEncoder encoder;
    private SoapMessageImpl message;

    AsyncDBSoapMessageConsumer(SoapMessageEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public void soap(SoapMessage message) throws Exception {
        this.message = (SoapMessageImpl) message;
        encoder.soap(message);
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
