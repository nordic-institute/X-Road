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
package ee.ria.xroad.common.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;
import javax.xml.crypto.dsig.DigestMethod;

import ee.ria.xroad.common.SystemProperties;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.util.encoders.Hex;

import static org.apache.xml.security.signature.XMLSignature.*;

/**
 * This class contains various security and certificate related utility methods.
 */
public final class CryptoUtils {

    static {
        try {
            Security.addProvider(new BouncyCastleProvider());

            CERT_FACTORY = CertificateFactory.getInstance("X.509");
            KEY_FACTORY = KeyFactory.getInstance("RSA");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** SSL protocol name. */
    public static final String SSL_PROTOCOL = "TLSv1.2";

    /** The list of cipher suites used with SSL. */
    @Getter
    private static final String[] INCLUDED_CIPHER_SUITES =
            {"TLS_DHE_RSA_WITH_AES_256_CBC_SHA256"};

    /** Global default digest method identifier and URL. */
    public static final String DEFAULT_DIGEST_ALGORITHM_ID =
            CryptoUtils.SHA512_ID;
    public static final String DEFAULT_DIGEST_ALGORITHM_URI =
            DigestMethod.SHA512;

    /** Default digest algorithm id used for calculating certificate hashes. */
    public static final String DEFAULT_CERT_HASH_ALGORITHM_ID =
            CryptoUtils.SHA1_ID;

    /** Default digest algorithm id used for calculating configuration
        anchor hashes. */
    public static final String DEFAULT_ANCHOR_HASH_ALGORITHM_ID =
            CryptoUtils.SHA224_ID;

    /** Global default digital signature algorithm. */
    public static final String DEFAULT_SIGNATURE_ALGORITHM =
            CryptoUtils.SHA1WITHRSA_ID;

    public static final String ALGO_ID_DIGEST_SHA224 =
            "http://www.w3.org/2001/04/xmldsig-more#sha224";

    /** Hash algorithm identifier constants. */
    public static final String MD5_ID = "MD5";
    public static final String SHA1_ID = "SHA-1";
    public static final String SHA224_ID = "SHA-224";
    public static final String SHA256_ID = "SHA-256";
    public static final String SHA384_ID = "SHA-384";
    public static final String SHA512_ID = "SHA-512";

    /** Hash algorithm digest lengths. */
    public static final int SHA1_DIGEST_LENGTH = 20;
    public static final int SHA224_DIGEST_LENGTH = 28;
    public static final int SHA256_DIGEST_LENGTH = 32;
    public static final int SHA384_DIGEST_LENGTH = 48;
    public static final int SHA512_DIGEST_LENGTH = 64;

    /** Digital signature algorithms. */
    public static final String SHA1WITHRSA_ID = "SHA1withRSA";
    public static final String SHA256WITHRSA_ID = "SHA256withRSA";
    public static final String SHA384WITHRSA_ID = "SHA384withRSA";
    public static final String SHA512WITHRSA_ID = "SHA512withRSA";

    /** Digest provider instance. */
    public static final DigestCalculatorProvider DIGEST_PROVIDER =
            new BcDigestCalculatorProvider();

    /** Verification builder instance. */
    public static final JcaContentVerifierProviderBuilder BC_VERIFICATION_BUILDER =
            new JcaContentVerifierProviderBuilder().setProvider("BC");
    public static final JcaContentVerifierProviderBuilder SUN_VERIFICATION_BUILDER =
            new JcaContentVerifierProviderBuilder().setProvider("SunRsaSign");

    /** Holds the certificate factory instance. */
    public static final CertificateFactory CERT_FACTORY;

    /** Holds the RSA key factory instance. */
    public static final KeyFactory KEY_FACTORY;

    /** A cache of BouncyCastle algorithm identifiers */
    private static final Map<String, AlgorithmIdentifier>
            ALGORITHM_IDENTIFIER_CACHE = new HashMap<>();

    private CryptoUtils() {
    }

    /**
     * @return the digest algorithm identifier for the given algorithm id.
     * @param signatureAlgorithm the algorithm id
     *
     * @throws NoSuchAlgorithmException if the algorithm id is unknown
     */
    public static String getDigestAlgorithmId(String signatureAlgorithm)
            throws NoSuchAlgorithmException {
        switch (signatureAlgorithm) {
            case SHA1WITHRSA_ID:
                return SHA1_ID;
            case SHA256WITHRSA_ID:
                return SHA256_ID;
            case SHA384WITHRSA_ID:
                return SHA384_ID;
            case SHA512WITHRSA_ID:
                return SHA512_ID;
            default:
                throw new NoSuchAlgorithmException("Unkown signature algorithm id: "
                        + signatureAlgorithm);
        }
    }

    /**
     * @return the signature algorithm identifier for the given algorithm id.
     * @param digestAlgorithm the algorithm id
     *
     * @throws NoSuchAlgorithmException if the algorithm id is unknown
     */
    public static String getSignatureAlgorithmId(String digestAlgorithm)
            throws NoSuchAlgorithmException {
        switch (digestAlgorithm) {
            case SHA1_ID:
                return SHA1WITHRSA_ID;
            case SHA256_ID:
                return SHA256WITHRSA_ID;
            case SHA384_ID:
                return SHA384WITHRSA_ID;
            case SHA512_ID:
                return SHA512WITHRSA_ID;
            default:
                throw new NoSuchAlgorithmException("Unkown digest algorithm id: "
                        + digestAlgorithm);

        }
    }

    /**
     * Returns the digest/signature algorithm URI for the given digest/signature
     * algorithm identifier.
     * @param algoId the id of the algorithm
     * @return the URI of the algorithm
     * @throws NoSuchAlgorithmException if the algorithm id is unknown
     */
    public static String getDigestAlgorithmURI(String algoId)
            throws NoSuchAlgorithmException {
        switch (algoId) {
            case SHA1_ID:
                return DigestMethod.SHA1;
            case SHA256_ID:
                return DigestMethod.SHA256;
            case SHA512_ID:
                return DigestMethod.SHA512;
            default:
                throw new NoSuchAlgorithmException(
                        "Unknown algorithm id: " + algoId);
        }
    }

    /**
     * Returns the digest/signature algorithm URI for the given digest/signature
     * algorithm identifier.
     * @param algoId the id of the algorithm
     * @return the URI of the algorithm
     * @throws NoSuchAlgorithmException if the algorithm id is unknown
     */
    public static String getSignatureAlgorithmURI(String algoId)
            throws NoSuchAlgorithmException {
        switch (algoId) {
            case SHA1WITHRSA_ID:
                return ALGO_ID_SIGNATURE_RSA_SHA1;
            case SHA256WITHRSA_ID:
                return ALGO_ID_SIGNATURE_RSA_SHA256;
            case SHA384WITHRSA_ID:
                return ALGO_ID_SIGNATURE_RSA_SHA384;
            case SHA512WITHRSA_ID:
                 return ALGO_ID_SIGNATURE_RSA_SHA512;
             default:
                 throw new NoSuchAlgorithmException("Unknown algorithm id: " + algoId);
        }
    }

    /**
     * Returns the digest/signature algorithm identifier for the given
     * digest/signature algorithm URI.
     * @param algoURI the URI of the algorithm
     * @return the identifier of the algorithm
     * @throws NoSuchAlgorithmException if the algorithm URI is unknown
     */
    public static String getAlgorithmId(String algoURI)
            throws NoSuchAlgorithmException {
        switch (algoURI) {
        case DigestMethod.SHA1:
            return SHA1_ID;
        case ALGO_ID_DIGEST_SHA224:
            return SHA224_ID;
        case DigestMethod.SHA256:
            return SHA256_ID;
        case DigestMethod.SHA512:
            return SHA512_ID;
        case ALGO_ID_SIGNATURE_RSA_SHA1:
            return SHA1WITHRSA_ID;
        case ALGO_ID_SIGNATURE_RSA_SHA256:
            return SHA256WITHRSA_ID;
        case ALGO_ID_SIGNATURE_RSA_SHA384:
            return SHA384WITHRSA_ID;
        case ALGO_ID_SIGNATURE_RSA_SHA512:
            return SHA512WITHRSA_ID;
        default:
            throw new NoSuchAlgorithmException("Unknown algorithm URI: " + algoURI);
        }
    }

    /**
     * @return the cached AlgorithmIdentifier object for the given digest
     * algorithm identifier.
     *
     * @param alg the algorithm identifier
     */
    public static AlgorithmIdentifier getAlgorithmIdentifier(String alg) {
        if (!ALGORITHM_IDENTIFIER_CACHE.containsKey(alg)) {
            ALGORITHM_IDENTIFIER_CACHE.put(alg,
                    new DefaultDigestAlgorithmIdentifierFinder().find(alg));
        }

        return ALGORITHM_IDENTIFIER_CACHE.get(alg);
    }

    /**
     * Creates a new digest calculator with the specified algorithm identifier.
     * @param algorithm the algorithm identifier
     * @return a new digest calculator instance
     * @throws OperatorCreationException if the calculator cannot be created
     */
    public static DigestCalculator createDigestCalculator(
            AlgorithmIdentifier algorithm) throws OperatorCreationException {
        return DIGEST_PROVIDER.get(algorithm);
    }

    /**
     * Creates a new digest calculator with the specified algorithm name.
     * @param algorithm the algorithm name
     * @return a new digest calculator instance
     * @throws OperatorCreationException if the calculator cannot be created
     */
    public static DigestCalculator createDigestCalculator(String algorithm)
            throws OperatorCreationException {
        return createDigestCalculator(getAlgorithmIdentifier(algorithm));
    }

    /**
     * Creates a new content signer with specified algorithm and private key.
     * @param algorithm the algorithm
     * @param key the private key
     * @return a new content signer instance
     * @throws OperatorCreationException if the content signer cannot be created
     */
    public static ContentSigner createContentSigner(String algorithm,
            PrivateKey key) throws OperatorCreationException {
        return new JcaContentSignerBuilder(algorithm).build(key);
    }

    /**
     * Creates a new content signer using default algorithm.
     * @param key the private key
     * @return a new content signer instance
     * @throws OperatorCreationException if the content signer cannot be created
     */
    public static ContentSigner createDefaultContentSigner(
            PrivateKey key) throws OperatorCreationException {
        return createContentSigner(SystemProperties.getDefaultSignatureAlgorithm(), key);
    }

    /**
     * Creates a new content verifier using default algorithm.
     * @param key the private key
     * @return a new content verifier
     * @throws OperatorCreationException if the content signer cannot be created
     */
    public static ContentVerifierProvider createDefaultContentVerifier(
            PublicKey key) throws OperatorCreationException {
        if ("RSA" == key.getAlgorithm()) {
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
     * @param subject the subject certificate
     * @param issuer the issuer certificate
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
     * @param subjectSerialNumber the subject certificate serial number
     * @param issuer the issuer certificate
     * @return the certificate id
     * @throws Exception if the certificate if cannot be created
     */
    public static CertificateID createCertId(BigInteger subjectSerialNumber,
            X509Certificate issuer) throws Exception {
        return new CertificateID(createDigestCalculator(SHA1_ID),
                new X509CertificateHolder(issuer.getEncoded()),
                subjectSerialNumber);
    }

    /**
     * Attempts to create an ASN1 primitive object from given byte array.
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
     * Calculates message digest using the provided digest calculator.
     * @param dc the digest calculator
     * @param data the data
     * @return message digest
     * @throws IOException if the digest cannot be calculated
     */
    public static byte[] calculateDigest(DigestCalculator dc, byte[] data)
            throws IOException {
        dc.getOutputStream().write(data);
        dc.getOutputStream().close();
        return dc.getDigest();
    }

    /**
     * Calculates message digest using the provided digest calculator.
     * @param dc the digest calculator
     * @param data the data
     * @return message digest
     * @throws IOException if the digest cannot be calculated
     */
    public static byte[] calculateDigest(DigestCalculator dc, InputStream data)
            throws IOException {
        IOUtils.copy(data, dc.getOutputStream());
        dc.getOutputStream().close();
        return dc.getDigest();
    }

    /**
     * Calculates message digest using the provided algorithm.
     * @param algorithm the algorithm
     * @param data the data
     * @return message digest
     * @throws OperatorCreationException if digest calculator cannot be created
     * @throws IOException if an I/O error occurred
     */
    public static byte[] calculateDigest(AlgorithmIdentifier algorithm,
            byte[] data) throws OperatorCreationException, IOException {
        DigestCalculator dc = createDigestCalculator(algorithm);
        return calculateDigest(dc, data);
    }

    /**
     * Calculates message digest using the provided algorithm id.
     * @param algorithm the algorithm
     * @param data the data
     * @return message digest
     * @throws OperatorCreationException if digest calculator cannot be created
     * @throws IOException if an I/O error occurred
     */
    public static byte[] calculateDigest(String algorithm, byte[] data)
            throws OperatorCreationException, IOException {
        DigestCalculator dc = createDigestCalculator(algorithm);
        return calculateDigest(dc, data);
    }

    /**
     * Calculates message digest using the provided algorithm id.
     * @param algorithm the algorithm
     * @param data the data
     * @return message digest
     * @throws OperatorCreationException if digest calculator cannot be created
     * @throws IOException if an I/O error occurred
     */
    public static byte[] calculateDigest(String algorithm, InputStream data)
            throws OperatorCreationException, IOException {
        DigestCalculator dc = createDigestCalculator(algorithm);
        return calculateDigest(dc, data);
    }

    /**
     * Creates a base 64 encoded string from the given input string.
     * @param input the value to encode
     * @return base 64 encoded string
     */
    public static String encodeBase64(String input) {
        return DatatypeConverter.printBase64Binary(
                input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a base 64 encoded string from the given input bytes.
     * @param input the value to encode
     * @return base 64 encoded string
     */
    public static String encodeBase64(byte[] input) {
        return DatatypeConverter.printBase64Binary(input);
    }

    /**
     * Decodes a base 64 encoded string into byte array.
     * @param base64Str the base64 encoded string
     * @return decoded byte array
     */
    public static byte[] decodeBase64(String base64Str) {
        return DatatypeConverter.parseBase64Binary(base64Str);
    }

    /**
     * Hex-encodes the given byte array.
     * @param data the value to encode
     * @return hex encoded String of the data
     */
    public static String encodeHex(byte[] data) {
        return new String(Hex.encode(data));
    }

    /**
     * Generates X509 encoded public key bytes from a given modulus and
     * public exponent.
     * @param modulus the modulus
     * @param publicExponent the public exponent
     * @return generated public key bytes
     * @throws Exception if any errors occur
     */
    public static byte[] generateX509PublicKey(BigInteger modulus,
            BigInteger publicExponent) throws Exception {
        RSAPublicKeySpec rsaPublicKeySpec =
                new RSAPublicKeySpec(modulus, publicExponent);
        PublicKey javaRsaPublicKey =
                KEY_FACTORY.generatePublic(rsaPublicKeySpec);
        return generateX509PublicKey(javaRsaPublicKey);
    }

    /**
     * Generates X509 encoded public key bytes from a given public key.
     * @param publicKey the public key
     * @return generated public key bytes
     * @throws Exception if any errors occur
     */
    public static byte[] generateX509PublicKey(PublicKey publicKey)
            throws Exception {
        X509EncodedKeySpec x509EncodedPublicKey =
                KEY_FACTORY.getKeySpec(publicKey, X509EncodedKeySpec.class);
        return x509EncodedPublicKey.getEncoded();
    }

    /**
     * Reads a public key from X509 encoded bytes.
     * @param encoded the data
     * @return public key read from the bytes
     * @throws Exception if any errors occur
     */
    public static PublicKey readX509PublicKey(byte[] encoded)
            throws Exception {
        X509EncodedKeySpec x509EncodedPublicKey =
                new X509EncodedKeySpec(encoded);
        return KEY_FACTORY.generatePublic(x509EncodedPublicKey);
    }

    /**
     * Reads X509Certificate object from given base64 data.
     * @param base64data the certificate in base64
     * @return the read certificate
     * @throws CertificateException if certificate could not be read
     * @throws IOException if an I/O error occurred
     */
    public static X509Certificate readCertificate(String base64data)
            throws CertificateException, IOException {
        return readCertificate(decodeBase64(base64data));
    }

    /**
     * Reads X509Certificate object from given certificate bytes.
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
     * Reads X509Certificate object from given input stream.
     * @param is Input stream containing certificate bytes.
     * @return the read certificate
     */
    @SneakyThrows
    public static X509Certificate readCertificate(InputStream is) {
        return (X509Certificate) CERT_FACTORY.generateCertificate(is);
    }

    /**
     * Reads X509Certificate object from given base64 data.
     * @param base64data the certificate in base64
     * @return the collection of read certificates
     * @throws Exception if any errors occur
     */
    @SuppressWarnings("unchecked")
    public static Collection<X509Certificate> readCertificates(
            String base64data) throws Exception {
        try (InputStream is =
                new ByteArrayInputStream(decodeBase64(base64data))) {
            return (Collection<X509Certificate>)
                    CERT_FACTORY.generateCertificates(is);
        }
    }

    /**
     * Calculates digest of the certificate and encodes it as lowercase hex.
     * @param cert the certificate
     * @return calculated certificate hex hash String
     * @throws Exception if any errors occur
     */
    public static String calculateCertHexHash(X509Certificate cert)
            throws Exception {
        return hexDigest(DEFAULT_CERT_HASH_ALGORITHM_ID, cert.getEncoded());
    }

    /**
     * Calculates a sha-1 digest of the given bytes and encodes it
     * as lowercase hex.
     * @return calculated certificate hex hash String
     * @param bytes the bytes
     * @throws Exception if any errors occur
     */
    public static String calculateCertHexHash(byte[] bytes)
            throws Exception {
        return hexDigest(DEFAULT_CERT_HASH_ALGORITHM_ID, bytes);
    }

    /**
     * Calculates a digest of the given certificate.
     * @param cert the certificate
     * @return digest byte array of the certificate
     * @throws Exception if any errors occur
     */
    public static byte[] certHash(X509Certificate cert) throws Exception {
        return certHash(cert.getEncoded());
    }

    /**
     * Calculates a digest of the given certificate bytes.
     * @param bytes the bytes
     * @return digest byte array of the certificate
     * @throws Exception if any errors occur
     */
    public static byte[] certHash(byte[] bytes) throws Exception {
        return calculateDigest(DEFAULT_CERT_HASH_ALGORITHM_ID, bytes);
    }

    /**
     * Digests the input data and hex-encodes the result.
     * @param hashAlg Name of the hash algorithm
     * @param data Data to be hashed
     * @return hex encoded String of the input data digest
     * @throws Exception if any errors occur
     */
    public static String hexDigest(String hashAlg, byte[] data)
            throws Exception {
        return encodeHex(calculateDigest(hashAlg, data));
    }

    /**
     * Digests the input data and hex-encodes the result.
     * @param hashAlg Name of the hash algorithm
     * @param data Data to be hashed
     * @return hex encoded String of the input data digest
     * @throws Exception if any errors occur
     */
    public static String hexDigest(String hashAlg, String data)
            throws Exception {
        return hexDigest(hashAlg, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Loads a pkcs12 keystore from a file.
     * @param file the file to load
     * @param password the password for the key store
     * @return the loaded keystore
     * @throws Exception if any errors occur
     */
    public static KeyStore loadPkcs12KeyStore(File file, char[] password)
            throws Exception {
        return loadKeyStore("pkcs12", file, password);
    }

    /**
     * Loads a key store from a file.
     * @param type the type of key store to load ("pkcs12" for PKCS12 type)
     * @param file the file to load
     * @param password the password for the key store
     * @return the loaded keystore
     * @throws Exception if any errors occur
     */
    public static KeyStore loadKeyStore(String type, File file,
            char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (FileInputStream fis = new FileInputStream(file)) {
            keyStore.load(fis, password);
        }

        return keyStore;
    }

    /**
     * Writes the given certificate bytes into the provided output stream in PEM format.
     * @param certBytes bytes content of the certificate
     * @param out output stream for writing the PEM formatted certificate
     * @throws IOException if an I/O error occurred
     */
    public static void writeCertificatePem(byte[] certBytes, OutputStream out)
            throws IOException {
        try (PEMWriter writer = new PEMWriter(new OutputStreamWriter(out))) {
            writer.writeObject(readCertificate(certBytes));
        }
    }
}
