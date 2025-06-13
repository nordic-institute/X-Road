/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.util;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.Providers;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import com.google.common.base.Splitter;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.crypto.identifier.Providers.SUN_RSA_SIGN;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;

/**
 * This class contains various security and certificate related utility methods.
 */
public final class CryptoUtils {

    static {
        try {
            Providers.init();
            CERT_FACTORY = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SSL protocol name.
     */
    public static final String SSL_PROTOCOL = "TLSv1.2";

    /**
     * Default digest algorithm id used for calculating certificate hashes.
     */
    public static final DigestAlgorithm DEFAULT_CERT_HASH_ALGORITHM_ID = DigestAlgorithm.SHA256;

    /**
     * Verification builder instance.
     */
    public static final JcaContentVerifierProviderBuilder BC_VERIFICATION_BUILDER =
            new JcaContentVerifierProviderBuilder().setProvider(BOUNCY_CASTLE);
    public static final JcaContentVerifierProviderBuilder SUN_VERIFICATION_BUILDER =
            new JcaContentVerifierProviderBuilder().setProvider(SUN_RSA_SIGN);

    /**
     * Holds the certificate factory instance.
     */
    public static final CertificateFactory CERT_FACTORY;


    private CryptoUtils() {
    }

    /**
     * Creates a new content signer with specified algorithm and private key.
     *
     * @param algorithm the algorithm
     * @param key       the private key
     * @return a new content signer instance
     * @throws OperatorCreationException if the content signer cannot be created
     */
    public static ContentSigner createContentSigner(SignAlgorithm algorithm, PrivateKey key) throws OperatorCreationException {
        return new JcaContentSignerBuilder(algorithm.name())
                .setProvider(BOUNCY_CASTLE)
                .build(key);
    }

    /**
     * Creates a new content verifier using default algorithm.
     *
     * @param key the private key
     * @return a new content verifier
     * @throws OperatorCreationException if the content signer cannot be created
     */
    public static ContentVerifierProvider createDefaultContentVerifier(
            PublicKey key) throws OperatorCreationException {
        if ("RSA".equals(key.getAlgorithm())) {
            // SunRsaSign supports only RSA signatures but it is (for some reason) about 2x faster
            // than the BC implementation
            return SUN_VERIFICATION_BUILDER.build(key);
        } else {
            return BC_VERIFICATION_BUILDER.build(key);
        }
    }

    /**
     * Creates a new certificate ID instance (using SHA-1 digest calculator)
     * for the specified subject and issuer certificates.
     *
     * @param subject the subject certificate
     * @param issuer  the issuer certificate
     * @return the certificate id
     * @throws Exception if the certificate if cannot be created
     */
    public static CertificateID createCertId(X509Certificate subject,
                                             X509Certificate issuer) throws Exception {
        return createCertId(subject.getSerialNumber(), issuer);
    }

    /**
     * Creates a new certificate ID instance (using SHA-1 digest calculator)
     * for the specified subject certificate serial number
     * and issuer certificate.
     *
     * @param subjectSerialNumber the subject certificate serial number
     * @param issuer              the issuer certificate
     * @return the certificate id
     * @throws Exception if the certificate id cannot be created
     */
    public static CertificateID createCertId(BigInteger subjectSerialNumber,
                                             X509Certificate issuer) throws Exception {
        return new CertificateID(Digests.createDigestCalculator(DigestAlgorithm.SHA1),
                new X509CertificateHolder(issuer.getEncoded()),
                subjectSerialNumber);
    }

    /**
     * Attempts to create an ASN1 primitive object from given byte array.
     *
     * @param data the byte array
     * @return ASN1Primitive object
     * @throws IOException if an error occurs
     */
    public static ASN1Primitive toDERObject(byte[] data) throws IOException {
        try (InputStream is = new ByteArrayInputStream(data)) {
            return new ASN1InputStream(is).readObject();
        }
    }

    /**
     * Reads X509Certificate object from given base64 data.
     *
     * @param base64data the certificate in base64
     * @return the read certificate
     * @throws CertificateException if certificate could not be read
     * @throws IOException          if an I/O error occurred
     */
    public static X509Certificate readCertificate(String base64data)
            throws CertificateException, IOException {
        return readCertificate(decodeBase64(base64data));
    }

    /**
     * Reads X509Certificate object from given certificate bytes.
     *
     * @param certBytes the certificate bytes
     * @return the read certificate
     */
    @SneakyThrows
    public static X509Certificate readCertificate(byte[] certBytes) {
        try (InputStream is = new ByteArrayInputStream(certBytes)) {
            return readCertificate(is);
        }
    }

    /**
     * Reads X509Certificate chain from given certificate bytes.
     *
     * @param certBytes the certificate chain bytes
     * @return the read certificate collection
     */
    @SneakyThrows
    public static Collection<X509Certificate> readCertificates(byte[] certBytes) {
        try (InputStream is = new ByteArrayInputStream(certBytes)) {
            return readCertificates(is);
        }
    }

    /**
     * Reads X509Certificate object from given input stream.
     *
     * @param is Input stream containing certificate bytes.
     * @return the read certificate
     */
    @SneakyThrows
    public static X509Certificate readCertificate(InputStream is) {
        return (X509Certificate) CERT_FACTORY.generateCertificate(is);
    }

    /**
     * Reads X509Certificate chain from given input stream.
     *
     * @param is Input stream containing certificate bytes.
     * @return the read certificate chain
     */
    @SneakyThrows
    public static Collection<X509Certificate> readCertificates(InputStream is) {
        return CERT_FACTORY.generateCertificates(is).stream()
                .map(X509Certificate.class::cast)
                .toList();
    }

    /**
     * Calculates digest of the certificate and encodes it as lowercase hex.
     *
     * @param cert the certificate
     * @return calculated certificate hex hash String
     * @throws CertificateEncodingException if a certificate encoding error occurs
     * @throws OperatorCreationException    if digest calculator cannot be created
     * @throws IOException                  if an I/O error occurred
     */
    public static String calculateCertHexHash(X509Certificate cert)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        return calculateCertHexHash(cert.getEncoded());
    }

    /**
     * Calculates digest of the certificate and encodes it as uppercase hex with the given delimiter every 2 characters.
     *
     * @param cert      the certificate
     * @param delimiter the delimiter to use
     * @return calculated certificate hex hash String
     * @throws CertificateEncodingException if a certificate encoding error occurs
     * @throws OperatorCreationException    if digest calculator cannot be created
     * @throws IOException                  if an I/O error occurred
     */
    public static String calculateDelimitedCertHexHash(X509Certificate cert, String delimiter)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        return String.join(delimiter, Splitter.fixedLength(2).split(calculateCertHexHash(cert).toUpperCase()));
    }

    /**
     * Calculates a sha-256 digest of the given bytes and encodes it
     * as lowercase hex.
     *
     * @param bytes the bytes
     * @return calculated certificate hex hash String
     * @throws OperatorCreationException if digest calculator cannot be created
     * @throws IOException               if an I/O error occurred
     */
    public static String calculateCertHexHash(byte[] bytes) throws IOException, OperatorCreationException {
        return Digests.hexDigest(DEFAULT_CERT_HASH_ALGORITHM_ID, bytes);
    }

    /**
     * Calculates a sha-256 digest of the given bytes and encodes it in
     * format 92:62:34:C5:39:1B:95:1F:BF:AF:8D:D6:23:24:AE:56:83:DC...
     *
     * @param bytes the bytes
     * @return calculated certificate hex hash uppercase and separated by semicolons String
     * @throws HexCalculationException if any errors occur
     */
    public static String calculateCertHexHashDelimited(byte[] bytes) {
        try {
            return calculateCertHexHash(bytes).toUpperCase().replaceAll("(?<=..)(..)", ":$1");
        } catch (Exception e) {
            throw new HexCalculationException(e);
        }
    }

    /**
     * Calculates a digest of the given certificate.
     *
     * @param cert the certificate
     * @return digest byte array of the certificate
     * @throws CertificateEncodingException if a certificate encoding error occurs
     * @throws OperatorCreationException    if digest calculator cannot be created
     * @throws IOException                  if an I/O error occurred
     */
    public static byte[] certHash(X509Certificate cert) throws CertificateEncodingException, IOException, OperatorCreationException {
        return certHash(cert.getEncoded());
    }

    /**
     * Calculates a digest of the given certificate bytes.
     *
     * @param bytes the bytes
     * @return digest byte array of the certificate
     * @throws OperatorCreationException if digest calculator cannot be created
     * @throws IOException               if an I/O error occurred
     */
    public static byte[] certHash(byte[] bytes) throws IOException, OperatorCreationException {
        return Digests.calculateDigest(DEFAULT_CERT_HASH_ALGORITHM_ID, bytes);
    }

    /**
     * Loads a pkcs12 keystore from a file.
     *
     * @param keyStoreInputStream stream that will be read. NOTE: The stream will not be closed.
     * @param password            the password for the key store
     * @return the loaded keystore
     * @throws Exception if any errors occur
     */
    public static KeyStore loadPkcs12KeyStore(InputStream keyStoreInputStream, char[] password)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
        return loadKeyStore("pkcs12", keyStoreInputStream, password);
    }

    /**
     * Loads a pkcs12 keystore from a file.
     *
     * @param file     the file to load
     * @param password the password for the key store
     * @return the loaded keystore
     * @throws Exception if any errors occur
     */
    public static KeyStore loadPkcs12KeyStore(File file, char[] password)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
        return loadKeyStore("pkcs12", file, password);
    }

    /**
     * Loads a key store from a file.
     *
     * @param type                the type of key store to load ("pkcs12" for PKCS12 type)
     * @param keystoreInputStream stream that will be read. NOTE: The stream will not be closed.
     * @param password            the password for the key store
     * @return the loaded keystore
     * @throws Exception if any errors occur
     */
    public static KeyStore loadKeyStore(String type, InputStream keystoreInputStream, char[] password)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(type);

        keyStore.load(keystoreInputStream, password);

        return keyStore;
    }

    /**
     * Loads a key store from a file.
     *
     * @param type     the type of key store to load ("pkcs12" for PKCS12 type)
     * @param file     the file to load
     * @param password the password for the key store
     * @return the loaded keystore
     * @throws Exception if any errors occur
     */
    public static KeyStore loadKeyStore(String type, File file, char[] password)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (FileInputStream fis = new FileInputStream(file)) {
            keyStore.load(fis, password);
        }

        return keyStore;
    }

    /**
     * Writes the given certificate bytes into the provided output stream in PEM format.
     *
     * @param certBytes bytes content of the certificate
     * @param out       output stream for writing the PEM formatted certificate
     * @throws IOException if an I/O error occurred
     */
    public static void writeCertificatePem(byte[] certBytes, OutputStream out)
            throws IOException {
        try (JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(out))) {
            writer.writeObject(readCertificate(certBytes));
        }
    }
}
