package ee.cyber.sdsb.signer.util;

import lombok.Value;

import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;

@Value
public final class TokenAndKey {

    private final String tokenId;

    private final KeyInfo key;

    public String getKeyId() {
        return key.getId();
    }

}
