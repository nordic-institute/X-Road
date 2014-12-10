package ee.cyber.sdsb.signer.protocol.dto;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

/**
 * Member signing info DTO.
 */
@Value
@ToString(exclude = "cert")
public class MemberSigningInfo implements Serializable {

    private final String keyId;

    private final CertificateInfo cert;
}
