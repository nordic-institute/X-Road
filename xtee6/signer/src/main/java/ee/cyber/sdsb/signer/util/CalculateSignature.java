package ee.cyber.sdsb.signer.util;

import java.io.Serializable;

import lombok.Data;
import lombok.ToString;
import akka.actor.ActorRef;

/**
 * Message for signature calculation request.
 */
@Data
@ToString(exclude = "data")
public class CalculateSignature implements Serializable {

    private final ActorRef receiver;
    private final String keyId;
    private final byte[] data;

}
