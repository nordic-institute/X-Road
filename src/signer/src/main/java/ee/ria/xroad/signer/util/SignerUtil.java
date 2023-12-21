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
package ee.ria.xroad.signer.util;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import jakarta.xml.bind.DatatypeConverter;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;

import static ee.ria.xroad.common.util.CryptoUtils.SHA1WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA256WITHRSAANDMGF1_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA256WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA384WITHRSAANDMGF1_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA384WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA512WITHRSAANDMGF1_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA512WITHRSA_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertSha1HexHash;

/**
 * Collection of various utility methods.
 */
public final class SignerUtil {

    private static final int RANDOM_ID_LENGTH = 20;

    private SignerUtil() {
    }

    /**
     * Returns the digest prefix bytes for the given digest. The digest must
     * be calculated using one of the following algorithms: SHA1, SHA224,
     * SHA256, SHA384, SHA512.
     *
     * @param digest the digest
     * @return the digest prefix bytes for the given digest
     */
    private static byte[] getDigestInfoPrefix(byte[] digest) {
        return DigestPrefixCache.getPrefix(digest);
    }

    /**
     * Creates data to be signed from the digest.
     *
     * @param digest     the digest
     * @param signAlgoId sign algorithm id
     * @return the data to be signed
     * @throws NoSuchAlgorithmException if the algorithm is not supported
     */
    public static byte[] createDataToSign(byte[] digest, String signAlgoId) throws NoSuchAlgorithmException {
        return switch (signAlgoId) {
            case SHA256WITHRSAANDMGF1_ID, SHA384WITHRSAANDMGF1_ID, SHA512WITHRSAANDMGF1_ID -> digest; // Nothing to do
            case SHA1WITHRSA_ID, SHA256WITHRSA_ID, SHA384WITHRSA_ID, SHA512WITHRSA_ID -> createDataToSign(digest);
            default -> throw new NoSuchAlgorithmException("Unknown sign algorithm id: " + signAlgoId);
        };

    }

    private static byte[] createDataToSign(byte[] digest) {
        byte[] prefix = getDigestInfoPrefix(digest);
        byte[] digestInfo = new byte[prefix.length + digest.length];

        System.arraycopy(prefix, 0, digestInfo, 0, prefix.length);
        System.arraycopy(digest, 0, digestInfo, prefix.length, digest.length);

        return digestInfo;
    }

    /**
     * Creates a key id (lexical representation of xsd:hexBinary)
     * from the specified key object.
     *
     * @param k the key
     * @return the id
     */
    public static String keyId(iaik.pkcs.pkcs11.objects.Key k) {
        if (k.getId() == null || k.getId().getByteArrayValue() == null) {
            return null;
        }

        return DatatypeConverter.printHexBinary(k.getId().getByteArrayValue());
    }

    /**
     * Creates a key id (lexical representation of xsd:hexBinary)
     * from the specified certificate object.
     *
     * @param c the certificate object
     * @return the id
     */
    public static String keyId(
            iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate c) {
        if (c.getId() == null || c.getId().getByteArrayValue() == null) {
            return null;
        }

        return DatatypeConverter.printHexBinary(c.getId().getByteArrayValue());
    }

    /**
     * Creates a certificate. The certificate is valid for 2 years.
     *
     * @param commonName the common name attribute
     * @param keyPair    the key pair containing the public key
     * @param signer     the signer of the certificate
     * @return the certificate
     * @throws Exception if an error occurs
     */
    public static X509Certificate createCertificate(String commonName, KeyPair keyPair, ContentSigner signer)
            throws Exception {
        Calendar cal = GregorianCalendar.getInstance();

        cal.add(Calendar.YEAR, -1);
        Date notBefore = cal.getTime();

        cal.add(Calendar.YEAR, 2);
        Date notAfter = cal.getTime();

        X500Name subject = new X500Name("CN=" + commonName);

        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        subject, BigInteger.ONE, notBefore, notAfter,
                        subject, keyPair.getPublic());

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    /**
     * @return a random identifier (lexical representation of xsd:hexBinary)
     */
    public static String randomId() {
        return DatatypeConverter.printHexBinary(generateId());
    }

    /**
     * @return an array of random bytes
     */
    public static byte[] generateId() {
        byte[] id = new byte[RANDOM_ID_LENGTH];
        new Random().nextBytes(id);
        return id;
    }

    /**
     * @param tokenInfo the token
     * @return returns the token worker id consisting of the token type, label
     * and serial number (if available)
     */
    public static String getWorkerId(TokenInfo tokenInfo) {
        String workerId = tokenInfo.getType();
        if (tokenInfo.getSerialNumber() != null
                && tokenInfo.getLabel() != null) {
            workerId += "-" + tokenInfo.getSerialNumber();
            workerId += "-" + tokenInfo.getLabel();
        }

        return workerId;
    }

    /**
     * @param certSha1Hash the certificate SHA-1 hash in HEX
     * @return certificate matching certHash
     * @throws CertificateEncodingException if a certificate encoding error occurs
     * @throws OperatorCreationException if digest calculator cannot be created
     * @throws IOException if an I/O error occurred
     */
    public static X509Certificate getCertForCertHash(String certSha1Hash)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        X509Certificate cert = TokenManager.getCertificateForCerHash(certSha1Hash);
        if (cert != null) {
            return cert;
        }

        // not in key conf, look elsewhere
        for (X509Certificate caCert : GlobalConf.getAllCaCerts()) {
            if (certSha1Hash.equals(calculateCertSha1HexHash(caCert))) {
                return caCert;
            }
        }
        return null;
    }

    /**
     * @param tokenIdFormat the format of the token ID
     * @param moduleType    module type
     * @param token         pkcs11 token
     * @return formatted token ID
     */
    @SneakyThrows
    public static String getFormattedTokenId(String tokenIdFormat, String moduleType,
            iaik.pkcs.pkcs11.Token token) {
        iaik.pkcs.pkcs11.TokenInfo tokenInfo = token.getTokenInfo();
        String slotIndex = Long.toString(token.getSlot().getSlotID());

        return tokenIdFormat.replace("{moduleType}", moduleType)
                .replace("{slotIndex}", slotIndex)
                .replace("{serialNumber}", tokenInfo.getSerialNumber().trim())
                .replace("{label}", tokenInfo.getLabel().trim());
    }

}

