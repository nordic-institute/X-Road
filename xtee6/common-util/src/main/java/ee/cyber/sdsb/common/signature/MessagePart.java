package ee.cyber.sdsb.common.signature;

import lombok.Data;

import ee.cyber.sdsb.common.util.CryptoUtils;


/**
 * Encapsulates part of the input that will be added to the signature.
 */
@Data
public final class MessagePart {

    /** Holds the name of the part. */
    private final String name;

     /** The identifier of the algorithm used to calculate the hash. */
    private final String hashAlgoId;

    /** The data in data. */
    private final byte[] data;

    /**
     * @return the raw data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * @return the hash algorithm URI
     */
    public String getHashAlgorithmURI() throws Exception {
        return CryptoUtils.getDigestAlgorithmURI(hashAlgoId);
    }

    @Override
    public String toString() {
        return name + " (" + hashAlgoId + ")";
    }
}
