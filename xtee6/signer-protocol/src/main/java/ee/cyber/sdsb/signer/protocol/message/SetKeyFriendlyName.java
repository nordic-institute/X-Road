package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

@Value
public class SetKeyFriendlyName implements Serializable {

    private final String keyId;
    private final String friendlyName;

}
