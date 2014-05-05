package ee.cyber.sdsb.proxy.conf;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthKeyManager extends X509ExtendedKeyManager {

    private static final String ALIAS = "AuthKeyManager";

    private static final Logger LOG =
            LoggerFactory.getLogger(AuthKeyManager.class);

    private static final AuthKeyManager instance = new AuthKeyManager();

    public static AuthKeyManager getInstance() {
        return instance;
    }

    private AuthKeyManager() {
    }

    public X509Certificate getAuthCert() {
        return KeyConf.getAuthKey().getCert();
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {
        LOG.debug("chooseClientAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        LOG.debug("chooseServerAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        LOG.debug("getCertificateChain {}", alias);
        return new X509Certificate[] { getAuthCert() /*, caCert*/ };
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        LOG.debug("getClientAliases {} {}", keyType, issuers);
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        LOG.debug("getPrivateKey {}", alias);
        return KeyConf.getAuthKey().getKey();
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        LOG.debug("getServerAliases {} {}", keyType, issuers);
        return null;
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
            SSLEngine engine) {
        LOG.debug("chooseEngineClientAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {
        LOG.debug("chooseEngineServerAlias {} {}", keyType, issuers);
        return ALIAS;
    }

}
