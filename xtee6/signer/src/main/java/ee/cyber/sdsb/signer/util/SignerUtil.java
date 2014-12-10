package ee.cyber.sdsb.signer.util;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.xml.bind.DatatypeConverter;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;

import scala.concurrent.Await;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;

import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

/**
 * Collection of various utility methods.
 */
public final class SignerUtil {

    private static final int RANDOM_ID_LENGTH = 20;

    private static final Timeout DEFAULT_ASK_TIMEOUT = new Timeout(5000);

    /**
     * DigestInfo ::= SEQUENCE {
     *      digestAlgorithm AlgorithmIdentifier,
     *      digest OCTET STRING
     * }
     */
    private static final Map<Integer, byte[]> DIGEST_PREFIX_CACHE =
            new HashMap<>();

    static {
        DIGEST_PREFIX_CACHE.put(CryptoUtils.SHA1_DIGEST_LENGTH, new byte[] {
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b,
                0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14 });
        DIGEST_PREFIX_CACHE.put(CryptoUtils.SHA224_DIGEST_LENGTH, new byte[] {
                0x30, 0x2d, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x04, 0x05, 0x00, 0x04, 0x1c });
        DIGEST_PREFIX_CACHE.put(CryptoUtils.SHA256_DIGEST_LENGTH, new byte[] {
                0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20 });
        DIGEST_PREFIX_CACHE.put(CryptoUtils.SHA384_DIGEST_LENGTH, new byte[] {
                0x30, 0x41, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x02, 0x05, 0x00, 0x04, 0x30 });
        DIGEST_PREFIX_CACHE.put(CryptoUtils.SHA512_DIGEST_LENGTH, new byte[] {
                0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40 });
    }

    public static final Long KEY_SIZE = 2048L;

    private SignerUtil() {
    }

    public static byte[] getDigestInfoPrefix(byte[] digest) {
        if (DIGEST_PREFIX_CACHE.containsKey(digest.length)) {
            return DIGEST_PREFIX_CACHE.get(digest.length);
        }

        throw new RuntimeException("Invalid digest length: " + digest.length);
    }

    public static byte[] createDataToSign(byte[] digest) {
        byte[] prefix = getDigestInfoPrefix(digest);
        byte[] digestInfo = new byte[prefix.length + digest.length];

        System.arraycopy(prefix, 0, digestInfo, 0, prefix.length);
        System.arraycopy(digest, 0, digestInfo, prefix.length, digest.length);

        return digestInfo;
    }

    public static boolean hasKey(TokenInfo tokenInfo, String keyId) {
        for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
            if (keyInfo.getId().equals(keyId)) {
                return true;
            }
        }

        return false;
    }

    public static String keyId(iaik.pkcs.pkcs11.objects.Key k) {
        if (k.getId() == null || k.getId().getByteArrayValue() == null) {
            return null;
        }

        return DatatypeConverter.printHexBinary(k.getId().getByteArrayValue());
    }

    public static String keyId(
            iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate c) {
        if (c.getId() == null || c.getId().getByteArrayValue() == null) {
            return null;
        }

        return DatatypeConverter.printHexBinary(c.getId().getByteArrayValue());
    }

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

    public static String randomId() {
        return DatatypeConverter.printHexBinary(generateId());
    }

    public static byte[] generateId() {
        byte[] id = new byte[RANDOM_ID_LENGTH];
        new Random().nextBytes(id);
        return id;
    }

    public static Object ask(ActorRef actor, Object message) throws Exception {
        return ask(actor, message, DEFAULT_ASK_TIMEOUT);
    }

    public static Object ask(ActorRef actor, Object message, Timeout timeout)
            throws Exception {
        return Await.result(Patterns.ask(actor, message,
                timeout.duration().length()), timeout.duration());
    }

    public static Object ask(ActorSelection actorSelection, Object message)
            throws Exception {
        return ask(actorSelection, message, DEFAULT_ASK_TIMEOUT);
    }

    public static Object ask(ActorSelection actorSelection, Object message,
            Timeout timeout) throws Exception {
        return Await.result(Patterns.ask(actorSelection, message, timeout),
                timeout.duration());
    }

    public static String getWorkerId(TokenInfo tokenInfo) {
        String workerId = tokenInfo.getType();
        if (tokenInfo.getSerialNumber() != null
                && tokenInfo.getLabel() != null) {
            workerId += "-" + tokenInfo.getSerialNumber();
            workerId += "-" + tokenInfo.getLabel();
        }

        return workerId;
    }

}
