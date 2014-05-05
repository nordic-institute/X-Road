package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

@Value
public class GenerateCertRequestResponse implements Serializable {

    private final String certReqId;

    private final byte[] certRequest;

}
