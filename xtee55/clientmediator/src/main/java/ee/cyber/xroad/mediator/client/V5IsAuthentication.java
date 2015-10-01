package ee.cyber.xroad.mediator.client;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.xroad.serviceimporter.XConf;
import ee.cyber.xroad.serviceimporter.XConf.Consumer;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.util.CryptoUtils;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

@Slf4j
final class V5IsAuthentication {

    private static List<Consumer> consumers;
    private static Map<Consumer, IsAuthentication> auth;
    private static Map<Consumer, List<X509Certificate>> certs;

    static void loadConf() {
        XConf xConf = new XConf();

        xConf.readLock();

        try {
            consumers = xConf.getConsumers();
            certs = new HashMap<>();
            auth = new HashMap<>();

            log.info("Loading authentication methods and certificates "
                    + "of information systems of 5.0 X-Road...");

            for (Consumer c : consumers) {
                try {
                    auth.put(c, getIsAuthentication(xConf, c));
                    certs.put(c, getIsCerts(xConf, c));
                } catch (Throwable t) {
                    log.error("Cannot load 5.0 X-Road configuration. "
                            + "Ignoring consumer '" + c.getShortName() + "': "
                            + t.getMessage());
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            xConf.unlock();
        }
    }

    private V5IsAuthentication() {
    }

    /**
     * Verifies the authentication for the consumer certificate.
     * @param consumer the consumer identifier
     * @param cert the consumer certificate
     * @throws Exception if verification fails
     */
    static void verifyConsumerAuthentication(String consumer,
            IsAuthenticationData cert) throws Exception {
        XConf.Consumer con = findConsumer(consumer);
        if (consumer == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Consumer '%s' not found", consumer);
        }

        IsAuthentication isAuthentication = auth.get(con);
        log.trace("V5 IS authentication for consumer '{}' is: {}", consumer,
                isAuthentication);

        if (isAuthentication == IsAuthentication.SSLNOAUTH) {
            if (cert.isPlaintextConnection()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Consumer (%s) specifies HTTPS NO AUTH but"
                        + " made plaintext connection", consumer);
            }
        } else if (isAuthentication == IsAuthentication.SSLAUTH) {
            if (cert.getCert() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Consumer (%s) specifies HTTPS but did not supply"
                                + " TLS certificate", consumer);
            }

            if (cert.getCert().equals(InternalSSLKey.load().getCert())) {
                // do not check certificates for local TLS connections
                return;
            }

            List<X509Certificate> isCerts = certs.get(con);
            if (isCerts.isEmpty()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Consumer (%s) has no IS certificates", consumer);
            }

            if (!isCerts.contains(cert.getCert())) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Consumer (%s) TLS certificate does not match any"
                                + " IS certificates", consumer);
            }
        }
    }

    private static List<X509Certificate> getIsCerts(XConf xConf, Consumer con) {
        try {
            return xConf.getInternalSSLCerts(con).stream()
                    .map(bytes -> CryptoUtils.readCertificate(bytes))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static IsAuthentication getIsAuthentication(XConf xConf,
            Consumer con) {
        try {
            switch (xConf.getPeerType(con)) {
                case "https":
                    return IsAuthentication.SSLAUTH;
                case "https noauth":
                    return IsAuthentication.SSLNOAUTH;
                default:
                    return IsAuthentication.NOSSL;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Consumer findConsumer(String consumer) throws IOException {
        for (Consumer con : consumers) {
            if (con.getShortName().equals(consumer)) {
                return con;
            }
        }

        return null;
    }

}

