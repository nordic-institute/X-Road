package ee.cyber.sdsb.signer.protocol.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Value;

@Value
public final class TokenInfo implements Serializable {

    private final String type;

    private final String friendlyName;

    private final String id;

    private final boolean readOnly;

    private final boolean available;

    private final boolean active;

    private final String serialNumber;

    private final String label;

    private final int slotIndex;

    private final TokenStatusInfo status;

    private final List<KeyInfo> keyInfo;

    /** Contains label-value pairs of information about token. */
    private final Map<String, String> tokenInfo;

}
