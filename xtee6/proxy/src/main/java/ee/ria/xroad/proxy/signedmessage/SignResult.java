package ee.ria.xroad.proxy.signedmessage;

import lombok.Data;

/**
 * Encapsulates information about a signing result.
 */
@Data
public class SignResult {

    private final byte[] signature;

    private final String hashChainResult;
    private final String hashChain;

    /**
     * @return true if the created signature is a batch signature
     */
    public boolean isBatchSignature() {
        return hashChainResult != null && hashChain != null;
    }

}
