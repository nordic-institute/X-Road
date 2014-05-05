package ee.cyber.xroad.mediator.message;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageImpl;

import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_MESSAGE;

public enum MessageVersion {
    SDSB,
    XROAD50,
    XROAD50_META;

    public static MessageVersion fromMessage(SoapMessage message) {
        if (message instanceof SoapMessageImpl) {
            return SDSB;
        } else if (message instanceof XRoadSoapMessageImpl) {
            return XROAD50;
        } else if (message instanceof XRoadMetaServiceImpl) {
            return XROAD50_META;
        }

        throw new CodedException(X_INVALID_MESSAGE, "Unknown SOAP");
    }
}
