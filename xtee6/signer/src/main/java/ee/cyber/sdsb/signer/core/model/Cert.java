package ee.cyber.sdsb.signer.core.model;

import java.util.Random;

import lombok.Data;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;

@Data
public class Cert {

    /** ID for cert that is used by the OCSP request keys. (optional) */
    private final String id;

    /** If this certificate belongs to signing key, then this attribute contains
     * identifier of the member that uses this certificate. */
    private ClientId memberId;

    /** Whether this certificate can be used by the proxy. */
    private boolean active;

    /** TODO - how and where it is supposed to be initialized? */
    private boolean revoked = new Random().nextBoolean();

    /** Whether or not this certificate is in the configuration. */
    // TODO: Is this still relevant?
    private boolean savedToConfiguration;

    /** Holds the status of the certificate. */
    private String status;

    /** Contains the X509 encoded certificate. */
    private byte[] certificateBytes;

    public CertificateInfo toDTO() {
        return new CertificateInfo(memberId, active, savedToConfiguration,
                status, id, certificateBytes);
    }
}
