package ee.cyber.sdsb.signer.tokenmanager.token;

public interface TokenType {

    String getModuleType();

    boolean isReadOnly();

    boolean isBatchSigningEnabled();

    Integer getSlotIndex();

    String getSerialNumber();

    String getLabel();

    String getId();
}
