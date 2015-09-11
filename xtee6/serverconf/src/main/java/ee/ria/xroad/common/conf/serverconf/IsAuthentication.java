package ee.ria.xroad.common.conf.serverconf;

import java.security.cert.X509Certificate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Encapsulates the information system authentication method.
 */
@Slf4j
public enum IsAuthentication {

    NOSSL,
    SSLNOAUTH,
    SSLAUTH;

    /**
     * Verifies the authentication for the client certificate.
     * @param client the client identifier
     * @param cert the client certificate
     * @throws Exception if verification fails
     */
    public static void verifyClientAuthentication(ClientId client,
            ClientCert cert) throws Exception {
        IsAuthentication isAuthentication =
                ServerConf.getIsAuthentication(client);
        if (isAuthentication == null) {
            // Means the client was not found in the server conf.
            // The getIsAuthentication method implemented in ServerConfCommonImpl
            // checks if the client exists; if it does, returns the
            // isAuthentication value or NOSSL if no value is specified.
            throw new CodedException(X_INTERNAL_ERROR,
                    "Client '%s' not found", client);
        }

        log.trace("IS authentication for client '{}' is: {}", client,
                isAuthentication);

        if (isAuthentication == IsAuthentication.SSLNOAUTH) {
            if (cert.getVerificationResult() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) specifies HTTPS NO AUTH but client made "
                                + " plaintext connection", client);
            }
        } else if (isAuthentication == IsAuthentication.SSLAUTH) {
            if (cert.getCert() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) specifies HTTPS but did not supply"
                                + " TLS certificate", client);
            }

            if (cert.getCert().equals(InternalSSLKey.load().getCert())) {
                // do not check certificates for local TLS connections
                return;
            }

            List<X509Certificate> isCerts = ServerConf.getIsCerts(client);
            if (isCerts.isEmpty()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) has no IS certificates", client);
            }

            if (!isCerts.contains(cert.getCert())) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) TLS certificate does not match any"
                                + " IS certificates", client);
            }
        }
    }

}
