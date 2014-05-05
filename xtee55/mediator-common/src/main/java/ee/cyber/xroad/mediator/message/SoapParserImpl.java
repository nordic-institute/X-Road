package ee.cyber.xroad.mediator.message;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapHeader;
import ee.cyber.sdsb.common.message.SoapUtils;

import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_BODY;
import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_MESSAGE;
import static ee.cyber.sdsb.common.message.SoapUtils.getServiceName;
import static ee.cyber.sdsb.common.message.SoapUtils.isRpcMessage;
import static ee.cyber.xroad.mediator.message.XRoadNamespaces.getSoapHeaderClass;

/**
 * An implementation of Soap parser that supports SDSB SOAP message and legacy
 * X-Road 5.0 SOAP message.
 */
public class SoapParserImpl
        extends ee.cyber.sdsb.common.message.SoapParserImpl {

    private static final Logger LOG =
            LoggerFactory.getLogger(SoapParserImpl.class);

    @Override
    protected void validateAgainstSoapSchema(SOAPMessage soap)
            throws Exception {
        // Disable SOAP schema validation, because X-Raod 5.0 SOAP messages
        // might contain non-valid elements.
    }

    @Override
    protected Soap createMessage(byte[] rawXml, SOAPMessage soap,
            String charset) throws Exception {
        String xml = new String(rawXml, charset);
        LOG.trace("Parsing SOAP: {}", xml);

        SOAPElement serviceElement = getServiceElement(soap.getSOAPBody());

        // Special cases -- meta services
        String serviceName = serviceElement.getLocalName();
        if (serviceName.startsWith(XRoadMetaServiceImpl.LIST_METHODS)) {
            LOG.debug("Reading X-Road 5.0 ListMethods");

            // TODO: In the future, ServiceMediator should respond to ListMethods
            return new XRoadListMethods(xml, charset, soap, serviceName,
                    isRpcMessage(soap));
        } else if (serviceName.startsWith(XRoadMetaServiceImpl.TEST_SYSTEM)) {
            LOG.debug("Reading X-Road 5.0 TestSystem");

            return new XRoadTestSystem(xml, charset, soap, isRpcMessage(soap));
        } else if (XRoadMetaServiceImpl.isMetaService(serviceName)) {
            LOG.debug("Reading X-Road 5.0 meta service '{}'", serviceName);

            return createXRoadMetaServiceMessage(xml, soap, charset,
                    getMetaServiceSoapHeaderClass(soap, serviceElement),
                    serviceName);
        }

        Class<?> soapHeaderClass = getSoapHeaderClass(soap);
        if (soapHeaderClass == null) {
            LOG.error("Unknown SOAP:\n{}", xml);

            throw new CodedException(X_INVALID_MESSAGE,
                    "Unable to determine SOAP version");
        }

        if (soapHeaderClass.equals(SoapHeader.class)) {
            return createSdsbMessage(rawXml, soap, charset);
        } else if (XRoadSoapHeader.class.isAssignableFrom(soapHeaderClass)) {
            return createXRoadMessage(xml, soap, charset, soapHeaderClass);
        }

        LOG.error("Received unknown SOAP message: {}", xml);
        throw new CodedException(X_INVALID_MESSAGE, "Unknown SOAP version");
    }

    private Soap createSdsbMessage(byte[] rawXml, SOAPMessage soap,
            String charset) throws Exception {
        // Request and response messages must have a header,
        // fault messages may or may not have a header.
        SoapHeader h = null;
        if (soap.getSOAPHeader() != null) {
            validateSOAPHeader(soap.getSOAPHeader());
            h = unmarshalHeader(SdsbSoapHeader.class, soap.getSOAPHeader());
        }

        LOG.debug("Reading SDSB SOAP message");

        return createMessage(rawXml, h, soap, charset);
    }

    private static Soap createXRoadMessage(String xml, SOAPMessage soap,
            String charset, Class<?> xroadHeaderClass) throws Exception {
        SOAPHeader soapHeader = soap.getSOAPHeader();
        if (soapHeader == null) {
            throw new CodedException(X_INVALID_MESSAGE, "Header missing");
        }

        validateSOAPHeader(soapHeader);

        AbstractXRoadSoapHeader xroadHeader =
                unmarshalHeader(xroadHeaderClass, soapHeader);

        LOG.debug("Reading X-Road 5.0 SOAP {} message",
                xroadHeader instanceof XRoadRpcSoapHeader
                        ? "(RPC encoded)" : "(D/L wrapped)");

        return new XRoadSoapMessageImpl(xml, charset, xroadHeader, soap,
                getServiceName(soap.getSOAPBody()));
    }

    private static Soap createXRoadMetaServiceMessage(String xml,
            SOAPMessage soap, String charset, Class<?> xroadHeaderClass,
            String serviceName) throws Exception {
        AbstractXRoadSoapHeader xroadHeader;
        SOAPHeader soapHeader = soap.getSOAPHeader();
        if (soapHeader != null) {
            xroadHeader = unmarshalHeader(xroadHeaderClass, soapHeader);
        } else {
            // Apparently, X-Road 5.0 meta-service responses might not contain
            // headers, so we just create dummy headers if they are missing.
            xroadHeader =
                    (AbstractXRoadSoapHeader) xroadHeaderClass.newInstance();
            xroadHeader.setConsumer("dummy");
            xroadHeader.setProducer("dummy");
            xroadHeader.setService("dummy." + serviceName);
            xroadHeader.setQueryId("dummy");
            xroadHeader.setUserId("dummy");
        }

        return new XRoadMetaServiceImpl(xml, charset, xroadHeader, soap);
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

        String nsURI = XRoadNamespaces.NS_DL_EE;
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
            soapHeaderClass = XRoadDlSoapHeader.EE.class;
        }

        return soapHeaderClass;
    }
}
