package ee.cyber.sdsb.common;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.apache.commons.io.IOUtils;

import ee.cyber.sdsb.common.util.CryptoUtils;

/**
 * Contains various certificate related utility methods for using in test cases,
 * all in one place.
 *
 * Whenever you need to use a certificate in a test class, use this class. Add
 * necessary convenience methods if needed.
 */
public final class TestCertUtil {

    /** Hard coded path to all the certificates. */
    private static final String CERT_PATH = "/";

    /** Lazily initialized cached instances of the certs. */
    private static X509Certificate caCert;
    private static X509Certificate tspCert;
    private static PKCS12 producer;
    private static PKCS12 consumer;
    private static PKCS12 ca2TestOrg;
    private static PKCS12 ocspSigner;

    /** Tiny container to keep the certificate and private key together. */
    public static class PKCS12 {
        public X509Certificate cert;
        public PrivateKey key;
    }

    // -- Certificate retrieval methods ------------------------------------- //

    /** AdminCA1. */
    public static X509Certificate getCaCert() {
        if (caCert == null) {
            caCert = loadPKCS12("root-ca.p12", "1", "test").cert;
        }

        return caCert;
    }

    /** TSP cert. */
    public static X509Certificate getTspCert() {
        if (tspCert == null) {
            tspCert = getCert(CERT_PATH + "tsp.pem");
        }

        return tspCert;
    }

    /** Producer org, signed by AdminCA1. */
    public static PKCS12 getProducer() {
        if (producer == null) {
            producer = loadPKCS12("producer.p12", "1", "test");
        }

        return producer;
    }

    /** Consumer org, signed by AdminCA1. */
    public static PKCS12 getConsumer() {
        if (consumer == null) {
            consumer = loadPKCS12("consumer.p12", "1", "test");
        }

        return consumer;
    }

    /** Test org signed by AdminCA2. */
    public static PKCS12 getCa2TestOrg() {
        if (ca2TestOrg == null) {
            ca2TestOrg = loadPKCS12("ca2testorg.p12", "ca2 test", "test");
        }

        return ca2TestOrg;
    }

    /** Ocsp signer, signed by AdminCA1. */
    public static PKCS12 getOcspSigner() {
        if (ocspSigner == null) {
            ocspSigner = loadPKCS12("ocspsigner.p12", "1", "test");
        }

        return ocspSigner;
    }

    /** Returns a certificate from the certificate chain test
     * (certs under "cert-chain" subdirectory). */
    public static X509Certificate getCertChainCert(String fileName)
            throws Exception {
        String file = CERT_PATH + "test_chain/" + fileName;
        KeyStore keyStore = loadPKCS12KeyStore(file, "test");
        return getCert(keyStore, "1");
    }

    /** Returns a private key from the certificate chain test
     * (certs under "cert-chain" subdirectory). */
    public static PrivateKey getCertChainKey(String fileName) throws Exception {
        String file = CERT_PATH + "test_chain/" + fileName;
        KeyStore keyStore = loadPKCS12KeyStore(file, "test");
        return getKey(keyStore, "test", "1");
    }

    // -- Misc utility methods ---------------------------------------------- //

    public static X509Certificate getCert(String pemFileName) {
        try {
            String data = IOUtils.toString(getFile(pemFileName));
            return CryptoUtils.readCertificate(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static X509Certificate getCert(KeyStore keyStore, String orgName) {
        try {
            X509Certificate cert =
                    (X509Certificate) keyStore.getCertificate(orgName);
            if (cert == null) {
                throw new RuntimeException("Unable to get certificate for " +
                        "name \"" + orgName + "\" from keystore");
            }

            return cert;
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey getKey(KeyStore keyStore, String password,
            String orgName) {
        try {
            PrivateKey key = (PrivateKey) keyStore.getKey(orgName,
                    password.toCharArray());
            if (key == null) {
                throw new RuntimeException("Unable to get key for " +
                        "name \"" + orgName + "\" using password \""
                        + password + "\" from keystore");
            }

            return key;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyStore loadPKCS12KeyStore(String file, String password) {
        return loadKeyStore("pkcs12", file, password);
    }

    public static KeyStore loadJKSKeyStore(String file, String password) {
        return loadKeyStore("jks", file, password);
    }

    public static KeyStore loadKeyStore(String type, String file,
            String password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            InputStream fis = getFile(file);
            keyStore.load(fis, password.toCharArray());
            fis.close();

            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PKCS12 loadPKCS12(String file, String orgName,
            String password) {
        KeyStore orgKeyStore = loadPKCS12KeyStore(CERT_PATH + file, password);
        PKCS12 pkcs12 = new PKCS12();
        pkcs12.cert = getCert(orgKeyStore, orgName);
        pkcs12.key = getKey(orgKeyStore, password, orgName);
        return pkcs12;
    }

    private static InputStream getFile(String fileName) throws Exception {
        InputStream is = TestCertUtil.class.getResourceAsStream(fileName);
        if (is == null) {
            throw new FileNotFoundException(fileName);
        }

        return is;
    }
}
