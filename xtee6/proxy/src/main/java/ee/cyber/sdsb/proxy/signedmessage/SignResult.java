package ee.cyber.sdsb.proxy.signedmessage;

import lombok.Data;

@Data
public class SignResult {

    private final byte[] signature;

    private final String hashChainResult;
    private final String hashChain;

    public boolean isBatchSignature() {
        return hashChainResult != null && hashChain != null;
    }

}
