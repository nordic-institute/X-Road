package ee.ria.xroad.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

/**
 * Signer API message.
 */
@Value
public class GetTokenBatchSigningEnabled implements Serializable {

    private final String keyId;

}
