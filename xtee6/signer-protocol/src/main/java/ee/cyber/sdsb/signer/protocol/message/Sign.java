package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

/**
 * Signer API message.
 */
@Value
@ToString(exclude = "digest")
public class Sign implements Serializable {

    private final String keyId;
    private final String signatureAlgorithmId;
    private final byte[] digest;

}
