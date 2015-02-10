package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

/**
 * Signer API message.
 */
@Value
public class DeleteKey implements Serializable {

    private String keyId;

    private boolean deleteFromDevice;

}
