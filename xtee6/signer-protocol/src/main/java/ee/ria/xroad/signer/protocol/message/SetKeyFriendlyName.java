package ee.ria.xroad.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

/**
 * Signer API message.
 */
@Value
public class SetKeyFriendlyName implements Serializable {

    private final String keyId;
    private final String friendlyName;

}
