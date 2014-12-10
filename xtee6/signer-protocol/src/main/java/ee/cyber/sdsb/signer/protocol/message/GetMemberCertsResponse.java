package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;
import java.util.List;

import lombok.ToString;
import lombok.Value;

import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;

/**
 * Signer API message.
 */
@Value
@ToString(exclude = "certs")
public class GetMemberCertsResponse implements Serializable {

    private final List<CertificateInfo> certs;

}
