package ee.ria.xroad.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

import ee.ria.xroad.common.identifier.SecurityServerId;

/**
 * Signer API message.
 */
@Value
public class GetAuthKey implements Serializable {

    private final SecurityServerId securityServer;
}
