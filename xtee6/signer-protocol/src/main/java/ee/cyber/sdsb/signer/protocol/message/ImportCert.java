package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

@Value
@ToString(exclude = "certData")
public class ImportCert implements Serializable {

    private final byte[] certData;
    private final String initialStatus;

}
