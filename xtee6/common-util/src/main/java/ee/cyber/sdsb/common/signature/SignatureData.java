package ee.cyber.sdsb.common.signature;

import lombok.Data;

/**
 * Encapsulates the created signature XML and possible hash chain.
 */
@Data
public final class SignatureData {

    private final String signatureXml;

    private final String hashChainResult;
    private final String hashChain;

    public boolean isBatchSignature() {
        return hashChainResult != null && hashChain != null;
    }
}
