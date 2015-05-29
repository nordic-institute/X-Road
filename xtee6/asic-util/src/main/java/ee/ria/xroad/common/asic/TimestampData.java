package ee.ria.xroad.common.asic;

import lombok.Data;

/**
 * Encapsulates timestamp data.
 */
@Data
public class TimestampData {

    /** Base64 encoded timestamp token */
    private final String timestampBase64;
    private final String hashChainResult;
    private final String hashChain;

}
