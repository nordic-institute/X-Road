package ee.ria.xroad.proxy.conf;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.cert.CertChain;

/**
 * Authentication key manager used by the proxy.
 */
@Slf4j
public final class AuthKeyManager extends X509ExtendedKeyManager {

    private static final String ALIAS = "AuthKeyManager";

    private static final AuthKeyManager INSTANCE = new AuthKeyManager();

    /**
     * @return the authentication key manager instance
     */
    public static AuthKeyManager getInstance() {
        return INSTANCE;
    }

    private AuthKeyManager() {
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {
        log.trace("chooseClientAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        log.trace("chooseServerAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        log.trace("getCertificateChain {}", alias);

        CertChain certChain = KeyConf.getAuthKey().getCertChain();
        List<X509Certificate> allCerts =
                certChain.getAllCertsWithoutTrustedRoot();
        return allCerts.toArray(new X509Certificate[allCerts.size()]);
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        log.trace("getClientAliases {} {}", keyType, issuers);
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        log.trace("getPrivateKey {}", alias);
        return KeyConf.getAuthKey().getKey();
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        log.trace("getServerAliases {} {}", keyType, issuers);
        return null;
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
            SSLEngine engine) {
        log.trace("chooseEngineClientAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {
        log.trace("chooseEngineServerAlias {} {}", keyType, issuers);
        return ALIAS;
    }
}
