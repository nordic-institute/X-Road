package ee.cyber.sdsb.common.request;

import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.CryptoUtils;

public class ManagementRequestServerKeyManager extends X509ExtendedKeyManager {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementRequestServerKeyManager.class);

    private static final String ALIAS = "ManagementRequestAuthKeyManager";

    X509Certificate acceptedIssuer;
    PrivateKey pkey;

    ManagementRequestServerKeyManager() throws Exception {
        loadPkcs12();
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
        return new X509Certificate[] { acceptedIssuer/*, caCert*/ };
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        LOG.debug("getClientAliases {} {}", keyType, issuers);
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        LOG.debug("getPrivateKey {}", alias);
        return pkey; //KeyConf.getAuthKey().getKey();
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

    private void loadPkcs12() throws Exception {
        String fileName = "../proxy/src/test/producer.p12";
        char[] password = "test".toCharArray();

        KeyStore ks = CryptoUtils.loadKeyStore("pkcs12", fileName, password);

        acceptedIssuer = (X509Certificate) ks.getCertificate("producer");
        pkey = (PrivateKey) ks.getKey("producer", password);
    }
}
