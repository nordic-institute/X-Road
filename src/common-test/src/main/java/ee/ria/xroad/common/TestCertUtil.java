/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.SneakyThrows;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Contains various certificate related utility methods for using in test cases,
 * all in one place.
 * <p>
 * Whenever you need to use a certificate in a test class, use this class. Add
 * necessary convenience methods if needed.
 */
public final class TestCertUtil {

    /**
     * Hard coded path to all the certificates.
     */
    private static final String CERT_PATH = "/";

    private static final String CERT_ERROR_MSG = "Unable to get certificate for name \"%1$s\" from keystore";
    private static final String CERT_ERROR_WITH_PASSWD_MSG = CERT_ERROR_MSG + " using password \"%2$s\"";

    /**
     * Lazily initialized cached instances of the certs.
     */
    private static volatile X509Certificate tspCert;
    private static volatile PKCS12 producer;
    private static volatile PKCS12 consumer;
    private static volatile PKCS12 ca2TestOrg;
    private static volatile PKCS12 ocspSigner;
    private static volatile PKCS12 internal;

    private static volatile PKCS12 ca;

    private static final class ClientKeyHolder {
        private static final PKCS12 INSTANCE = loadPKCS12("client.p12", "1", "test");
    }

    private TestCertUtil() {
    }

    /**
     * Tiny container to keep the certificate and private key together.
     */
    public static final class PKCS12 {
        public final X509Certificate[] certChain;
        public final PrivateKey key;

        private PKCS12(X509Certificate[] certChain, PrivateKey key) {
            this.certChain = certChain;
            this.key = key;
        }
    }

    // -- Certificate retrieval methods ------------------------------------- //

    /**
     * @return AdminCA1 from test resources
     */
    public static X509Certificate getCaCert() {
        return getCa().certChain[0];
    }

    /**
     * @return AdminCA1 from test resources
     */
    public static PKCS12 getCa() {
        if (ca == null) {
            ca = loadPKCS12("root-ca.p12", "1", "test");
        }
        return ca;
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

    public static PKCS12 getClient() {
        return ClientKeyHolder.INSTANCE;
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
     * @return internal keystore from test resources
     */
    public static PKCS12 getInternalKey() {
        if (internal == null) {
            internal = TestCertUtil.loadPKCS12("internal.p12", "1", "test");
        }
        return internal;
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
     *
     * @param pemFileName filename of the certificate
     * @return X509Certificate
     */
    public static X509Certificate getCert(String pemFileName) {
        try (InputStream is = getFile(pemFileName)) {
            return CryptoUtils.readCertificate(Base64.getMimeDecoder().wrap(is));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a certificate with the specified org name from a keystore.
     *
     * @param keyStore keystore from which to load the certificate
     * @param orgName  name of the certificate org
     * @return X509Certificate
     */
    public static X509Certificate getCert(KeyStore keyStore, String orgName) {
        try {
            X509Certificate cert =
                    (X509Certificate) keyStore.getCertificate(orgName);
            if (cert == null) {
                throw new RuntimeException(String.format(CERT_ERROR_MSG, orgName));
            }

            return cert;
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a certificate with the specified org name from a keystore.
     *
     * @param keyStore keystore from which to load the certificate
     * @param orgName  name of the certificate org
     * @return X509Certificate
     */
    public static X509Certificate[] getCertChain(KeyStore keyStore, String orgName) {
        try {
            final Certificate[] chain = keyStore.getCertificateChain(orgName);
            if (chain == null || chain.length == 0) {
                throw new RuntimeException(String.format(CERT_ERROR_MSG, orgName));
            }

            X509Certificate[] tmp = new X509Certificate[chain.length];
            System.arraycopy(chain, 0, tmp, 0, chain.length);
            return tmp;
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a private key with the specified org name from a keystore.
     *
     * @param keyStore keystore from which to load the private key
     * @param orgName  name of the private key org
     * @param password keystore password
     * @return PrivateKey
     */
    public static PrivateKey getKey(KeyStore keyStore, String password,
                                    String orgName) {
        try {
            PrivateKey key = (PrivateKey) keyStore.getKey(orgName,
                    password.toCharArray());
            if (key == null) {
                throw new RuntimeException(
                        String.format(CERT_ERROR_WITH_PASSWD_MSG, orgName, password));
            }

            return key;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a PKCS12 keystore with the specified filename.
     *
     * @param file     keystore filename
     * @param password keystore password
     * @return KeyStore
     */
    public static KeyStore loadPKCS12KeyStore(String file, String password) {
        return loadKeyStore("pkcs12", file, password);
    }

    /**
     * Loads a JKS keystore with the specified filename.
     *
     * @param file     keystore filename
     * @param password keystore password
     * @return KeyStore
     */
    public static KeyStore loadJKSKeyStore(String file, String password) {
        return loadKeyStore("jks", file, password);
    }

    /**
     * Loads a keystore with the given type from the specified filename.
     *
     * @param type     type of the keystore
     * @param file     keystore filename
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
     *
     * @param file     filename of the keystore
     * @param orgName  name of the org
     * @param password keystore password
     * @return PKCS12 container containing the private key and certificate
     */
    public static PKCS12 loadPKCS12(String file, String orgName,
                                    String password) {
        KeyStore orgKeyStore = loadPKCS12KeyStore(CERT_PATH + file, password);
        return new PKCS12(getCertChain(orgKeyStore, orgName), getKey(orgKeyStore, password, orgName));
    }

    public static KeyStore getKeyStore(String name) {
        return loadPKCS12KeyStore(CERT_PATH + name + ".p12", "test");
    }

    public static char[] getKeyStorePassword(String name) {
        return "test".toCharArray();
    }

    public static byte[] generateAuthCert() throws NoSuchAlgorithmException, OperatorCreationException, IOException {
        var subjectKey = getKeyPairGenerator().generateKeyPair();
        return generateAuthCert(subjectKey.getPublic());
    }

    @SneakyThrows
    @SuppressWarnings({"checkstyle:MagicNumber", "java:S4426"})
    public static KeyPairGenerator getKeyPairGenerator() {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        return keyPairGenerator;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static byte[] generateAuthCert(PublicKey subjectKey) throws OperatorCreationException, IOException {

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(getCa().key);
        var issuer = ca.certChain[0].getSubjectX500Principal();
        var subject = new X500Principal("CN=Subject");

        return new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.ONE,
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                subjectKey)
                .addExtension(Extension.create(
                        Extension.keyUsage,
                        true,
                        new KeyUsage(KeyUsage.digitalSignature)))
                .build(signer)
                .getEncoded();

    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @SneakyThrows
    public static X509Certificate generateSignCert(PublicKey subjectKey, ClientId id) {

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(getCa().key);
        var issuer = ca.certChain[0].getSubjectX500Principal();
        var subject = new X500Principal(
                //EJBCA Profile format
                String.format("C=%s,O=%s,CN=%s",
                        id.getXRoadInstance(),
                        id.getMemberClass(),
                        id.getMemberCode()));

        var cert = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.TWO,
                Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                subjectKey)
                .addExtension(Extension.create(
                        Extension.keyUsage,
                        true,
                        new KeyUsage(KeyUsage.nonRepudiation)))
                .build(signer);

        return new JcaX509CertificateConverter().getCertificate(cert);
    }

    private static InputStream getFile(String fileName) throws Exception {
        InputStream is = TestCertUtil.class.getResourceAsStream(fileName);
        if (is == null) {
            throw new FileNotFoundException(fileName);
        }

        return is;
    }
}
