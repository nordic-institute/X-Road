package ee.ria.xroad.signer.tokenmanager.token;

/**
 * Describes a token type, usually a software or hardware based token.
 */
public interface TokenType {

    /**
     * @return the module type
     */
    String getModuleType();

    /**
     * @return true if the token is read only
     */
    boolean isReadOnly();

    /**
     * @return true if batch signing is enabled for the token
     */
    boolean isBatchSigningEnabled();

    /**
     * @return the slot index of the token
     */
    Integer getSlotIndex();

    /**
     * @return the serial number of the token
     */
    String getSerialNumber();

    /**
     * @return the label of the token
     */
    String getLabel();

    /**
     * @return the id of the token
     */
    String getId();
}
