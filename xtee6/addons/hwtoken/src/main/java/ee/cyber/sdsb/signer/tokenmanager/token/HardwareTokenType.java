package ee.cyber.sdsb.signer.tokenmanager.token;

import lombok.EqualsAndHashCode;
import lombok.Value;

import org.apache.commons.lang3.StringUtils;

import ee.cyber.sdsb.common.util.CryptoUtils;

/**
 * Hardware token type, holding the actual pkcs11 token.
 */
@Value
@EqualsAndHashCode(exclude = "readOnly")
public class HardwareTokenType implements TokenType {

    private final String moduleType;

    private final iaik.pkcs.pkcs11.Token token;

    private final boolean readOnly;

    private final Integer slotIndex;

    private final String serialNumber;

    private final String label;

    private boolean pinVerificationPerSigning;

    private boolean batchSigningEnabled;

    @Override
    public String getId() {
        return CryptoUtils.encodeHex(StringUtils.join(new Object[] {
                moduleType, slotIndex, serialNumber, label }).getBytes());
    }
}
