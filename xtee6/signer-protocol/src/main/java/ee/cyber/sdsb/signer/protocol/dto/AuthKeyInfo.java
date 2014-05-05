package ee.cyber.sdsb.signer.protocol.dto;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

@Value
@ToString(exclude = { "password", "certificateBytes" })
public class AuthKeyInfo implements Serializable {

    private final String alias;

    private final String keyStoreFileName;

    private final char[] password;

    private final byte[] certificateBytes;

}
