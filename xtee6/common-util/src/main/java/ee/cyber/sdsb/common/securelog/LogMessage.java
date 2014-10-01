package ee.cyber.sdsb.common.securelog;

import java.io.Serializable;

import lombok.Value;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.signature.SignatureData;

@Value
public class LogMessage implements Serializable {

    private final SoapMessageImpl message;
    private final SignatureData signature;

}
