package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

@Value
public class InitSoftwareToken implements Serializable {

    private final char[] pin;

}
