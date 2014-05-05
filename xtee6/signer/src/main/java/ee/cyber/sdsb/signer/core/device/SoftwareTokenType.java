package ee.cyber.sdsb.signer.core.device;

import lombok.Data;

@Data
public final class SoftwareTokenType implements TokenType {

    public static final String ID = "0";

    @Override
    public String getDeviceType() {
        return SoftwareDeviceType.TYPE;
    }

    @Override
    public Integer getSlotIndex() {
        return 0;
    }

    @Override
    public String getSerialNumber() {
        return null;
    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isBatchSigningEnabled() {
        return true;
    }

}
