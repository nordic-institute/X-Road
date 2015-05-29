package ee.cyber.xroad.mediator.service;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.conf.InternalSSLKey;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ServiceMediatorKeyManager extends X509ExtendedKeyManager {

    private static final Logger LOG =
            LoggerFactory.getLogger(ServiceMediatorKeyManager.class);

    private static final String ALIAS = "AuthKeyManager";

    private final InternalSSLKey sslKey;

    @Override
    public String chooseEngineClientAlias(String[] keyType,
            Principal[] issuers, SSLEngine engine) {
        return ALIAS;
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {
        return ALIAS;
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {
        return ALIAS;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        return ALIAS;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        LOG.trace("getCertificateChain: {}", sslKey.getCert());
        return new X509Certificate[] {sslKey.getCert()};
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        LOG.trace("getPrivateKey: {}", sslKey.getKey());
        return sslKey.getKey();
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return null;
    }

}
