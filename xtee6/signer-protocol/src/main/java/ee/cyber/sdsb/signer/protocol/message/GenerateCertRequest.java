package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;

import lombok.Value;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;

/**
 * Signer API message.
 */
@Value
public class GenerateCertRequest implements Serializable {

    private final String keyId;

    private final ClientId memberId;

    private final KeyUsageInfo keyUsage;

    private final String subjectName;

}
