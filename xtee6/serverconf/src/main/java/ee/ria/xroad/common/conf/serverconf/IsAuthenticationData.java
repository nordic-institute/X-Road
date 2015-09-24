package ee.ria.xroad.common.conf.serverconf;

import java.security.cert.X509Certificate;

import lombok.Data;

/**
 * The client information system authentication data contains
 * the client IS certificate (optional) and flag indicating whether
 * the client made plaintext connection.
 */
@Data
public class IsAuthenticationData {

    private final X509Certificate cert;
    private final boolean isPlaintextConnection;

}
