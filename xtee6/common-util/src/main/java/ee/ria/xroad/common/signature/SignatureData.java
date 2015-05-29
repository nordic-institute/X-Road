package ee.ria.xroad.common.signature;

import lombok.Data;

/**
 * Encapsulates the created signature XML and possible hash chain.
 */
@Data
public final class SignatureData {

    private final String signatureXml;

    private final String hashChainResult;
    private final String hashChain;

    /**
     * @return true if this signature is a batch signature
     */
    public boolean isBatchSignature() {
        return hashChainResult != null && hashChain != null;
    }
}
