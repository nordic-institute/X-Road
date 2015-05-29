package ee.ria.xroad.signer.util;

import lombok.Value;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;

/**
 * DTO for holding a token id and key info.
 */
@Value
public final class TokenAndKey {

    private final String tokenId;

    private final KeyInfo key;

    /**
     * @return the key id
     */
    public String getKeyId() {
        return key.getId();
    }

}
