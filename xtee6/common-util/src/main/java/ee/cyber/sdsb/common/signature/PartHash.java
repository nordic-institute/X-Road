package ee.cyber.sdsb.common.signature;

import ee.cyber.sdsb.common.util.CryptoUtils;


/**
 * Encapsulates part of the input that will be added to the signature.
 *
 * TODO: Rename this class or refactor to contain either hash or raw data
 * Since we are using this class for both raw data and data hashes,
 * it is currently not clear what data is in it at any given time.
 */
public final class PartHash {

    /** Holds the name of the part. */
    private final String name;

    /** The data in base64. */
    private final String data;

    /** The identifier of the algorithm used to calculate the hash. */
    private final String hashAlgoId;

    /** Creates a new part hash. */
    public PartHash(String name, String hashAlgoId, String base64Data) {
        this.name = name;
        this.hashAlgoId = hashAlgoId;
        this.data = base64Data;
    }

    /**
     * @return the name of the part
     */
    public String getName() {
        return name;
    }

    /**
     * @return the hash value in base64
     */
    public String getBase64Data() {
        return data;
    }

    /**
     * @return the hash value in bytes
     */
    public byte[] getData() {
        return CryptoUtils.decodeBase64(data);
    }

    /**
     * @return the hash algorithm identifier
     */
    public String getHashAlgoId() {
        return hashAlgoId;
    }

    /**
     * @return the hash algorithm URI
     */
    public String getHashAlgorithmURI() throws Exception {
        return CryptoUtils.getAlgorithmURI(hashAlgoId);
    }

    /**
     * Verifies that this part hash is equal to another part hash, by
     * comparing the name, hash value and hash algorithm identifiers.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PartHash)) {
            return false;
        }

        PartHash part = (PartHash) o;
        return hashAlgoId.equals(part.hashAlgoId)
                && data.equals(part.data);
    }

    @Override
    public String toString() {
        return name + "\n" + hashAlgoId + ": " + data;
    }
}
