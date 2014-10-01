package ee.cyber.sdsb.common.message;

import javax.xml.soap.SOAPMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class AbstractSoapMessage<T> implements SoapMessage {

    private final String xml;
    private final String charset;
    private final T header;
    private final SOAPMessage soap;
    private final boolean isResponse;
    private final boolean isRpcEncoded;

    public T getHeader() {
        return header;
    }

    @Override
    public boolean isRequest() {
        return !isResponse;
    }

}
