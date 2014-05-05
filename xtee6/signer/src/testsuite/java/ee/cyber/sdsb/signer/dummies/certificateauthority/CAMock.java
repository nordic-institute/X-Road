package ee.cyber.sdsb.signer.dummies.certificateauthority;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.Vector;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;

import static ee.cyber.sdsb.common.util.CryptoUtils.SHA512WITHRSA_ID;


public class CAMock {

    private static final Logger LOG = LoggerFactory.getLogger(CAMock.class);

    static {
        // Added Bouncy Castle as a provider in java.security (wasn't
        // automatically)
        Security.addProvider(
                new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    // TODO: Rename method to something better
    @SuppressWarnings("deprecation")
    public static byte[] certRequest(byte[] inputByteCSR,
            KeyUsageInfo certUsage, int expiresInSeconds) throws Exception {
        LOG.debug("Started process to convert CSR to sertificate.");
        // Main process logic got from here:
        // http://stackoverflow.com/questions/7230330/sign-csr-using-bouncy-castle

        // Hard coded CA private key
        PrivateKey privKeyCA = privStrToPrivateKey("MIIEuwIBADANBgkqhkiG9w" +
                "0BAQEFAASCBKUwggShAgEAAoIBAQCudi4gMI02ohn8YfRMeCU0ehRPw8J" +
                "8fkcZ5Vp1pC7nEkWDaEt9sK8k0MYOA5wZeWyzy1nnXLzQQbAkUBTUhqOE" +
                "QsXJEOERSwF3sXO6UzEzAwXCW6g8HW7RdSw2Zi6eZ2UFzoERGStNCLyhF" +
                "c7umyyVuJTgsOp4h0/ThGHJI2dVHB9vd4F8ZHsaH8Rt0V4HzXiAKmgzqe" +
                "YrVdHZ/cl0JMnE/ITsSmkgM8EGaCnOrZPMcXerN3VC/ld7RdILoOeRNvO" +
                "nGsK9qUaoashg6xXm+04W37ZOXH5AchaG9n1sN0QZjnw7SNaPeU7lWQVi" +
                "yWhq5hry2sKbvDbQnpCrC0IiyQy/AgMBAAECggEBAKJqez6UdKKr/q0rN" +
                "BgMsfZMwKQRhvoHRYIiNzjWBKQyKmzPp5f36NXJVtitG6HLnRs08RmnCF" +
                "CJFsZ3lyzTu17iGue4ww8qWM9pcGfCE0d+RNpQIir067or72Eld7kYYMA" +
                "rI5UbbWhl/dWpS+Bure8ky9TyXxaeQf6Ue8SZJRXhcxSviyh6ugwfHP+y" +
                "wSzF4Vo9E7BAo2/TrX+7QkByBk1q1K5pe8Gwj9agt6NxHQCg9lL2RTlQt" +
                "PjjBTb94R5K4vzG9rFmFIX01PHy8iY709UnBlocah/D60Vor+NSxvPhkw" +
                "/2Oyw1u8ran9W1Lc935AUSSRYVD18TcwczGkDp4cECgYEA01yMTNdAaF4" +
                "wAbGFmT6Ia82j+nZeQKAA/eaBEwjHQuR+s7ZvN+7O3SH1TdVxbCO6vioc" +
                "92DUOGjH41B2yXawls9rjdTqPHrm5ZL1CNKYhNoyOlR2AW81T+OY4mYjw" +
                "Fdddl6p7hByF0zQFhlMlPKrLn2RMhslPRMAzdhVudBrr0cCgYEA006bYj" +
                "IU35lxAaDsSIF5OIkcHQWH2cXXt7Cm2QuQQIoNH5nteieXVdKfRqHwvpH" +
                "Ypg1ttDayAioKEQ/XbKZqfx2yg+hw3tv1Q3/34U2Xw2Ac1CK01zrQebru" +
                "Er2xuVZfHXP8h9iZLxnTUzcSXquDf36+XoChj1UK7e3Si5PjIskCfylH6" +
                "h8X63x26OlrwqwrinmDsIM6my4EEi2E17DgBgsKlbSD7TT/b2fBOif5iC" +
                "2WzOJXV1D5mHlr/Z5oGMtAYz0RRfBtpVIqru4nxbPzbdaEx0qdNG8TSVZ" +
                "RHYsaZ+EpLxfvZUEZ9S6X0gx+5HYy7zwlSjZ/FsbW71FE73lbO60CgYBp" +
                "8gCwVEHo9ksVsPSnSdtYgE5LMsmxY6PL4tNVAZo68bywuoG5/H3BNxBK3" +
                "skRE5kXcKYvdDj+IsvXUCUHwcylnWMzBekJpCktxcyn9zp/aAIsTpZzT3" +
                "bVgeb6GIgyT97yqoPULMJpPj6Ze4RHWSFeXFLgmQfoEJChSNuGxghdGQK" +
                "BgF3BwvkppVi0riS6dFIEugsqubyoeGVPq65FdPwtgcjhXDn1N3NGu13M" +
                "UBPzaSPUHzUUoM5WlA1/ISRt+4n2W9Vk22XuBKvEvk+l/JsKDOa+rGIz7" +
                "Em8B2ud78PHKctD94wdddTlGuCNf88OL1bqjZE8b8lQTy6k6DjAHM/9LrZl");


        // Hard coded CA certificate
        X509Certificate certCA = stringToCert("MIIDdTCCAl2gAwIBAgIJAOuBNCIU" +
                "m0gwMA0GCSqGSIb3DQEBBQUAMFExCzAJBgNVBAYTAkVFMRQwEgYDVQQKEw" +
                "tDeWJlcm5ldGljYTEQMA4GA1UECxMHUm9vdCBDQTEaMBgGCSqGSIb3DQEJ" +
                "ARYLYWFhQGJiYi5jY2MwHhcNMTIwOTE0MTE1NjM3WhcNMjIwOTEyMTE1Nj" +
                "M3WjBRMQswCQYDVQQGEwJFRTEUMBIGA1UEChMLQ3liZXJuZXRpY2ExEDAO" +
                "BgNVBAsTB1Jvb3QgQ0ExGjAYBgkqhkiG9w0BCQEWC2FhYUBiYmIuY2NjMI" +
                "IBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArnYuIDCNNqIZ/GH0" +
                "THglNHoUT8PCfH5HGeVadaQu5xJFg2hLfbCvJNDGDgOcGXlss8tZ51y80E" +
                "GwJFAU1IajhELFyRDhEUsBd7FzulMxMwMFwluoPB1u0XUsNmYunmdlBc6B" +
                "ERkrTQi8oRXO7psslbiU4LDqeIdP04RhySNnVRwfb3eBfGR7Gh/EbdFeB8" +
                "14gCpoM6nmK1XR2f3JdCTJxPyE7EppIDPBBmgpzq2TzHF3qzd1Qv5Xe0XS" +
                "C6DnkTbzpxrCvalGqGrIYOsV5vtOFt+2Tlx+QHIWhvZ9bDdEGY58O0jWj3" +
                "lO5VkFYsloauYa8trCm7w20J6QqwtCIskMvwIDAQABo1AwTjAMBgNVHRME" +
                "BTADAQH/MB0GA1UdDgQWBBRSFYADqiIAKTGIT44uTLwShz1YZzAfBgNVHS" +
                "MEGDAWgBRSFYADqiIAKTGIT44uTLwShz1YZzANBgkqhkiG9w0BAQUFAAOC" +
                "AQEAMn7YD7C3cjkQL0wm1v47KYda/Y05jR5zMwV648VHgPeNLRyZYWJrpH" +
                "dUQiAqKL3zhF8neOQO100fwUxSxLsuPNqkce02DwjMSMWi3bF9xX7MlrQn" +
                "Ab6aSJ47YaPyZSvXlkzRC3dcDjcBIRSGNxsftISSEJJeqGWQz6b9LkIfxT" +
                "jtcHbTnm/yGPWpmr2blkm7qRKK4eFwvooJ6KqBmm8/J086VpDOc9qRy/ar" +
                "3za6UdFEBDX2aHQD4OLgBvj0dLYCu3w32ltmVOgBoewIq5M1wBGp8dIs5J" +
                "rr4P9xYprRY1une3IWvviJNXoWm1enl1+N31r32YIc4vXZiA2L+cjlvQ==");

        // Get CA name (subject)
        X500Name subjectNameCA =
                new X500Name(certCA.getSubjectDN().getName());

        // Deserialize inputByteCSR and get key and subject name
        PKCS10CertificationRequest pk10Holder =
                new PKCS10CertificationRequest(inputByteCSR);

        X500Name csrSubject = pk10Holder.getSubject();
        SubjectPublicKeyInfo csrPubKeyInfo =
                pk10Holder.getSubjectPublicKeyInfo();

        //        Boolean ok = checkCsrValidity(pk10Holder, inputByteCSR);
        //        if (!ok) {
        //            throw new Exception("Csr signature and key do not match.");
        //        }

        // Determine algorithms used
        AlgorithmIdentifier sigAlgId =
                new DefaultSignatureAlgorithmIdentifierFinder().find(
                        SHA512WITHRSA_ID);
        AlgorithmIdentifier digAlgId =
                new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);

        // finally start signing csr certificate with CA priv key
        AsymmetricKeyParameter foo =
                PrivateKeyFactory.createKey(privKeyCA.getEncoded());

        // Generate some variables for readability.
        // Dates and serials
        BigInteger serialNr = new BigInteger(
                Integer.toString(Random.class.newInstance().nextInt(
                        (int) Math.pow(10., 20.))));
        Date startDate = new Date(System.currentTimeMillis());

        Date expDate = new Date(
                System.currentTimeMillis() + expiresInSeconds * 1000);

        // Create
        X509v3CertificateBuilder myCertificateGenerator =
                new X509v3CertificateBuilder(
                        subjectNameCA, serialNr, startDate, expDate, csrSubject,
                        csrPubKeyInfo);

        // Add common extensions
        ASN1Encodable authKeyExtForCert = toDERObject(certCA.getExtensionValue(
                X509Extension.subjectKeyIdentifier.getId()));

        myCertificateGenerator.addExtension(
                X509Extension.authorityKeyIdentifier, false,
                authKeyExtForCert);

        myCertificateGenerator.addExtension(
                X509Extension.subjectKeyIdentifier, false,
                new SubjectKeyIdentifier(csrPubKeyInfo));
        myCertificateGenerator.addExtension(X509Extension.basicConstraints,
                true, new BasicConstraints(false));

        if (certUsage == KeyUsageInfo.AUTHENTICATION) {
            // Add auth cert extensions:
            myCertificateGenerator.addExtension(X509Extensions.KeyUsage, true,
                    new KeyUsage(
                            X509KeyUsage.digitalSignature |
                            X509KeyUsage.keyAgreement     |
                            X509KeyUsage.keyEncipherment  |
                            X509KeyUsage.dataEncipherment));
            myCertificateGenerator.addExtension(X509Extensions.ExtendedKeyUsage,
                    false, new ExtendedKeyUsage(new Vector<KeyPurposeId>(
                            Arrays.asList(KeyPurposeId.id_kp_clientAuth,
                                    KeyPurposeId.id_kp_serverAuth))));
        } else if (certUsage == KeyUsageInfo.SIGNING) {
            // Add sign cert extensions:
            myCertificateGenerator.addExtension(X509Extensions.KeyUsage, true,
                    new X509KeyUsage(
                            X509KeyUsage.nonRepudiation));
        } else {
            throw new Exception("KeyUsage not " +
                    "'KeyUsageInfo.AUTHENTICATION' or 'KeyUsageInfo.SIGNING'.");
        }

        ContentSigner sigGen =
                new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(foo);

        X509CertificateHolder holder = myCertificateGenerator.build(sigGen);

        writeCertToFile(new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(holder));

        LOG.debug("Cert created for: {}", csrSubject);
        return holder.getEncoded();
    }

    private static boolean checkCsrValidity(
            PKCS10CertificationRequest holder, byte[] inputByteCSR)
                    throws IOException, NoSuchAlgorithmException,
                    InvalidKeySpecException, InvalidKeyException,
                    SignatureException, NoSuchProviderException {
        // TODO: csr validity check isn't working.
        // To get public key from SubjectPublicKeyInfo
        SubjectPublicKeyInfo csrPubKey = holder.getSubjectPublicKeyInfo();
        RSAKeyParameters rsa = (RSAKeyParameters) PublicKeyFactory.createKey(
                csrPubKey);
        RSAPublicKeySpec rsaSpec = new RSAPublicKeySpec(
                rsa.getModulus(), rsa.getExponent());
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pubKey = kf.generatePublic(rsaSpec);
        // Verify against signature instance - http://docs.oracle.com/javase/
        // tutorial/security/apisign/vstep4.html
        byte[] sigToVerify = holder.getSignature();
        Signature sig = Signature.getInstance("SHA512withRSA", "BC");
        sig.initVerify(pubKey);

        InputStream datais = new ByteArrayInputStream(inputByteCSR);
        try (BufferedInputStream bufin = new BufferedInputStream(datais)) {
            byte[] buffer = new byte[2048];
            int len;
            while (bufin.available() != 0) {
                len = bufin.read(buffer);
                sig.update(buffer, 0, len);
            }
        }
        return sig.verify(sigToVerify);
    }

    private static PrivateKey privStrToPrivateKey(String key64)
            throws GeneralSecurityException {
        byte[] encoded = Base64.decode(key64);

        // PKCS8 decode the encoded RSA private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        PrivateKey priv = fact.generatePrivate(keySpec);
        Arrays.fill(encoded, (byte) 0); // what does it do? just destroys
        // encoded array?
        return priv;
    }

    private static X509Certificate stringToCert(String certStr)
            throws Exception {
        InputStream is = new ByteArrayInputStream(Base64.decode(certStr));

        X509Certificate cert =
                (X509Certificate) CertificateFactory.getInstance(
                        "X.509", "BC").generateCertificate(is);
        is.close();
        return cert;
    }

    private static void writeCertToFile(X509Certificate cert)
            throws CertificateEncodingException, IOException {
        // TODO: Start using pem writer.
        try (FileOutputStream fOs = new FileOutputStream(
                new File("theCert.pem"))) {
            // TODO: 1) Make csr subject name into file title. 2) add suffixes,
            // not to overwrite
            fOs.write(cert.getEncoded());
        }
    }

    /**
     * From http://stackoverflow.com/questions/2409618/how-do-i-decode-a-der-
     * encoded-string-in-java
     */
    private static ASN1Primitive toDERObject(byte[] d) throws IOException {
        return new ASN1InputStream(new ByteArrayInputStream(d)).readObject();
    }
}

