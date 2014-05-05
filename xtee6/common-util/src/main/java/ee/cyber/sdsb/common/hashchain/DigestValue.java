package ee.cyber.sdsb.common.hashchain;

import lombok.Data;

@Data
/**
 * Represents a digest value together with digest method used to
 * compute this value.
 */
public class DigestValue {
    private final String digestMethod;
    private final byte[] digestValue;
}
