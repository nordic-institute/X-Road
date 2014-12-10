package ee.cyber.xroad.common.signature;

import lombok.Data;

import ee.cyber.xroad.common.util.CryptoUtils;


/**
 * Encapsulates part of the input that will be added to the signature.
 */
@Data
public final class MessagePart {

    /** Holds the name of the part. */
    private final String name;

     /** The identifier of the algorithm used to calculate the hash. */
    private final String hashAlgoId;

    /** The data in base64. */
    private final String base64Data;

    /**
     * @return the raw data (base64 decoded)
     */
    public byte[] getData() {
        return CryptoUtils.decodeBase64(base64Data);
    }

    /**
     * @return the hash algorithm URI
     */
    public String getHashAlgorithmURI() throws Exception {
        return CryptoUtils.getAlgorithmURI(hashAlgoId);
    }

    @Override
    public String toString() {
        return name + "\n" + hashAlgoId + ": " + base64Data;
    }
}
