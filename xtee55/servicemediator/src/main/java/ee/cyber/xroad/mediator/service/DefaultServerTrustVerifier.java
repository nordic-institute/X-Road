package ee.cyber.xroad.mediator.service;

import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLSession;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.xroad.mediator.MediatorServerConf;

import static ee.cyber.sdsb.common.ErrorCodes.X_SSL_AUTH_FAILED;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DefaultServerTrustVerifier implements ServerTrustVerifier {

    private static final Logger LOG =
            LoggerFactory.getLogger(DefaultServerTrustVerifier.class);

    private final ServiceId service;

    @Override
    public void checkServerTrusted(HttpContext httpContext, SSLSession session)
            throws Exception {
        if (!MediatorServerConf.isSslAuthentication(service)) {
            return;
        }

        checkServerTrusted(service.getClientId(), session);
    }

    private static void checkServerTrusted(ClientId client, SSLSession session)
            throws Exception {
        X509Certificate[] certs =
                (X509Certificate[]) session.getPeerCertificates();
        if (certs.length == 0) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Could not get peer certificates");
        }

        List<X509Certificate> isCerts =
                MediatorServerConf.getIsCerts(client);
        if (isCerts.isEmpty()) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Client '%s' has no IS certificates", client);
        }

        for (X509Certificate cert : certs) {
            if (isCerts.contains(cert)) {
                LOG.trace("Found matching IS certificate");
                return;
            }
        }

        LOG.error("Could not find matching IS certificate for client '{}'",
                client);
        throw new CodedException(X_SSL_AUTH_FAILED,
                "Server certificate is not trusted");
    }
}
