package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

@Value
@ToString(exclude = "base64EncodedResponses")
public class GetOcspResponsesResponse implements Serializable {

    private final String[] base64EncodedResponses;
}
