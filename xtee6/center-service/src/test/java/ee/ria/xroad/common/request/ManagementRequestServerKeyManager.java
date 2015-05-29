package ee.ria.xroad.common.request;

import java.io.File;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;

@Slf4j
class ManagementRequestServerKeyManager extends X509ExtendedKeyManager {

    private static final String ALIAS = "ManagementRequestAuthKeyManager";

    X509Certificate acceptedIssuer;
    PrivateKey pkey;

    ManagementRequestServerKeyManager() throws Exception {
        loadPkcs12();
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {
        log.debug("chooseClientAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        log.debug("chooseServerAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        log.debug("getCertificateChain {}", alias);
        return new X509Certificate[] {acceptedIssuer/*, caCert*/};
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        log.debug("getClientAliases {} {}", keyType, issuers);
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        log.debug("getPrivateKey {}", alias);
        return pkey; //KeyConf.getAuthKey().getKey();
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        log.debug("getServerAliases {} {}", keyType, issuers);
        return null;
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
            SSLEngine engine) {
        log.debug("chooseEngineClientAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {
        log.debug("chooseEngineServerAlias {} {}", keyType, issuers);
        return ALIAS;
    }

    private void loadPkcs12() throws Exception {
        File file = new File("../proxy/src/test/producer.p12");
        char[] password = "test".toCharArray();

        KeyStore ks = loadPkcs12KeyStore(file, password);

        acceptedIssuer = (X509Certificate) ks.getCertificate("producer");
        pkey = (PrivateKey) ks.getKey("producer", password);
    }
}
