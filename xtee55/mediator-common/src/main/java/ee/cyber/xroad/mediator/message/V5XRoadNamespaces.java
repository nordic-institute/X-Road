package ee.cyber.xroad.mediator.message;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;

import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapUtils;

import static ee.cyber.xroad.mediator.message.V5XRoadSoapNamespacePrefixMapper.getPrefix;

/**
 * Contains constants and utility methods for working with X-Road 5.0 namespaces.
 */
public final class V5XRoadNamespaces {

    public static final String NS_RPC = "http://x-tee.riik.ee/xsd/xtee.xsd";
    public static final String PREFIX_RPC = "xtee";

    public static final String NS_DL_EU = "http://x-road.eu/xsd/x-road.xsd";
    public static final String PREFIX_DL_EU = "xrd";

    public static final String NS_DL_EE = "http://x-road.ee/xsd/x-road.xsd";
    public static final String PREFIX_DL_EE = "xrd";

    public static final String NS_DL_XX = "http://x-rd.net/xsd/xroad.xsd";
    public static final String PREFIX_DL_XX = "xrd";

    private static final Map<String, Class<?>> SOAP_HEADER_REGISTRY =
            new LinkedHashMap<>();
    static {
        SOAP_HEADER_REGISTRY.put(SoapHeader.NS_XROAD, SoapHeader.class);
        SOAP_HEADER_REGISTRY.put(NS_RPC, V5XRoadRpcSoapHeader.class);
        SOAP_HEADER_REGISTRY.put(NS_DL_EE, V5XRoadDlSoapHeader.EE.class);
        SOAP_HEADER_REGISTRY.put(NS_DL_EU, V5XRoadDlSoapHeader.EU.class);
        SOAP_HEADER_REGISTRY.put(NS_DL_XX, V5XRoadDlSoapHeader.XX.class);
    }

    private V5XRoadNamespaces() {
    }

    /**
     * @param soap the SOAP message
     * @return the SOAP header class that's appropriate for the namespaces contained
     * in the given SOAP message
     * @throws Exception in case of any errors
     */
    public static Class<?> getSoapHeaderClass(SOAPMessage soap)
            throws Exception {
        if (soap.getSOAPHeader() != null) {
            return getSoapHeaderClass(getNamespaceURIs(soap),
                    soap.getSOAPHeader());
        }

        return getSoapHeaderClass(getNamespaceURIs(soap));
    }

    /**
     * @param nsURIs set of namespace URIs
     * @return the SOAP header class that's appropriate for the given namespaces
     * @throws Exception in case of any errors
     */
    public static Class<?> getSoapHeaderClass(Set<String> nsURIs)
            throws Exception {
        for (String nsURI : SOAP_HEADER_REGISTRY.keySet()) {
            if (nsURIs.contains(nsURI)) {
                return SOAP_HEADER_REGISTRY.get(nsURI);
            }
        }

        return null;
    }

    /**
     * @param nsURIs set of namespace URIs
     * @param soapHeader the SOAP header
     * @return the SOAP header class that's appropriate for the given namespaces
     * that are present in the given SOAP message header
     * @throws Exception in case of any errors
     */
    public static Class<?> getSoapHeaderClass(Set<String> nsURIs,
            SOAPHeader soapHeader) throws Exception {
        for (String nsURI : SOAP_HEADER_REGISTRY.keySet()) {
            if (nsURIs.contains(nsURI)) {
                Class<?> headerClass = SOAP_HEADER_REGISTRY.get(nsURI);
                if (soapHeaderContainsFields(soapHeader, headerClass)) {
                    return headerClass;
                }
            }
        }

        return null;
    }

    private static boolean soapHeaderContainsFields(SOAPHeader soapHeader,
            Class<?> headerClass) {
        for (Field field : headerClass.getDeclaredFields()) {
            XmlElement a = field.getAnnotation(XmlElement.class);
            if (a != null && a.required()
                    && soapHeader.getChildElements(
                            new QName(a.namespace(), a.name())).hasNext()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Adds namespace declaration to the given SOAP message that are appropriate
     * for the specified SOAP header class
     * @param soap the SOAP message
     * @param soapHeaderClass the SOAP header class
     * @throws Exception in case of any errors
     */
    public static void addNamespaceDeclarations(SOAPMessage soap,
            Class<?> soapHeaderClass) throws Exception {
        for (Map.Entry<String, Class<?>> entry
                : SOAP_HEADER_REGISTRY.entrySet()) {
            if (entry.getValue().equals(soapHeaderClass)) {
                soap.getSOAPPart().getEnvelope().addNamespaceDeclaration(
                        getPrefix(entry.getKey()), entry.getKey());
                return;
            }
        }
    }

    /**
     * @param soap the SOAP message
     * @return set of namespace URIs present in the given SOAP message
     * @throws Exception in case of any errors
     */
    public static Set<String> getNamespaceURIs(SOAPMessage soap)
            throws Exception {
        Set<String> nsURIs = new HashSet<>();
        nsURIs.addAll(SoapUtils.getNamespaceURIs(soap));
        nsURIs.addAll(getNamespaceURIs(soap.getSOAPHeader()));
        return nsURIs;
    }

    /**
     *
     * @param soapElement the SOAP message element
     * @return set of namespace URIs present in the given SOAP message element
     * @throws Exception in case of any errors
     */
    public static Set<String> getNamespaceURIs(SOAPElement soapElement)
            throws Exception {
        Set<String> nsURIs = new HashSet<>();

        if (soapElement != null) {
            Iterator<?> it = soapElement.getNamespacePrefixes();
            while (it.hasNext()) {
                nsURIs.add(soapElement.getNamespaceURI((String) it.next()));
            }

            for (SOAPElement child : SoapUtils.getChildElements(soapElement)) {
                nsURIs.addAll(getNamespaceURIs(child));
            }
        }

        return nsURIs;
    }

}
