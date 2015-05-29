package ee.ria.xroad.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * Signer API message.
 */
@Value
public class GetMemberCerts implements Serializable {

    private final ClientId memberId;
}
