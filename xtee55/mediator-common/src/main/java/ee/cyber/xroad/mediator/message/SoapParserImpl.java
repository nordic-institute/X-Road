package ee.cyber.xroad.mediator.message;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapUtils;

import static ee.cyber.xroad.mediator.message.V5XRoadNamespaces.getSoapHeaderClass;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_BODY;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_MESSAGE;
import static ee.ria.xroad.common.message.SoapUtils.getServiceName;

/**
 * An implementation of Soap parser that supports X-Road 6.0 SOAP message and legacy
 * X-Road 5.0 SOAP message.
 */
@Slf4j
public class SoapParserImpl
        extends ee.ria.xroad.common.message.SoapParserImpl {

    @Override
    protected void validateAgainstSoapSchema(SOAPMessage soap)
            throws Exception {
        // Disable SOAP schema validation, because X-Road 5.0 SOAP messages
        // might contain non-valid elements.
    }

    @Override
    protected Soap createMessage(byte[] rawXml, SOAPMessage soap,
            String charset, String originalContentType) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Parsing SOAP: {}", new String(rawXml, charset));
        }

        Class<?> soapHeaderClass = getSoapHeaderClass(soap);

        SOAPElement serviceElement = getServiceElement(soap.getSOAPBody());

        // Special cases -- meta services
        String serviceName = serviceElement.getLocalName();
        if (!isXRoadMessage(soapHeaderClass)) {
            if (V5XRoadMetaServiceImpl.isMetaService(serviceName)) {
                log.debug("Reading X-Road 5.0 meta service '{}'", serviceName);

                return createXRoadMetaServiceMessage(rawXml, soap, charset,
                        getMetaServiceSoapHeaderClass(soap, serviceElement),
                        serviceName, originalContentType);
            }
        }

        if (soapHeaderClass == null) {
            log.error("Unknown SOAP:\n{}", new String(rawXml, charset));

            throw new CodedException(X_INVALID_MESSAGE,
                    "Unable to determine SOAP version");
        }

        if (isXRoadMessage(soapHeaderClass)) {
            return createXRoadMessage(rawXml, soap, charset, originalContentType);
        } else if (isV5XRoadMessage(soapHeaderClass)) {
            return createXRoadMessage(rawXml, soap, charset, soapHeaderClass,
                    originalContentType);
        }

        log.error("Received unknown SOAP message: {}",
                new String(rawXml, charset));
        throw new CodedException(X_INVALID_MESSAGE, "Unknown SOAP version");
    }

    private Soap createXRoadMessage(byte[] rawXml, SOAPMessage soap,
            String charset, String originalContentType) throws Exception {
        // Request and response messages must have a header,
        // fault messages may or may not have a header.
        SoapHeader h = null;
        if (soap.getSOAPHeader() != null) {
            validateSOAPHeader(soap.getSOAPHeader());
            h = unmarshalHeader(XRoadSoapHeader.class, soap.getSOAPHeader());
        }

        log.debug("Reading X-Road 6.0 SOAP message");

        return createMessage(rawXml, h, soap, charset, originalContentType);
    }

    private static Soap createXRoadMessage(byte[] xml, SOAPMessage soap,
            String charset, Class<?> xroadHeaderClass,
            String originalContentType) throws Exception {
        SOAPHeader soapHeader = soap.getSOAPHeader();
        if (soapHeader == null) {
            throw new CodedException(X_INVALID_MESSAGE, "Header missing");
        }

        validateSOAPHeader(soapHeader);

        AbstractV5XRoadSoapHeader xroadHeader =
                unmarshalHeader(xroadHeaderClass, soapHeader);

        log.debug("Reading X-Road 5.0 SOAP {} message",
                xroadHeader instanceof V5XRoadRpcSoapHeader
                        ? "(RPC encoded)" : "(D/L wrapped)");

        return new V5XRoadSoapMessageImpl(xml, charset, xroadHeader, soap,
                getServiceName(soap.getSOAPBody()), originalContentType);
    }

    private static Soap createXRoadMetaServiceMessage(byte[] xml,
            SOAPMessage soap, String charset, Class<?> xroadHeaderClass,
            String serviceName, String originalContentType) throws Exception {
        AbstractV5XRoadSoapHeader xroadHeader;
        SOAPHeader soapHeader = soap.getSOAPHeader();
        if (soapHeader != null) {
            xroadHeader = unmarshalHeader(xroadHeaderClass, soapHeader);
        } else {
            // Apparently, X-Road 5.0 meta-service responses might not contain
            // headers, so we just create dummy headers if they are missing.
            xroadHeader =
                    (AbstractV5XRoadSoapHeader) xroadHeaderClass.newInstance();
            xroadHeader.setConsumer("dummy");
            xroadHeader.setProducer("dummy");
            xroadHeader.setService("dummy." + serviceName);
            xroadHeader.setQueryId("dummy");
            xroadHeader.setUserId("dummy");
        }

        return new V5XRoadMetaServiceImpl(xml, charset, xroadHeader, soap,
                originalContentType);
    }

    private static SOAPElement getServiceElement(SOAPBody soapBody) {
        List<SOAPElement> children = SoapUtils.getChildElements(soapBody);
        if (children.size() != 1) {
            throw new CodedException(X_INVALID_BODY,
                    "Malformed SOAP message: "
                            + "body must have exactly one child element");
        }

        return children.get(0);
    }

    private static Class<?> getMetaServiceSoapHeaderClass(SOAPMessage soap,
            SOAPElement serviceElement) throws Exception {
        Class<?> soapHeaderClass;
        if (soap.getSOAPHeader() != null) {
            soapHeaderClass = getSoapHeaderClass(soap);
            if (soapHeaderClass != null) {
                return soapHeaderClass;
            }
        }

        String nsURI = V5XRoadNamespaces.NS_DL_EE;
        String nsPrefix = serviceElement.getPrefix();
        if (nsPrefix != null) {
            String ns = soap.getSOAPPart().lookupNamespaceURI(nsPrefix);
            if (ns != null) {
                nsURI = ns;
            }
        }

        soapHeaderClass = getSoapHeaderClass(
                new HashSet<String>(Arrays.asList(nsURI)));
        if (soapHeaderClass == null) {
            soapHeaderClass = V5XRoadDlSoapHeader.EE.class;
        }

        return soapHeaderClass;
    }

    private static boolean isXRoadMessage(Class<?> soapHeaderClass) {
        return soapHeaderClass != null
                && soapHeaderClass.equals(SoapHeader.class);
    }

    private static boolean isV5XRoadMessage(Class<?> soapHeaderClass) {
        return soapHeaderClass != null
                && V5XRoadSoapHeader.class.isAssignableFrom(soapHeaderClass);
    }
}
