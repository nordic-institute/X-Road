package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

import ee.cyber.sdsb.common.identifier.ClientId;

@Value
public class GetMemberCerts implements Serializable {

    private final ClientId memberId;
}
