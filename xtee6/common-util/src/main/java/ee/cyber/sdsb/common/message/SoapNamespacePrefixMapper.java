package ee.cyber.sdsb.common.message;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

/**
 * Maps namespace URIs to specified prefixes. This class is used when
 * marshalling JAXB objects to XML.
 */
public class SoapNamespacePrefixMapper extends NamespacePrefixMapper {

    @Override
    public String getPreferredPrefix(String namespaceUri, String suggestion,
            boolean requirePrefix) {
        switch (namespaceUri) {
            case SoapHeader.NS_SDSB:
                return SoapHeader.PREFIX_SDSB;
            case SoapUtils.NS_SOAPENV:
                return SoapUtils.PREFIX_SOAPENV;
            default:
                return null;
        }
    }

}
