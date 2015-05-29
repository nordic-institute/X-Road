package ee.ria.xroad.signer.protocol.message;

import java.io.Serializable;
import java.util.Date;

import lombok.Value;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

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
