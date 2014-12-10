package ee.cyber.sdsb.signer.protocol.dto;

import java.io.Serializable;

import lombok.ToString;
import lombok.Value;

import ee.cyber.sdsb.common.identifier.ClientId;

/**
 * Certificate info DTO.
 */
@Value
@ToString(exclude = { "certificateBytes", "ocspBytes" })
public class CertificateInfo implements Serializable {

    public static final String STATUS_SAVED = "saved";
    public static final String STATUS_REGINPROG = "registration in progress";
    public static final String STATUS_REGISTERED = "registered";
    public static final String STATUS_DELINPROG = "deletion in progress";
    public static final String STATUS_GLOBALERR = "global error";

    public static final String OCSP_RESPONSE_DELIMITER = ":";
    public static final String OCSP_RESPONSE_GOOD = "good";
    public static final String OCSP_RESPONSE_REVOKED = "revoked";
    public static final String OCSP_RESPONSE_UNKNOWN = "unknown";
    public static final String OCSP_RESPONSE_SUSPENDED = "suspended";

    private final ClientId memberId;

    private final boolean active;

    private final boolean savedToConfiguration;

    private final String status;

    private final String id;

    private final byte[] certificateBytes;
    private final byte[] ocspBytes;
}
