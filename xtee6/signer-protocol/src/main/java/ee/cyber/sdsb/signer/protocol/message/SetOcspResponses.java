package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

/**
 * Signer API message.
 */
@Value
@ToString(exclude = "base64EncodedResponses")
public class SetOcspResponses implements Serializable {

    String[] certHashes;
    String[] base64EncodedResponses;

}
