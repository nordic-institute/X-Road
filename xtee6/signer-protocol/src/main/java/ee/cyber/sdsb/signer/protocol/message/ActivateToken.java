package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

/**
 * Signer API message.
 */
@Value
public class ActivateToken implements Serializable {

    private final String tokenId;

    private final boolean activate;

}
