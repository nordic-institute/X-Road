package ee.cyber.xroad.mediator.message;

import java.io.IOException;

import ee.cyber.sdsb.common.message.SoapMessage;

public interface MessageEncoder {

    String getContentType();

    void soap(SoapMessage soapMessage) throws Exception;

    void close() throws IOException;

}
