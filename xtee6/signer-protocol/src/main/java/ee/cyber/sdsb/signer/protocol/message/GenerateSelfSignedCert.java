package ee.cyber.sdsb.signer.protocol.message;

import java.io.Serializable;
import java.util.Date;

import lombok.Value;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;

/**
 * Signer API message.
 */
@Value
public class GenerateSelfSignedCert implements Serializable {

    private final String keyId;

    private final String commonName;

    private final Date notBefore;

    private final Date notAfter;

    private final KeyUsageInfo keyUsage;

    private final ClientId memberId;
}
