package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

import ee.cyber.sdsb.common.identifier.SecurityServerId;

/**
 * Signer API message.
 */
@Value
public class GetAuthKey implements Serializable {

    private final SecurityServerId securityServer;
}
