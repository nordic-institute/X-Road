package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

@Value
public class ActivateCert implements Serializable {

    private final String certIdOrHash;
    private final boolean active;

}
