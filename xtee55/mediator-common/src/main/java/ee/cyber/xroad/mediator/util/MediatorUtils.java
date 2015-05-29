package ee.cyber.xroad.mediator.util;

import ee.cyber.xroad.mediator.message.V5XRoadSoapMessageImpl;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageImpl;

/**
 * Contains utility methods commonly used by mediators.
 */
public final class MediatorUtils {

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
}
