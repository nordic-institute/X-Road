package ee.cyber.xroad.mediator.message;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageImpl;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_MESSAGE;

/**
 * Enumeration that identifies a message either as a X-Road 6.0 message,
 * a X-Road 5.0 message or a X-Road 5.0 meta message.
 */
public enum MessageVersion {
    XROAD60,
    XROAD50,
    XROAD50_META;

    /**
     * @param message the SOAP message
     * @return the message version of the given SOAP message
     */
    public static MessageVersion fromMessage(SoapMessage message) {
        if (message instanceof SoapMessageImpl) {
            return XROAD60;
        } else if (message instanceof V5XRoadSoapMessageImpl) {
            return XROAD50;
        } else if (message instanceof V5XRoadMetaServiceImpl) {
            return XROAD50_META;
        }

        throw new CodedException(X_INVALID_MESSAGE, "Unknown SOAP");
    }
}
