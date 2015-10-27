package ee.ria.xroad.common.message;

import javax.xml.soap.SOAPMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Base class for SOAP messages.
 * @param <T> generic type of the SOAP message header.
 */
@Getter
@RequiredArgsConstructor
public abstract class AbstractSoapMessage<T> implements SoapMessage {

    private final byte[] bytes;
    private final String charset;
    private final T header;
    private final SOAPMessage soap;
    private final boolean isResponse;
    private final boolean isRpcEncoded;
    private final String contentType;

    /**
     * Gets the SOAP header instance for this message.
     * @return T
     */
    public T getHeader() {
        return header;
    }

    @Override
    public boolean isRequest() {
        return !isResponse;
    }

    @Override
    public String getXml() throws Exception {
        return new String(bytes, charset);
    }
}
