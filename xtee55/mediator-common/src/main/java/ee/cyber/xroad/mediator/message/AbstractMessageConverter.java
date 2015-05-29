package ee.cyber.xroad.mediator.message;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.soap.SOAPMessage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.util.XmlUtils;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;

import static ee.ria.xroad.common.message.SoapUtils.MESSAGE_FACTORY;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
abstract class AbstractMessageConverter<FromType extends SoapMessage,
        ToType extends SoapMessage> {

    private final IdentifierMappingProvider identifierMapping;

    public abstract ToType convert(FromType message) throws Exception;

    protected static SOAPMessage cloneMessage(SOAPMessage soap)
            throws Exception {
        String soapXml = XmlUtils.prettyPrintXml(
                soap.getSOAPBody().getOwnerDocument()); // XXX: or unpretty XML

        return MESSAGE_FACTORY.createMessage(soap.getMimeHeaders(),
                new ByteArrayInputStream(
                        soapXml.getBytes(StandardCharsets.UTF_8)));
    }

    protected static String prettyPrintXml(SOAPMessage soap, String charset)
            throws Exception {
        return XmlUtils.prettyPrintXml(soap.getSOAPBody().getOwnerDocument(),
                charset);
    }

    protected static String prettyPrintXml(SoapMessage message)
            throws Exception {
        return prettyPrintXml(message.getSoap(), message.getCharset());
    }

}
