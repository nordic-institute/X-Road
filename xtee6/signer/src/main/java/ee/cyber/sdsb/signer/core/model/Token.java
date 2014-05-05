package ee.cyber.sdsb.signer.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

import ee.cyber.sdsb.signer.core.device.TokenType;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo;

@Data
public final class Token {

    /** The device type as configured in Signer's device configuration. */
    private final String type;

    /** The token id. */
    private final String id;

    /** The name to display in UI. */
    private String friendlyName;

    /** True, if token is read-only */
    private boolean readOnly;

    /** True, if token is available (in device) */
    private boolean available;

    /** True, if password is inserted */
    private boolean active;

    /** The token serial number (optional). */
    private String serialNumber;

    /** The token label (optional). */
    private String label;

    /** The pin index to further specify the token (optional). */
    private int slotIndex;

    /** Whether batch signing should be enabled for this token. */
    private boolean batchSigningEnabled = false;

    /** Holds the currect status of the token. */
    private TokenStatusInfo status = TokenStatusInfo.OK;

    /** Contains the the keys of this device. */
    private final List<Key> keys = new ArrayList<>();

    /** Contains label-value pairs of information about token. */
    private final Map<String, String> tokenInfo = new LinkedHashMap<>();

    public void addKey(Key key) {
        keys.add(key);
    }

    public void setInfo(Map<String, String> tokenInfo) {
        tokenInfo.putAll(tokenInfo);
    }

    public TokenInfo toDTO() {
        return new TokenInfo(type, friendlyName, id, readOnly, available,
                active, serialNumber, label, slotIndex, status,
                Collections.unmodifiableList(getKeysAsDTOs()),
                Collections.unmodifiableMap(tokenInfo));
    }

    public boolean matches(TokenType token) {
        if (token == null) {
            return false;
        }

        if (id.equals(token.getId())) {
            return true;
        }

        return token.getDeviceType() != null
                && token.getDeviceType().equals(type)
                && areEqual(token.getSerialNumber(), serialNumber)
                && areEqual(token.getLabel(), label)
                && areEqual(token.getSlotIndex(), slotIndex);
    }

    private List<KeyInfo> getKeysAsDTOs() {
        List<KeyInfo> keyInfo = new ArrayList<>();
        for (Key key : keys) {
            keyInfo.add(key.toDTO());
        }

        return keyInfo;
    }

    private static boolean areEqual(Object one, Object another) {
        if (one != null) {
            return one.equals(another);
        }

        return another == null;
    }
}
