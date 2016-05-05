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
package ee.ria.xroad.signer.util;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import scala.concurrent.Await;

import javax.xml.bind.DatatypeConverter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;

/**
 * Collection of various utility methods.
 */
public final class SignerUtil {

    private static final int RANDOM_ID_LENGTH = 20;

    private static final Timeout DEFAULT_ASK_TIMEOUT = new Timeout(5000, TimeUnit.MILLISECONDS);

    private SignerUtil() {
    }

    /**
     * Returns the digest prefix bytes for the given digest. The digest must
     * be calculated using one of the following algorithms: SHA1, SHA224,
     * SHA256, SHA384, SHA512.
     * @param digest the digest
     * @return the digest prefix bytes for the given digest
     */
    public static byte[] getDigestInfoPrefix(byte[] digest) {
        return DigestPrefixCache.getPrefix(digest);
    }

    /**
     * Creates data to be signed from the digest.
     * @param digest the digest
     * @return the data to be signed
     */
    public static byte[] createDataToSign(byte[] digest) {
        byte[] prefix = getDigestInfoPrefix(digest);
        byte[] digestInfo = new byte[prefix.length + digest.length];

        System.arraycopy(prefix, 0, digestInfo, 0, prefix.length);
        System.arraycopy(digest, 0, digestInfo, prefix.length, digest.length);

        return digestInfo;
    }

    /**
     * @param tokenInfo the token
     * @param keyId the key id
     * @return true if the token contains a key with the specified id
     */
    public static boolean hasKey(TokenInfo tokenInfo, String keyId) {
        for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
            if (keyInfo.getId().equals(keyId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a key id (lexical representation of xsd:hexBinary)
     * from the specified key object.
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
     * @param commonName the common name attribute
     * @param keyPair the key pair containing the public key
     * @param signer the signer of the certificate
     * @return the certificate
     * @throws Exception if an error occurs
     */
    public static X509Certificate createCertificate(String commonName,
            KeyPair keyPair, ContentSigner signer) throws Exception {
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
     * Convenience method for sending a message to an actor and returning
     * the result.
     * @param actor the actor
     * @param message the message
     * @return the result
     * @throws Exception if an error occurs or if the result times out
     */
    public static Object ask(ActorRef actor, Object message) throws Exception {
        return ask(actor, message, DEFAULT_ASK_TIMEOUT);
    }


    /**
     * Convenience method for sending a message to an actor and returning
     * the result.
     * @param actor the actor
     * @param message the message
     * @param timeout the timeout for the result
     * @return the result
     * @throws Exception if an error occurs or if the result times out
     */
    public static Object ask(ActorRef actor, Object message, Timeout timeout)
            throws Exception {
        return Await.result(Patterns.ask(actor, message,
                timeout.duration().length()), timeout.duration());
    }

    /**
     * Convenience method for sending a message to an actor selection
     * and returning the result.
     * @param actorSelection the actor selection
     * @param message the message
     * @return the result
     * @throws Exception if an error occurs or if the result times out
     */
    public static Object ask(ActorSelection actorSelection, Object message)
            throws Exception {
        return ask(actorSelection, message, DEFAULT_ASK_TIMEOUT);
    }

    /**
     * Convenience method for sending a message to an actor selection
     * and returning the result.
     * @param actorSelection the actor selection
     * @param message the message
     * @param timeout the timeout for the result
     * @return the result
     * @throws Exception if an error occurs or if the result times out
     */
    public static Object ask(ActorSelection actorSelection, Object message,
            Timeout timeout) throws Exception {
        return Await.result(Patterns.ask(actorSelection, message, timeout),
                timeout.duration());
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
     * @return certificate matching certHash
     */
    public static X509Certificate getCertForCertHash(String certHash)
            throws Exception {
        X509Certificate cert =
                TokenManager.getCertificateForCertHash(certHash);
        if (cert != null) {
            return cert;
        }

        // not in key conf, look elsewhere
        for (X509Certificate caCert : GlobalConf.getAllCaCerts()) {
            if (certHash.equals(calculateCertHexHash(caCert))) {
                return caCert;
            }
        }
        return null;
    }
}
