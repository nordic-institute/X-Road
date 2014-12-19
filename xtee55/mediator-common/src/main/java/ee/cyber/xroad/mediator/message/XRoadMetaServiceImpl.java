package ee.cyber.xroad.mediator.message;

import javax.xml.soap.SOAPMessage;

import ee.cyber.sdsb.common.message.SoapUtils;

public class XRoadMetaServiceImpl extends XRoadSoapMessageImpl {

    public static final String LIST_METHODS = "listMethods";
    public static final String ALLOWED_METHODS = "allowedMethods";
    public static final String TEST_SYSTEM = "testSystem";
    public static final String GET_CHARGE = "getCharge";
    public static final String LOAD_CLASSIFICATOR = "loadClassificator";
    public static final String LIST_PRODUCERS = "listProducers";
    public static final String ASYNC_NEXT = "asyncNext";
    public static final String ASYNC_LAST = "asyncLast";
    public static final String LIST_CONSUMERS = "listConsumers";
    public static final String LIST_GROUPS = "listGroups";
    public static final String GET_PRODUCER_DATA = "getProducerData";
    public static final String GET_CONSUMER_DATA = "getConsumerData";
    public static final String GET_GROUP_DATA = "getGroupData";
    public static final String GET_PRODUCER_ACL = "getProducerACL";
    public static final String GET_SERVICE_ACL = "getServiceACL";
    public static final String GET_STATE = "getState";
    public static final String GET_METHODS = "getMethods";
    public static final String LOG_ONLY = "logOnly";
    public static final String LEGACY_XYZ = "legacy";

    XRoadMetaServiceImpl(byte[] xml, String charset, XRoadSoapHeader header,
            SOAPMessage soap) throws Exception {
        super(xml, charset, header, soap,
                SoapUtils.getServiceName(soap.getSOAPBody()));
    }

    public static boolean isMetaService(String serviceName) {
        if (serviceName == null) {
            return false;
        }

        switch (getActualServiceName(serviceName)) {
            case LIST_METHODS:
            case ALLOWED_METHODS:
            case TEST_SYSTEM:
            case GET_CHARGE:
            case LOAD_CLASSIFICATOR:
            case LIST_PRODUCERS:
            case ASYNC_NEXT:
            case ASYNC_LAST:
            case LIST_CONSUMERS:
            case LIST_GROUPS:
            case GET_PRODUCER_DATA:
            case GET_CONSUMER_DATA:
            case GET_GROUP_DATA:
            case GET_PRODUCER_ACL:
            case GET_SERVICE_ACL:
            case GET_STATE:
            case GET_METHODS:
            case LOG_ONLY:
                return true;
            default:
                return serviceName.startsWith(LEGACY_XYZ);
        }
    }

    private static String getActualServiceName(String serviceName) {
        if (serviceName.endsWith("Response")) {
            return serviceName.substring(0, serviceName.indexOf("Response"));
        }

        return serviceName;
    }
}
