package ee.cyber.sdsb.proxyui;

import java.net.Socket;
import java.net.URL;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.InternalSSLKey;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.conf.serverconf.model.CertificateType;
import ee.cyber.sdsb.common.util.CryptoUtils;

@Slf4j
public class InternalServerTestUtil {

    private static final Logger LOG =
        LoggerFactory.getLogger(InternalServerTestUtil.class);

    public static void testHttpsConnection(
            List<CertificateType> trustedCerts, String url) throws Exception {

        List<X509Certificate> trustedX509Certs = new ArrayList<>();
        for (CertificateType trustedCert : trustedCerts) {
            trustedX509Certs.add(
                CryptoUtils.readCertificate(trustedCert.getData()));
        }

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(createServiceKeyManager(),
            new TrustManager[] { new ServiceTrustManager(trustedX509Certs) },
            new SecureRandom());

        HttpsURLConnection con = (HttpsURLConnection)
            (new URL(url).openConnection());

        con.setSSLSocketFactory(ctx.getSocketFactory());
        con.setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

        con.connect();
    }

    private static KeyManager[] createServiceKeyManager() throws Exception {
        InternalSSLKey key = ServerConf.getSSLKey();

        if (key != null) {
            return new KeyManager[] { new ServiceKeyManager(key) };
        }

        return null;
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    private static class ServiceKeyManager extends X509ExtendedKeyManager {

        private static final String ALIAS = "AuthKeyManager";

        private final InternalSSLKey sslKey;

        @Override
        public String chooseEngineClientAlias(String[] keyType,
                Principal[] issuers, SSLEngine engine) {
            return ALIAS;
        }

        @Override
        public String chooseEngineServerAlias(String keyType,
                Principal[] issuers, SSLEngine engine) {
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
            return new X509Certificate[] { sslKey.getCert() };
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

    private static class ServiceTrustManager implements X509TrustManager {

        private List<X509Certificate> trustedCerts;

        public ServiceTrustManager(List<X509Certificate> trustedCerts) {
            this.trustedCerts = trustedCerts;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
            LOG.trace("checkClientTrusted()");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {

            if (chain == null || chain.length == 0) {
                throw new IllegalArgumentException(
                    "Server certificate chain is empty");
            }

            if (trustedCerts.contains(chain[0])) {
                LOG.trace("Found matching IS certificate");
                return;
            }

            LOG.error("Could not find matching IS certificate");
            throw new CertificateException("Server certificate is not trusted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            LOG.trace("getAcceptedIssuers()");
            return new X509Certificate[] {};
        }
    }
}
