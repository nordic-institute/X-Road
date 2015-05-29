package ee.ria.xroad.common.messagelog;

import java.io.Serializable;

import lombok.Value;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;

/**
 * Message for logging the contained SOAP message and signature data.
 */
@Value
public class LogMessage implements Serializable {

    private final SoapMessageImpl message;
    private final SignatureData signature;

}
