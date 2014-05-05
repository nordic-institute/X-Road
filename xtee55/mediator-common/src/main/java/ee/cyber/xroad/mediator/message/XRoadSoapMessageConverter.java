package ee.cyber.xroad.mediator.message;

import java.util.Iterator;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;

import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapBuilder;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Converts X-Road 5.0 SOAP messages to SDSB SOAP messages.
 *
 * Note, that conversions between encodings (RCP <-> D/L) are not supported,
 * thus if the input message is RPC encoded, the output message is also
 * RPC encoded.
 */
class XRoadSoapMessageConverter extends
        AbstractMessageConverter<XRoadSoapMessageImpl, SoapMessageImpl> {

    private static final Logger LOG =
            LoggerFactory.getLogger(XRoadSoapMessageConverter.class);

    @Setter
    private boolean includeLegacyHeaders = true;

    XRoadSoapMessageConverter(IdentifierMappingProvider identifierMapping) {
        super(identifierMapping);
    }

    @Override
    public SoapMessageImpl convert(final XRoadSoapMessageImpl xroadMessage)
            throws Exception {
        LOG.trace("sdsbSoapMessage()");

        ClientId sender = getClientId(xroadMessage.getConsumer());

        ServiceId receiver = ServiceId.create(
                getClientId(xroadMessage.getProducer()),
                xroadMessage.getServiceName(),
                xroadMessage.getServiceVersion());

        String userId = xroadMessage.getUserId();
        String queryId = xroadMessage.getQueryId();

        SdsbSoapHeader sdsbHeader = new SdsbSoapHeader();
        sdsbHeader.setClient(sender);
        sdsbHeader.setService(receiver);
        sdsbHeader.setUserId(userId);
        sdsbHeader.setQueryId(queryId);
        sdsbHeader.setAsync(xroadMessage.isAsync());

        // All X-Road headers into xroadHeaders element
        if (includeLegacyHeaders) {
            sdsbHeader.setXroadHeader(createXRoadHeaderFields(
                    xroadMessage.getSoap().getSOAPHeader()));
        }

        final SOAPBody xroadBody = xroadMessage.getSoap().getSOAPBody();
        SoapBuilder builder = new SoapBuilder() {
            @Override
            protected void addNamespaces(SOAPMessage soapMessage,
                    boolean rpcEncoded) throws SOAPException {
                super.addNamespaces(soapMessage, rpcEncoded);

                // Add all namespaces from the X-Road message
                addXRoadNamespaces(
                        xroadMessage.getSoap().getSOAPPart().getEnvelope(),
                        soapMessage.getSOAPPart().getEnvelope());
            }
        };

        builder.setHeader(sdsbHeader);
        builder.setRpcEncoded(xroadMessage.isRpcEncoded());
        builder.setCreateBodyCallback(new SoapBuilder.SoapBodyCallback() {
            @Override
            public void create(SOAPBody soapBody) throws Exception {
                replaceBody(soapBody, xroadBody);
            }
        });

        SoapMessageImpl sdsbMessage = builder.build();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Converted X-Road 5.0 SOAP '{}' to SDSB SOAP '{}'",
                    prettyPrintXml(xroadMessage), prettyPrintXml(sdsbMessage));
        }

        return sdsbMessage;
    }

    public static SoapMessageImpl removeXRoadHeaders(SoapMessageImpl message)
            throws Exception {
        LOG.trace("removeXRoadHeaders()");

        SdsbSoapHeader sdsbHeader = new SdsbSoapHeader();
        sdsbHeader.setClient(message.getClient());
        sdsbHeader.setService(message.getService());
        sdsbHeader.setUserId(message.getUserId());
        sdsbHeader.setQueryId(message.getQueryId());
        sdsbHeader.setAsync(message.isAsync());

        final SOAPBody xroadBody = message.getSoap().getSOAPBody();
        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(sdsbHeader);
        builder.setRpcEncoded(message.isRpcEncoded());
        builder.setCreateBodyCallback(new SoapBuilder.SoapBodyCallback() {
            @Override
            public void create(SOAPBody soapBody) throws Exception {
                replaceBody(soapBody, xroadBody);
            }
        });

        SoapMessageImpl sdsbMessage = builder.build();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Removed X-Road headers from SDSB SOAP '{}'",
                    prettyPrintXml(sdsbMessage));
        }

        return sdsbMessage;
    }

    private ClientId getClientId(String shortName) throws Exception {
        ClientId clientId = getIdentifierMapping().getClientId(shortName);
        if (clientId == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "No mapping found for short name '%s'", shortName);
        }

        return clientId;
    }

    private static XRoadHeaderFields createXRoadHeaderFields(
            SOAPHeader xroadHeader) throws Exception {
        XRoadHeaderFields fields = new XRoadHeaderFields();

        NodeList nodes = xroadHeader.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof SOAPElement) {
                fields.add((SOAPElement) node);
            }
        }

        return fields;
    }

    private static void addXRoadNamespaces(SOAPEnvelope sourceEnvelope,
            SOAPEnvelope targetEnvelope) throws SOAPException {
        Iterator<?> prefixes = sourceEnvelope.getNamespacePrefixes();
        while (prefixes.hasNext()) {
            String nsPrefix = prefixes.next().toString();
            String nsURI = sourceEnvelope.getNamespaceURI(nsPrefix);
            targetEnvelope.addNamespaceDeclaration(nsPrefix, nsURI);
        }
    }

    private static void replaceBody(SOAPBody oldBody, SOAPBody newBody) {
        Document doc = oldBody.getOwnerDocument();
        NodeList nl = newBody.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node newNode = doc.importNode(nl.item(i), true);
            oldBody.appendChild(newNode);
        }
    }
}
