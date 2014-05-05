package ee.cyber.xroad.mediator.util;

import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.xroad.mediator.message.XRoadSoapMessageImpl;

public final class MediatorUtils {

    private MediatorUtils() {
    }

    /**
     * Returns true, if provided message is SDSB SOAP message.
     */
    public static final boolean isSdsbSoapMessage(SoapMessage m) {
        return m instanceof SoapMessageImpl;
    }

    /**
     * Returns true, if provided message is X-Road 5.0 SOAP message.
     */
    public static final boolean isXroadSoapMessage(SoapMessage m) {
        return m instanceof XRoadSoapMessageImpl;
    }
}
