package ee.cyber.xroad.mediator.util;

import ee.cyber.xroad.mediator.message.V5XRoadSoapMessageImpl;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageImpl;

/**
 * Contains utility methods commonly used by mediators.
 */
public final class MediatorUtils {

    private static final int MIN_SERVICE_NAME_PARTS = 2;
    private static final int MAX_SERVICE_NAME_PARTS = 4;

    private MediatorUtils() {
    }

    /**
     * @param m the SOAP message
     * @return true, if provided message is X-Road 6.0 SOAP message.
     */
    public static boolean isV6XRoadSoapMessage(SoapMessage m) {
        return m instanceof SoapMessageImpl;
    }

    /**
     * @param m the SOAP message
     * @return true, if provided message is X-Road 5.0 SOAP message.
     */
    public static boolean isV5XRoadSoapMessage(SoapMessage m) {
        return m instanceof V5XRoadSoapMessageImpl;
    }

    /**
     * @param v5FullServiceName the full v5 service name
     * @return the V5 full service name is in format producer[.subsystem].query[.version]
     */
    public static String extractServiceCode(String v5FullServiceName) {
        String[] parts = v5FullServiceName.split("\\.");

        if (parts.length < MIN_SERVICE_NAME_PARTS
                || parts.length > MAX_SERVICE_NAME_PARTS) {
            return null;
        }

        if (parts[parts.length - 1].matches("^v[\\d]+$")) {
            return parts[parts.length - 2];
        } else {
            if (parts.length == MAX_SERVICE_NAME_PARTS) {
                return null;
            }

            return parts[parts.length - 1];
        }
    }

    /**
     * @param v5FullServiceName the full v5 service name
     * @return the V5 service version
     */
    public static String extractServiceVersion(String v5FullServiceName) {
        String[] parts = v5FullServiceName.split("\\.");

        if (parts[parts.length - 1].matches("v[\\d]+$")) {
            return parts[parts.length - 1];
        }

        return null;
    }
}
