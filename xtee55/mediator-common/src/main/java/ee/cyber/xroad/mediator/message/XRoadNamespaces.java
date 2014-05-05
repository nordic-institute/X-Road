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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import ee.cyber.sdsb.common.message.SoapHeader;
import ee.cyber.sdsb.common.message.SoapUtils;

import static ee.cyber.xroad.mediator.message.XRoadSoapNamespacePrefixMapper.getPrefix;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class XRoadNamespaces {

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
        SOAP_HEADER_REGISTRY.put(SoapHeader.NS_SDSB, SoapHeader.class);
        SOAP_HEADER_REGISTRY.put(NS_RPC, XRoadRpcSoapHeader.class);
        SOAP_HEADER_REGISTRY.put(NS_DL_EE, XRoadDlSoapHeader.EE.class);
        SOAP_HEADER_REGISTRY.put(NS_DL_EU, XRoadDlSoapHeader.EU.class);
        SOAP_HEADER_REGISTRY.put(NS_DL_XX, XRoadDlSoapHeader.XX.class);
    }

    public static Class<?> getSoapHeaderClass(SOAPMessage soap)
            throws Exception {
        if (soap.getSOAPHeader() != null) {
            return getSoapHeaderClass(getNamespaceURIs(soap),
                    soap.getSOAPHeader());
        }

        return getSoapHeaderClass(getNamespaceURIs(soap));
    }

    public static Class<?> getSoapHeaderClass(Set<String> nsURIs)
            throws Exception {
        for (String nsURI : SOAP_HEADER_REGISTRY.keySet()) {
            if (nsURIs.contains(nsURI)) {
                return SOAP_HEADER_REGISTRY.get(nsURI);
            }
        }

        return null;
    }

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

    public static void addNamespaceDeclarations(SOAPMessage soap,
            Class<?> soapHeaderClass) throws Exception {
        for (Map.Entry<String, Class<?>> entry :
                SOAP_HEADER_REGISTRY.entrySet()) {
            if (entry.getValue().equals(soapHeaderClass)) {
                soap.getSOAPPart().getEnvelope().addNamespaceDeclaration(
                        getPrefix(entry.getKey()), entry.getKey());
                return;
            }
        }
    }

    public static Set<String> getNamespaceURIs(SOAPMessage soap)
            throws Exception {
        Set<String> nsURIs = new HashSet<>();
        nsURIs.addAll(SoapUtils.getNamespaceURIs(soap));
        nsURIs.addAll(getNamespaceURIs(soap.getSOAPHeader()));
        return nsURIs;
    }

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
