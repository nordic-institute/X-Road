package ee.cyber.sdsb.signer.util;

import java.io.Serializable;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "signature")
public class CalculatedSignature implements Serializable {

    private final CalculateSignature request;
    private final byte[] signature;

    private final Exception exception;

}
