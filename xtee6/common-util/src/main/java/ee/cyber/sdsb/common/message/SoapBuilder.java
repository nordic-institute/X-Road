package ee.cyber.sdsb.common.message;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.IdentifierXmlNodePrinter;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.util.MimeUtils;

import static ee.cyber.sdsb.common.ErrorCodes.X_MISSING_BODY;
import static ee.cyber.sdsb.common.identifier.IdentifierXmlNodeParser.NS_IDENTIFIERS;
import static ee.cyber.sdsb.common.identifier.IdentifierXmlNodeParser.PREFIX_IDENTIFIERS;
import static ee.cyber.sdsb.common.message.SoapHeader.*;
import static ee.cyber.sdsb.common.message.SoapMessageImpl.NS_SDSB;
import static ee.cyber.sdsb.common.message.SoapMessageImpl.PREFIX_SDSB;
import static ee.cyber.sdsb.common.message.SoapUtils.*;

public class SoapBuilder {

    private static final Logger LOG =
            LoggerFactory.getLogger(SoapBuilder.class);

    private static final String CHARSET = MimeUtils.UTF8;

    public static interface BodyBuilderCallback {
        void build(Node soapBodyNode) throws Exception;
    }

    private SoapBuilder() {
    }

    public static SoapMessageImpl build(ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        return build(false, sender, receiver, userId, queryId);
    }

    public static SoapMessageImpl build(boolean rpcEncoded, ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        final String bodyNodeName = receiver.getServiceCode();
        return build(rpcEncoded, sender, receiver, userId, queryId,
                new BodyBuilderCallback() {
                    @Override
                    public void build(Node soapBodyNode) throws Exception {
                        Document doc = soapBodyNode.getOwnerDocument();
                        Element node = doc.createElementNS(NS_SDSB,
                                PREFIX_SDSB + ":" + bodyNodeName);
                        soapBodyNode.appendChild(node);
                    }
        });
    }

    public static SoapMessageImpl build(boolean rpcEncoded, ClientId sender,
            ServiceId receiver, String userId, String queryId,
            BodyBuilderCallback bodyBuilder) throws Exception {
        LOG.debug("build(sender: {}, receiver: {}, userId: {}, queryId: {})",
                new Object[] {sender, receiver, userId, queryId});

        SOAPMessage soap = createMessage();
        addNamespaces(soap, rpcEncoded);

        SOAPBody soapBody = soap.getSOAPBody();
        if (bodyBuilder != null) {
            bodyBuilder.build(soapBody);
        } else {
            throw new CodedException(X_MISSING_BODY,
                    "SOAP message must have body");
        }

        addHeaderFields(sender, receiver, userId, queryId,
                soap.getSOAPHeader());

        SoapHeader header =
                new SoapHeader(CHARSET, sender, receiver, userId, queryId);

        String serviceName = getServiceName(soapBody);
        validateServiceName(header.service.getServiceCode(), serviceName);

        String xml = SoapUtils.getXml(soap, CHARSET);
        return new SoapMessageImpl(xml, soap, header, serviceName);
    }

    private static void addHeaderFields(ClientId sender, ServiceId receiver,
            String userId, String queryId, SOAPHeader soapHeader)
            throws Exception {
        addHeaderField(soapHeader, FIELD_USER_ID, userId);
        addHeaderField(soapHeader, FIELD_QUERY_ID, queryId);

        IdentifierXmlNodePrinter.printClientId(sender, soapHeader,
                createHeaderQName(FIELD_CLIENT));

        IdentifierXmlNodePrinter.printServiceId(receiver, soapHeader,
                createHeaderQName(FIELD_SERVICE));

        // TODO: Optional fields?
    }

    private static QName createHeaderQName(String name) {
        return new QName(NS_SDSB, name, PREFIX_SDSB);
    }

    private static void addHeaderField(SOAPHeader header, String name,
            String value) throws Exception {
        SOAPElement headerElement = header.addChildElement(name, PREFIX_SDSB);
        headerElement.setTextContent(value);
    }

    private static void addNamespaces(SOAPMessage soapMessage,
            boolean rpcEncoded) throws SOAPException {
        SOAPEnvelope soapEnvelope = soapMessage.getSOAPPart().getEnvelope();
        soapEnvelope.addNamespaceDeclaration(PREFIX_SDSB, NS_SDSB);
        soapEnvelope.addNamespaceDeclaration(
                PREFIX_IDENTIFIERS, NS_IDENTIFIERS);

        if (rpcEncoded) {
            soapEnvelope.addAttribute(new QName(RPC_ATTR), RPC_ENCODING);
        }
    }

    private static SOAPMessage createMessage() throws SOAPException {
        MessageFactory messageFactory = MessageFactory.newInstance();
        return messageFactory.createMessage();
    }
}
