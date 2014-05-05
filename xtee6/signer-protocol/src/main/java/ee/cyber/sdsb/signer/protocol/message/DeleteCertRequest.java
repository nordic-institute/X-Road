package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

@Value
public class DeleteCertRequest implements Serializable {

    private final String certId;

}
