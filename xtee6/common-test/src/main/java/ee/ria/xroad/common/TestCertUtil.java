/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.apache.commons.io.IOUtils;

import ee.ria.xroad.common.util.CryptoUtils;

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

    private TestCertUtil() {
    }

    /** Tiny container to keep the certificate and private key together. */
    public static class PKCS12 {
        public X509Certificate cert;
        public PrivateKey key;
    }

    // -- Certificate retrieval methods ------------------------------------- //

    /**
     * @return AdminCA1 from test resources
     */
    public static X509Certificate getCaCert() {
        if (caCert == null) {
            caCert = loadPKCS12("root-ca.p12", "1", "test").cert;
        }

        return caCert;
    }

    /**
     * @return TSP cert from test resources
     */
    public static X509Certificate getTspCert() {
        if (tspCert == null) {
            tspCert = getCert(CERT_PATH + "tsp.pem");
        }

        return tspCert;
    }

    /**
     * @return Producer org keystore from test resources, signed by AdminCA1
     */
    public static PKCS12 getProducer() {
        if (producer == null) {
            producer = loadPKCS12("producer.p12", "1", "test");
        }

        return producer;
    }

    /**
     * @return Consumer org keystore from test resources, signed by AdminCA1
     */
    public static PKCS12 getConsumer() {
        if (consumer == null) {
            consumer = loadPKCS12("consumer.p12", "1", "test");
        }

        return consumer;
    }

    /**
     * @return Test org keystore from test resources, signed by AdminCA2
     */
    public static PKCS12 getCa2TestOrg() {
        if (ca2TestOrg == null) {
            ca2TestOrg = loadPKCS12("ca2testorg.p12", "ca2 test", "test");
        }

        return ca2TestOrg;
    }

    /**
     * @return Ocsp signer keystore from test resources, signed by AdminCA1
     */
    public static PKCS12 getOcspSigner() {
        if (ocspSigner == null) {
            ocspSigner = loadPKCS12("ocspsigner.p12", "1", "test");
        }

        return ocspSigner;
    }

    /**
     * @param fileName name of the certificate file
     * @return a certificate from the certificate chain test
     * (certs under "cert-chain" subdirectory).
     */
    public static X509Certificate getCertChainCert(String fileName) {
        String file = CERT_PATH + "test_chain/" + fileName;
        KeyStore keyStore = loadPKCS12KeyStore(file, "test");
        return getCert(keyStore, "1");
    }

    /**
     * @param fileName name of the private key file
     * @return a private key from the certificate chain test
     * (certs under "cert-chain" subdirectory).
     */
    public static PrivateKey getCertChainKey(String fileName) {
        String file = CERT_PATH + "test_chain/" + fileName;
        KeyStore keyStore = loadPKCS12KeyStore(file, "test");
        return getKey(keyStore, "test", "1");
    }

    // -- Misc utility methods ---------------------------------------------- //

    /**
     * Loads a certificate in PEM format.
     * @param pemFileName filename of the certificate
     * @return X509Certificate
     */
    public static X509Certificate getCert(String pemFileName) {
        try {
            String data = IOUtils.toString(getFile(pemFileName));
            return CryptoUtils.readCertificate(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a certificate with the specified org name from a keystore.
     * @param keyStore keystore from which to load the certificate
     * @param orgName name of the certificate org
     * @return X509Certificate
     */
    public static X509Certificate getCert(KeyStore keyStore, String orgName) {
        try {
            X509Certificate cert =
                    (X509Certificate) keyStore.getCertificate(orgName);
            if (cert == null) {
                throw new RuntimeException("Unable to get certificate for "
                        + "name \"" + orgName + "\" from keystore");
            }

            return cert;
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a private key with the specified org name from a keystore.
     * @param keyStore keystore from which to load the private key
     * @param orgName name of the private key org
     * @param password keystore password
     * @return PrivateKey
     */
    public static PrivateKey getKey(KeyStore keyStore, String password,
            String orgName) {
        try {
            PrivateKey key = (PrivateKey) keyStore.getKey(orgName,
                    password.toCharArray());
            if (key == null) {
                throw new RuntimeException("Unable to get key for "
                        + "name \"" + orgName + "\" using password \""
                        + password + "\" from keystore");
            }

            return key;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a PKCS12 keystore with the specified filename.
     * @param file keystore filename
     * @param password keystore password
     * @return KeyStore
     */
    public static KeyStore loadPKCS12KeyStore(String file, String password) {
        return loadKeyStore("pkcs12", file, password);
    }

    /**
     * Loads a JKS keystore with the specified filename.
     * @param file keystore filename
     * @param password keystore password
     * @return KeyStore
     */
    public static KeyStore loadJKSKeyStore(String file, String password) {
        return loadKeyStore("jks", file, password);
    }

    /**
     * Loads a keystore with the given type from the specified filename.
     * @param type type of the keystore
     * @param file keystore filename
     * @param password keystore password
     * @return KeyStore
     */
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

    /**
     * Loads private key and certificate with the specified org name
     * from a PKCS12 keystore.
     * @param file filename of the keystore
     * @param orgName name of the org
     * @param password keystore password
     * @return PKCS12 container containing the private key and certificate
     */
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
