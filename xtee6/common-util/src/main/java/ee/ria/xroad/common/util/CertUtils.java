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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.toDERObject;

/**
 * Contains utility methods for working with certificates.
 */
@Slf4j
public final class CertUtils {

    private static final int DIGITAL_SIGNATURE_IDX = 0;
    private static final int KEY_ENCIPHERMENT_IDX = 2;
    private static final int DATA_ENCIPHERMENT_IDX = 3;

    private CertUtils() {
    }

    /**
     * @param cert certificate for which to get the subject common name
     * @return short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    public static String getSubjectCommonName(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());

        String cn = getRDNValue(x500name, BCStyle.CN);
        if (cn == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain common name");
        }

        return cn;
    }

    /**
     * @param cert certificate from which to get the subject serial number
     * @return the SerialNumber component of the Subject field.
     */
    public static String getSubjectSerialNumber(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());
        return getRDNValue(x500name, BCStyle.SERIALNUMBER);
    }

    /**
     * @param cert certificate from which to construct the client ID
     * @return a fully constructed Client identifier from DN of the certificate.
     */
    public static ClientId getSubjectClientId(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());

        String c = getRDNValue(x500name, BCStyle.C);
        if (c == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain country code");
        }

        String o = getRDNValue(x500name, BCStyle.O);
        if (o == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain organization");
        }

        String cn = getRDNValue(x500name, BCStyle.CN);
        if (cn == null) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Certificate subject name does not contain common name");
        }

        return ClientId.create(c, o, cn);
    }

    /**
     * Checks if this certificate is an authentication certificate.
     * The certificate is an authentication certificate, if it has
     * ExtendedKeyUsage extension which contains
     * <pre>ClientAuthentication</pre> or if it has keyUsage extension
     * which has <pre>digitalSignature</pre>, <pre>keyEncipherment</pre>
     * or <pre>dataEncipherment</pre> bit set.
     * @param cert certificate to check
     * @return boolean
     * @throws Exception if the cert has no keyUsage extension
     */
    public static boolean isAuthCert(X509Certificate cert) throws Exception {
        List<String> extendedKeyUsage = cert.getExtendedKeyUsage();
        if (extendedKeyUsage != null
                && extendedKeyUsage.contains("1.3.6.1.5.5.7.3.2")) {
            return true;
        }

        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage == null) {
            throw new RuntimeException(
                    "Certificate does not contain keyUsage extension");
        }

        return keyUsage[DIGITAL_SIGNATURE_IDX] || keyUsage[KEY_ENCIPHERMENT_IDX]
                || keyUsage[DATA_ENCIPHERMENT_IDX];
    }

    /**
     * Checks if this certificate is a signing certificate.
     * The certificate is a signing certificate, if it has keyUsage extension
     * which has <pre>nonRepudiation</pre> bit set.
     * @param cert certificate to check
     * @return boolean
     * @throws Exception if the cert has no keyUsage extension
     */
    public static boolean isSigningCert(X509Certificate cert)
            throws Exception {
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage == null) {
            throw new RuntimeException(
                    "Certificate does not contain keyUsage extension");
        }

        return keyUsage[1]; // nonRepudiation
    }

    /**
     * Checks if the certificate is valid at the current time.
     * @param cert certificate to check
     * @return boolean
     */
    public static boolean isValid(X509Certificate cert) {
        try {
            cert.checkValidity();
            return true;
        } catch (CertificateExpiredException
                | CertificateNotYetValidException ignored) {
            log.info("Certificate not valid: {}", ignored);
            return false;
        }
    }

    /**
     * Checks if the certificate is self-signed.
     * @param cert certificate to check
     * @return boolean
     */
    public static boolean isSelfSigned(X509Certificate cert) {
        return cert.getIssuerX500Principal().equals(
                cert.getSubjectX500Principal());
    }

    /**
     * @param subject certificate from which to get the OCSP responder URI
     * @return OCSP responder URI from given certificate.
     * @throws IOException if an I/O error occurred
     */
    public static String getOcspResponderUriFromCert(X509Certificate subject)
            throws IOException {
        final byte[] extensionValue = subject.getExtensionValue(
                Extension.authorityInfoAccess.toString());
        if (extensionValue != null) {
            ASN1Primitive derObject = toDERObject(extensionValue);
            if (derObject instanceof DEROctetString) {
                DEROctetString derOctetString = (DEROctetString) derObject;
                derObject = toDERObject(derOctetString.getOctets());

                AuthorityInformationAccess authorityInformationAccess =
                        AuthorityInformationAccess.getInstance(derObject);
                AccessDescription[] descriptions =
                        authorityInformationAccess.getAccessDescriptions();
                for (AccessDescription desc : descriptions) {
                    if (desc.getAccessMethod().equals(
                            AccessDescription.id_ad_ocsp)) {
                        GeneralName generalName = desc.getAccessLocation();
                        return generalName.getName().toString();
                    }
                }
            }
        }

        return null;
    }

    /**
     * @param certs list of certificates
     * @return list of certificate hashes for given list of certificates.
     * @throws Exception in case of any errors
     */
    public static String[] getCertHashes(List<X509Certificate> certs)
            throws Exception {
        String[] certHashes = new String[certs.size()];
        for (int i = 0; i < certs.size(); i++) {
            certHashes[i] = calculateCertHexHash(certs.get(i));
        }

        return certHashes;
    }

    /**
     * Returns the RDN value from the X500Name.
     */
    static String getRDNValue(X500Name name, ASN1ObjectIdentifier id) {
        RDN[] cnList = name.getRDNs(id);
        if (cnList.length == 0) {
            return null;
        }

        return IETFUtils.valueToString(cnList[0].getFirst().getValue());
    }

    /**
     * Generates certificate request
     * @return byte content of the certificate request
     */
    static byte[] generateCertRequest(PrivateKey privateKey, PublicKey publicKey, String principal)
            throws NoSuchAlgorithmException, OperatorCreationException, IOException {
        X500Principal subject = new X500Principal(principal);

        ContentSigner signGen = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);

        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(subject, publicKey);
        PKCS10CertificationRequest csr = builder.build(signGen);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (Writer destination = new OutputStreamWriter(output)) {
                try (JcaPEMWriter pemWriter = new JcaPEMWriter(destination)) {
                    pemWriter.writeObject(csr);
                }
                return output.toByteArray();
            }
        }
    }

    /**
     * Read private and public keys from PEM file
     * @param filename file containing the keypair
     * @return KeyPair
     * @throws NoSuchAlgorithmException when algorithm for decoding is not available
     * @throws InvalidKeySpecException when key file is invalid
     * @throws IOException when I/O error occurs
     */
    public static KeyPair readKeyPairFromPemFile(String filename)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        File pkFile = new File(filename);
        try (PEMParser pemParser = new PEMParser(new FileReader(pkFile))) {
            Object o = pemParser.readObject();
            if (o == null || !(o instanceof PrivateKeyInfo)) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not read key from '%s'", filename);
            }
            PrivateKeyInfo pki = (PrivateKeyInfo) o;
            KeyFactory kf = KeyFactory.getInstance("RSA");
            final PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(pki.getEncoded());
            final PrivateKey privateKey = kf.generatePrivate(ks);
            final RSAPrivateKey rpk = RSAPrivateKey.getInstance(pki.parsePrivateKey());
            final PublicKey publicKey = kf.generatePublic(new RSAPublicKeySpec(rpk.getModulus(),
                    rpk.getPublicExponent()));
            KeyPair kp = new KeyPair(publicKey, privateKey);
            return kp;
        }
    }

    /**
     * Write certificate to file
     * @param certBytes raw bytes of the certificate
     * @param filename output file
     * @throws IOException when I/O error occurs
     */
    public static void writePemToFile(byte[] certBytes, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(certBytes);
        }
    }

    /**
     * Read X509 certificate from file
     * @param filename input file
     * @return X509Certificate
     * @throws CertificateException when certificate is invalid
     * @throws FileNotFoundException when file is not found
     * @throws IOException when I/O error occurs
     */
    public static X509Certificate readCertificate(String filename) throws CertificateException, IOException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        try (FileInputStream is = new FileInputStream(filename)) {
            X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
            return cer;
        }
    }

    /**
     * Create and write pkcs12 keystore to file
     * @param filenameKey pem file containing private key
     * @param filenamePem pem file containing certificate
     * @param filenameP12 output filename of the .p12 keystore
     * @throws Exception when error occurs
     */
    public static void createPkcs12(String filenameKey, String filenamePem, String filenameP12) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        KeyPair keyPair = readKeyPairFromPemFile(filenameKey);
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        X509Certificate cert = readCertificate(filenamePem);
        Certificate[] outChain = {cert};

        KeyStore outStore = KeyStore.getInstance("PKCS12");
        outStore.load(null, InternalSSLKey.getKEY_PASSWORD());
        outStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, privateKey, InternalSSLKey.getKEY_PASSWORD(), outChain);
        try (OutputStream outputStream = new FileOutputStream(filenameP12)) {
            outStore.store(outputStream, InternalSSLKey.getKEY_PASSWORD());
            outputStream.flush();
        }
    }
}
