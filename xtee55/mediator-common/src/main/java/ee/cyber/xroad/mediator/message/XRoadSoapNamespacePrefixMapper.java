package ee.cyber.xroad.mediator.message;

import ee.cyber.sdsb.common.message.SoapNamespacePrefixMapper;

import static ee.cyber.xroad.mediator.message.XRoadNamespaces.*;

public class XRoadSoapNamespacePrefixMapper extends SoapNamespacePrefixMapper {

    @Override
    public String getPreferredPrefix(String namespaceUri,
            String suggestion, boolean requirePrefix) {
        String prefix = getPrefix(namespaceUri);
        if (prefix != null) {
            return prefix;
        }

        return super.getPreferredPrefix(namespaceUri, suggestion,
                requirePrefix);
    }

    public static String getPrefix(String namespaceUri) {
        switch (namespaceUri) {
            case NS_RPC:
                return PREFIX_RPC;
            case NS_DL_EU:
                return PREFIX_DL_EU;
            case NS_DL_EE:
                return PREFIX_DL_EE;
            case NS_DL_XX:
                return PREFIX_DL_XX;
            default:
                return null;
        }
    }
}
