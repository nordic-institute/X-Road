package ee.cyber.sdsb.signer.protocol.dto;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

import ee.cyber.sdsb.common.identifier.ClientId;

@Value
@ToString(exclude = "certificateBytes")
public class CertificateInfo implements Serializable {

    private final ClientId memberId;

    private final boolean active;

    private final boolean revoked = false; // TODO: Handle cert revocation

    private final boolean savedToConfiguration;

    private final String status;

    private final String id;

    private final byte[] certificateBytes;
}
