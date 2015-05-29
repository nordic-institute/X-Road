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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Converts X-Road 5.0 SOAP messages to X-Road 6.0 SOAP messages.
 *
 * Note, that conversions between encodings (RCP <-> D/L) are not supported,
 * thus if the input message is RPC encoded, the output message is also
 * RPC encoded.
 */
class V5XRoadSoapMessageConverter extends
        AbstractMessageConverter<V5XRoadSoapMessageImpl, SoapMessageImpl> {

    private static final Logger LOG =
            LoggerFactory.getLogger(V5XRoadSoapMessageConverter.class);

    @Setter
    private boolean includeLegacyHeaders = true;

    V5XRoadSoapMessageConverter(IdentifierMappingProvider identifierMapping) {
        super(identifierMapping);
    }

    @Override
    public SoapMessageImpl convert(final V5XRoadSoapMessageImpl xroadMessage)
            throws Exception {
        LOG.trace("xroadSoapMessage()");

        ClientId sender = getClientId(xroadMessage.getConsumer());

        ServiceId receiver = ServiceId.create(
                getClientId(xroadMessage.getProducer()),
                xroadMessage.getServiceName(),
                xroadMessage.getServiceVersion());

        String userId = xroadMessage.getUserId();
        String queryId = xroadMessage.getQueryId();

        XroadSoapHeader xroadHeader = new XroadSoapHeader();
        xroadHeader.setClient(sender);
        xroadHeader.setService(receiver);
        xroadHeader.setUserId(userId);
        xroadHeader.setQueryId(queryId);
        xroadHeader.setAsync(xroadMessage.isAsync());

        // All X-Road headers into xroadHeaders element
        if (includeLegacyHeaders) {
            xroadHeader.setXroadHeader(createV5XRoadHeaderFields(
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

        builder.setHeader(xroadHeader);
        builder.setRpcEncoded(xroadMessage.isRpcEncoded());
        builder.setCreateBodyCallback(new SoapBuilder.SoapBodyCallback() {
            @Override
            public void create(SOAPBody soapBody) throws Exception {
                replaceBody(soapBody, xroadBody);
            }
        });

        SoapMessageImpl message = builder.build();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Converted X-Road 5.0 SOAP '{}' to X-Road 6.0 SOAP '{}'",
                    prettyPrintXml(xroadMessage), prettyPrintXml(message));
        }

        return message;
    }

    public static SoapMessageImpl removeXRoadHeaders(SoapMessageImpl message)
            throws Exception {
        LOG.trace("removeXRoadHeaders()");

        XroadSoapHeader xroadHeader = new XroadSoapHeader();
        xroadHeader.setClient(message.getClient());
        xroadHeader.setService(message.getService());
        xroadHeader.setUserId(message.getUserId());
        xroadHeader.setQueryId(message.getQueryId());
        xroadHeader.setAsync(message.isAsync());

        final SOAPBody xroadBody = message.getSoap().getSOAPBody();
        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(xroadHeader);
        builder.setRpcEncoded(message.isRpcEncoded());
        builder.setCreateBodyCallback(new SoapBuilder.SoapBodyCallback() {
            @Override
            public void create(SOAPBody soapBody) throws Exception {
                replaceBody(soapBody, xroadBody);
            }
        });

        SoapMessageImpl xroadMessage = builder.build();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Removed X-Road headers from X-Road 6.0 SOAP '{}'",
                    prettyPrintXml(xroadMessage));
        }

        return xroadMessage;
    }

    private ClientId getClientId(String shortName) throws Exception {
        ClientId clientId = getIdentifierMapping().getClientId(shortName);
        if (clientId == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "No mapping found for short name '%s'", shortName);
        }

        return clientId;
    }

    private static V5XRoadHeaderFields createV5XRoadHeaderFields(
            SOAPHeader xroadHeader) throws Exception {
        V5XRoadHeaderFields fields = new V5XRoadHeaderFields();

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
