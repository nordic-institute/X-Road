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

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.JaxbUtils;
import ee.cyber.sdsb.common.message.SoapHeader;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapUtils;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;

import static ee.cyber.sdsb.common.ErrorCodes.X_INCONSISTENT_HEADERS;
import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.message.SoapParserImpl.unmarshalHeader;
import static ee.cyber.sdsb.common.message.SoapUtils.getServiceName;
import static ee.cyber.sdsb.common.message.SoapUtils.isRpcMessage;
import static ee.cyber.xroad.mediator.message.XRoadNamespaces.addNamespaceDeclarations;

/**
 * Converts SDSB SOAP messages to X-Road 5.0 SOAP messages.
 *
 * Note, that conversions between encodings (RCP <-> D/L) are not supported,
 * thus if the input message is RPC encoded, the output message is also
 * RPC encoded.
 */
class SdsbSoapMessageConverter extends
        AbstractMessageConverter<SoapMessageImpl, XRoadSoapMessageImpl> {

    private static final Logger LOG =
            LoggerFactory.getLogger(SdsbSoapMessageConverter.class);

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

    SdsbSoapMessageConverter(IdentifierMappingProvider identifierMapping) {
        super(identifierMapping);
    }

    @Override
    public XRoadSoapMessageImpl convert(SoapMessageImpl message)
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

        AbstractXRoadSoapHeader header =
                (AbstractXRoadSoapHeader) xroadHeaderClass.newInstance();
        XRoadHeaderFields xroadHeaderFields = getXRoadHeaderFields(message);
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
        XRoadSoapMessageImpl xroadMessage =
                new XRoadSoapMessageImpl(xml.getBytes(message.getCharset()),
                        message.getCharset(), header, soap,
                        getServiceName(soap.getSOAPBody()));

        if (LOG.isTraceEnabled()) {
            LOG.trace("Converted SDSB SOAP '{}' to X-Road 5.0 SOAP '{}'",
                    prettyPrintXml(message), xml);
        }

        return xroadMessage;
    }

    private AbstractXRoadSoapHeader createXRoadHeader(XRoadSoapHeader header,
            SOAPMessage soap, XRoadHeaderFields fields) throws Exception {
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

    private static void verifyXRoadHeader(XRoadSoapHeader xroadHeader,
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

    private static XRoadHeaderFields getXRoadHeaderFields(
            SoapMessageImpl sdsbMessage) {
        if (sdsbMessage.getHeader() instanceof SdsbSoapHeader) {
            return ((SdsbSoapHeader) sdsbMessage.getHeader()).getXroadHeader();
        }

        return null;
    }

    public static Class<?> getSoapHeaderClass(SOAPMessage soap)
            throws Exception {
        if (isRpcMessage(soap)) {
            return XRoadRpcSoapHeader.class;
        }

        Set<String> nsURIs = XRoadNamespaces.getNamespaceURIs(soap);
        // Since we are converting from SDSB to X-Road, we filter out the
        // SDSB namespace so we do not get SDSB header class,
        // even of the SOAP message contains the SDSB namespace.
        nsURIs.remove(SoapHeader.NS_SDSB);
        nsURIs.remove(XRoadNamespaces.NS_RPC);

        Class<?> clazz = XRoadNamespaces.getSoapHeaderClass(nsURIs);
        if (clazz == null) {
            clazz = XRoadDlSoapHeader.EE.class; // XXX: Which default?
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
                new XRoadSoapNamespacePrefixMapper());
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(newHeader, envelope);

        envelope.appendChild(soapBody);
    }
}
