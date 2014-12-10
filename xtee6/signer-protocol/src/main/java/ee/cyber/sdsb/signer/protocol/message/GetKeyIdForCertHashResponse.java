package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

/**
 * Signer API message.
 */
@Value
public class GetKeyIdForCertHashResponse implements Serializable {

    private final String keyId;

}
