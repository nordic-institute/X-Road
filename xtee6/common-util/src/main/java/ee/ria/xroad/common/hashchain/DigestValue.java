package ee.ria.xroad.common.hashchain;

import lombok.Data;

/**
 * Represents a digest value together with digest method used to
 * compute this value.
 */
@Data
public class DigestValue {
    private final String digestMethod;
    private final byte[] digestValue;
}
