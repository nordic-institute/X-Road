package ee.cyber.sdsb.signer.tokenmanager.token;

import ee.cyber.sdsb.signer.tokenmanager.module.SoftwareModuleType;

import lombok.Data;

@Data
public final class SoftwareTokenType implements TokenType {

    public static final String ID = "0";

    @Override
    public String getModuleType() {
        return SoftwareModuleType.TYPE;
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
