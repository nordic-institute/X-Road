package ee.cyber.sdsb.signer.core.device;

import lombok.Value;

import org.apache.commons.lang3.StringUtils;

import ee.cyber.sdsb.common.util.CryptoUtils;

@Value
public class SscdTokenType implements TokenType {

    private final String deviceType;

    private final iaik.pkcs.pkcs11.Token token;

    private final boolean readOnly;

    private final Integer slotIndex;

    private final String serialNumber;

    private final String label;

    private boolean pinVerificationPerSigning;

    private boolean batchSigningEnabled;

    @Override
    public String getId() {
        // TODO: Better ID assembly?
        return CryptoUtils.encodeHex(StringUtils.join(new Object[] {
                deviceType, slotIndex, serialNumber, label }).getBytes());
    }

}
