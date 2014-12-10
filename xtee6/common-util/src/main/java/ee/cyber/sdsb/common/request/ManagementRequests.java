package ee.cyber.sdsb.common.request;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Contains constants for management request service names.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ManagementRequests {

    public static final String AUTH_CERT_REG = "authCertReg";

    public static final String AUTH_CERT_DELETION = "authCertDeletion";

    public static final String CLIENT_REG = "clientReg";

    public static final String CLIENT_DELETION = "clientDeletion";

}
