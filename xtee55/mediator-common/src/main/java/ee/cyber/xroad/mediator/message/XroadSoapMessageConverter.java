package ee.cyber.xroad.mediator.message;

import java.util.Set;

import javax.xml.bind.Marshaller;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPMessage;

import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.JaxbUtils;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;

import static ee.cyber.xroad.mediator.message.V5XRoadNamespaces.addNamespaceDeclarations;
import static ee.ria.xroad.common.ErrorCodes.X_INCONSISTENT_HEADERS;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.message.SoapParserImpl.unmarshalHeader;
import static ee.ria.xroad.common.message.SoapUtils.getServiceName;
import static ee.ria.xroad.common.message.SoapUtils.isRpcMessage;

/**
 * Converts X-Road 6.0 SOAP messages to X-Road 5.0 SOAP messages.
 *
 * Note, that conversions between encodings (RCP <-> D/L) are not supported,
 * thus if the input message is RPC encoded, the output message is also
 * RPC encoded.
 */
class XroadSoapMessageConverter extends
        AbstractMessageConverter<SoapMessageImpl, V5XRoadSoapMessageImpl> {

    private static final Logger LOG =
            LoggerFactory.getLogger(XroadSoapMessageConverter.class);

    private static final SOAPFactory SOAP_FACTORY = initSOAPFactory();

    private static SOAPFactory initSOAPFactory() {
        try {
            return SOAPFactory.newInstance();
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    @Setter
    private Class<?> destinationSoapHeaderClass;

    XroadSoapMessageConverter(IdentifierMappingProvider identifierMapping) {
        super(identifierMapping);
    }

    @Override
    public V5XRoadSoapMessageImpl convert(SoapMessageImpl message)
            throws Exception {
        LOG.trace("convert()");

        if (message.getCentralService() != null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Converting message with central services not suported");
        }

        ServiceId serviceId = message.getService();

        String consumer = getShortName(message.getClient());
        String producer = getShortName(serviceId.getClientId());
        String service = getXRoadService(producer, serviceId);

        SOAPMessage soap = cloneMessage(message.getSoap());

        Class<?> xroadHeaderClass = destinationSoapHeaderClass != null
                ? destinationSoapHeaderClass : getSoapHeaderClass(soap);
        addNamespaceDeclarations(soap, xroadHeaderClass);

        AbstractV5XRoadSoapHeader header =
                (AbstractV5XRoadSoapHeader) xroadHeaderClass.newInstance();
        V5XRoadHeaderFields xroadHeaderFields = getXRoadHeaderFields(message);
        if (xroadHeaderFields != null) {
            header = createXRoadHeader(header, soap, xroadHeaderFields);
            verifyXRoadHeader(header, consumer, producer, service);
        } else {
            header.setConsumer(consumer);
            header.setProducer(producer);
            header.setService(service);
            header.setQueryId(message.getQueryId());
            header.setUserId(message.getUserId());
            header.setAsync(message.isAsync());
            marshalHeader(soap, header);
        }

        String xml = prettyPrintXml(soap, message.getCharset());
        V5XRoadSoapMessageImpl xroadMessage =
                new V5XRoadSoapMessageImpl(xml.getBytes(message.getCharset()),
                        message.getCharset(), header, soap,
                        getServiceName(soap.getSOAPBody()));

        if (LOG.isTraceEnabled()) {
            LOG.trace("Converted X-Road 6.0 SOAP '{}' to X-Road 5.0 SOAP '{}'",
                    prettyPrintXml(message), xml);
        }

        return xroadMessage;
    }

    private AbstractV5XRoadSoapHeader createXRoadHeader(V5XRoadSoapHeader header,
            SOAPMessage soap, V5XRoadHeaderFields fields) throws Exception {
        SOAPEnvelope envelope = soap.getSOAPPart().getEnvelope();
        soap.getSOAPHeader().removeContents();

        for (Element field : fields.getFields()) {
            SOAPElement se = SOAP_FACTORY.createElement(field);
            for (String nsPrefix : SoapUtils.getNamespacePrefixes(se)) {
                String nsURI = se.getNamespaceURI(nsPrefix);
                envelope.addNamespaceDeclaration(nsPrefix, nsURI);
                se.removeNamespaceDeclaration(nsPrefix);
            }

            soap.getSOAPHeader().addChildElement(se);
        }

        return unmarshalHeader(header.getClass(), soap.getSOAPHeader());
    }

    private String getShortName(ClientId clientId) throws Exception {
        String shortName = getIdentifierMapping().getShortName(clientId);
        if (shortName == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "No mapping found for identifier '%s'", clientId);
        }

        return shortName;
    }

    private static String getXRoadService(String producer,
            ServiceId serviceId) {
        String service = producer + "." + serviceId.getServiceCode();

        if (!StringUtils.isEmpty(serviceId.getServiceVersion())) {
            service += "." + serviceId.getServiceVersion();
        }

        return service;
    }

    private static void verifyXRoadHeader(V5XRoadSoapHeader xroadHeader,
            String consumer, String producer, String service) {
        verifyXRoadHeaderField("consumer", xroadHeader.getConsumer(), consumer);
        verifyXRoadHeaderField("producer", xroadHeader.getProducer(), producer);
        verifyXRoadHeaderField("service", xroadHeader.getService(), service);
    }

    private static void verifyXRoadHeaderField(String fieldName,
            String expectedValue, String actualValue) {
        if (!expectedValue.equals(actualValue)) {
            throw new CodedException(X_INCONSISTENT_HEADERS,
                    "Expected field '%s' value '%s', but was '%s'",
                    fieldName, expectedValue, actualValue);
        }
    }

    private static V5XRoadHeaderFields getXRoadHeaderFields(
            SoapMessageImpl xroadMessage) {
        if (xroadMessage.getHeader() instanceof XroadSoapHeader) {
            return ((XroadSoapHeader) xroadMessage.getHeader()).getXroadHeader();
        }

        return null;
    }

    public static Class<?> getSoapHeaderClass(SOAPMessage soap)
            throws Exception {
        if (isRpcMessage(soap)) {
            return V5XRoadRpcSoapHeader.class;
        }

        Set<String> nsURIs = V5XRoadNamespaces.getNamespaceURIs(soap);
        // Since we are converting from X-Road 6.0 to X-Road 5.0, we filter
        // out the X-Road 6.0 namespace so we do not get X-Road 6.0 header
        // class, even of the SOAP message contains the X-Road 6.0 namespace.
        nsURIs.remove(SoapHeader.NS_XROAD);
        nsURIs.remove(V5XRoadNamespaces.NS_RPC);

        Class<?> clazz = V5XRoadNamespaces.getSoapHeaderClass(nsURIs);
        if (clazz == null) {
            clazz = V5XRoadDlSoapHeader.EE.class; // XXX: Which default?
        }

        return clazz;
    }

    private static void marshalHeader(SOAPMessage message, Object newHeader)
            throws Exception {
        // Since we are marshaling the header into the SOAPEnvelope object
        // (creating a new SOAPHeader element), we need to remove the existing
        // SOAPHeader element and SOAPBody from the Envelope, then marshal
        // the header and add the body back to get correct order of elements.
        SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();
        envelope.removeChild(message.getSOAPHeader());

        Node soapBody = envelope.removeChild(message.getSOAPBody());
        envelope.removeContents(); // removes newlines etc.

        Marshaller marshaller =
                JaxbUtils.createMarshaller(newHeader.getClass());
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper",
                new V5XRoadSoapNamespacePrefixMapper());
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(newHeader, envelope);

        envelope.appendChild(soapBody);
    }
}
