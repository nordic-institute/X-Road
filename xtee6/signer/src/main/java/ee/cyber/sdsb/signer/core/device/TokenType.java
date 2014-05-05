package ee.cyber.sdsb.signer.core.device;

public interface TokenType {

    String getDeviceType();

    boolean isReadOnly();

    boolean isBatchSigningEnabled();

    Integer getSlotIndex();

    String getSerialNumber();

    String getLabel();

    String getId();
}
