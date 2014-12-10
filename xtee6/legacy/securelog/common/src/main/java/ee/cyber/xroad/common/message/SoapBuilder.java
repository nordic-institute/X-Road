package ee.cyber.xroad.common.message;

import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import ee.cyber.xroad.common.util.MimeUtils;

import static ee.cyber.sdsb.common.identifier.IdentifierXmlNodeParser.NS_IDENTIFIERS;
import static ee.cyber.sdsb.common.identifier.IdentifierXmlNodeParser.PREFIX_IDENTIFIERS;
import static ee.cyber.xroad.common.message.SoapHeader.NS_SDSB;
import static ee.cyber.xroad.common.message.SoapHeader.PREFIX_SDSB;
import static ee.cyber.xroad.common.message.SoapUtils.*;

@Getter
@Setter
public class SoapBuilder {

    public static interface SoapBodyCallback {
        void create(SOAPBody soapBody) throws Exception;
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(SoapBuilder.class);

    private static final String DEFAULT_CHARSET = MimeUtils.UTF8;

    private String charset = DEFAULT_CHARSET;
    private SoapHeader header;
    private boolean isRpcEncoded;
    private SoapBodyCallback createBodyCallback;

    public SoapMessageImpl build() throws Exception {
        if (header == null) {
            throw new IllegalStateException("Header cannot be null");
        }

        RequiredHeaderFieldsChecker.checkRequiredFields(header);
        LOG.trace("build(header: {})", header);

        SOAPMessage soap = createMessage();
        assembleMessage(soap);
        assembleMessageBody(soap);

        String serviceName = getServiceName(soap.getSOAPBody());
        validateServiceName(header.getService().getServiceCode(), serviceName);

        String xml = SoapUtils.getXml(soap, charset);
        return new SoapMessageImpl(xml, charset, header, soap, serviceName);
    }

    protected void addNamespaces(SOAPMessage soapMessage, boolean rpcEncoded)
            throws SOAPException {
        SOAPEnvelope soapEnvelope = soapMessage.getSOAPPart().getEnvelope();
        soapEnvelope.addNamespaceDeclaration(PREFIX_SDSB, NS_SDSB);
        soapEnvelope.addNamespaceDeclaration(
                PREFIX_IDENTIFIERS, NS_IDENTIFIERS);

        if (rpcEncoded) {
            soapEnvelope.addAttribute(new QName(RPC_ATTR), RPC_ENCODING);
        }
    }

    private void assembleMessageBody(SOAPMessage soap) throws Exception {
        SoapBodyCallback cb = createBodyCallback;
        if (cb == null) {
            final String bodyNodeName = header.getService().getServiceCode();
            cb = new SoapBodyCallback() {
                @Override
                public void create(SOAPBody soapBody) throws Exception {
                    Document doc = soapBody.getOwnerDocument();
                    Element node = doc.createElementNS(NS_SDSB,
                            PREFIX_SDSB + ":" + bodyNodeName);
                    soapBody.appendChild(node);
                }
            };
        }

        cb.create(soap.getSOAPBody());
    }

    private void assembleMessage(SOAPMessage soap) throws Exception {
        // Since we are marshaling the header into the SOAPEnvelope object
        // (creating a new SOAPHeader element), we need to remove the existing
        // SOAPHeader element and SOAPBody from the Envelope, then marshal
        // the header and add the body back to get correct order of elements.
        SOAPEnvelope envelope = soap.getSOAPPart().getEnvelope();

        Node soapBody = envelope.removeChild(soap.getSOAPBody());
        envelope.removeContents(); // removes newlines etc.

        Marshaller marshaller = JaxbUtils.createMarshaller(header.getClass(),
                new SoapNamespacePrefixMapper());
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(header, envelope);

        envelope.appendChild(soapBody);
    }

    private SOAPMessage createMessage() throws SOAPException {
        SOAPMessage soap = SoapUtils.MESSAGE_FACTORY.createMessage();
        addNamespaces(soap, isRpcEncoded);
        return soap;
    }

}
