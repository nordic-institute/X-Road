package ee.ria.xroad.signer.console;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import lombok.Data;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.*;

final class DummyCertBuilder {

    private DummyCertBuilder() {
    }

    static X509Certificate build(String keyId, String commonName,
            PublicKey publicKey) throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        Date notBefore = cal.getTime();
        cal.add(Calendar.YEAR, 2);
        Date notAfter = cal.getTime();

        X500Name subject = new X500Name("CN=" + commonName);

        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        subject, BigInteger.ONE, notBefore, notAfter,
                        subject, publicKey);

        ContentSigner signer = new CertContentSigner(keyId);

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    @Data
    private static class CertContentSigner implements ContentSigner {

        private static final String SIGNATURE_ALGORITHM = SHA1WITHRSA_ID;

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private final String keyId;

        @Override
        public AlgorithmIdentifier getAlgorithmIdentifier() {
            return new DefaultSignatureAlgorithmIdentifierFinder().find(
                    SIGNATURE_ALGORITHM);
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public byte[] getSignature() {
            byte[] dataToSign = out.toByteArray();
            byte[] digest;
            try {
                String digAlgoId = getDigestAlgorithmId(SIGNATURE_ALGORITHM);
                digest = calculateDigest(digAlgoId, dataToSign);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, e);
            }

            try {
                SignResponse response =
                        SignerClient.execute(
                                new Sign(keyId, SIGNATURE_ALGORITHM, digest));
                return response.getSignature();
            } catch (Exception e) {
                throw translateException(e);
            }
        }

    }
}
