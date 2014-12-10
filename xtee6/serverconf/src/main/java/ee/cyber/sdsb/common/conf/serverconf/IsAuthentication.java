package ee.cyber.sdsb.common.conf.serverconf;

import java.security.cert.X509Certificate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.InternalSSLKey;
import ee.cyber.sdsb.common.identifier.ClientId;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_SSL_AUTH_FAILED;

@Slf4j
public enum IsAuthentication {

    NOSSL,
    SSLNOAUTH,
    SSLAUTH;

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
                        "Client (%s) specifies SSLNOAUTH but client made "
                                + " plaintext connection", client);
            }
        } else if (isAuthentication == IsAuthentication.SSLAUTH) {
            if (cert.getCert() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) specifies SSLAUTH but did not supply"
                                + " SSL certificate", client);
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
                        "Client (%s) SSL certificate does not match any"
                                + " IS certificates", client);
            }
        }
    }

}
