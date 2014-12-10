package ee.cyber.sdsb.signer.protocol.handler;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.message.GenerateSelfSignedCert;
import ee.cyber.sdsb.signer.protocol.message.GenerateSelfSignedCertResponse;
import ee.cyber.sdsb.signer.protocol.message.ImportCert;
import ee.cyber.sdsb.signer.protocol.message.ImportCertResponse;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.protocol.message.SignResponse;
import ee.cyber.sdsb.signer.tokenmanager.ServiceLocator;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;
import ee.cyber.sdsb.signer.util.SignerUtil;
import ee.cyber.sdsb.signer.util.TokenAndKey;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.translateException;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles generation of self signed certificates.
 */
@Slf4j
@SuppressWarnings("deprecation")
public class GenerateSelfSignedCertRequestHandler
        extends AbstractRequestHandler<GenerateSelfSignedCert> {

    private static final String SIGNATURE_ALGORITHM = SHA512WITHRSA_ID;

    @Override
    protected Object handle(GenerateSelfSignedCert message) throws Exception {
        TokenAndKey tokenAndKey =
                TokenManager.findTokenAndKey(message.getKeyId());
        if (!TokenManager.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw keyNotAvailable(tokenAndKey.getKeyId());
        }

        if (tokenAndKey.getKey().getPublicKey() == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Key '%s' has no public key", message.getKeyId());
        }

        PublicKey pk = readX509PublicKey(decodeBase64(
                tokenAndKey.getKey().getPublicKey()));

        X509Certificate cert =
                new DummyCertBuilder().build(tokenAndKey, message, pk);

        byte[] certData = cert.getEncoded();

        importCert(new ImportCert(certData, CertificateInfo.STATUS_REGISTERED,
                message.getMemberId()));

        return new GenerateSelfSignedCertResponse(certData);
    }

    private void importCert(ImportCert importCert) throws Exception {
        Object response = SignerUtil.ask(
                ServiceLocator.getRequestProcessor(getContext()), importCert);
        if (!(response instanceof ImportCertResponse)) {
            if (response instanceof Exception) {
                throw (Exception) response;
            }

            log.error("Received unexpected response: " + response.getClass());
            throw new CodedException(X_INTERNAL_ERROR,
                    "Failed to import certificate to key");
        }
    }

    class DummyCertBuilder {

        public X509Certificate build(TokenAndKey tokenAndKey,
                GenerateSelfSignedCert message, PublicKey publicKey)
                        throws Exception {
            X500Name subject = new X500Name("CN=" + message.getCommonName());

            JcaX509v3CertificateBuilder builder =
                    new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                            message.getNotBefore(), message.getNotAfter(),
                            subject, publicKey);

            KeyUsage keyUsage = new KeyUsage(
                    message.getKeyUsage() == KeyUsageInfo.SIGNING
                        ? KeyUsage.nonRepudiation : KeyUsage.digitalSignature);
            builder.addExtension(X509Extension.keyUsage, true, keyUsage);

            ContentSigner signer = new CertContentSigner(tokenAndKey);

            X509CertificateHolder holder = builder.build(signer);
            return new JcaX509CertificateConverter().getCertificate(holder);
        }

        @Data
        private class CertContentSigner implements ContentSigner {

            private final ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            private final TokenAndKey tokenAndKey;

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
                    String digAlgoId =
                            getDigestAlgorithmId(SIGNATURE_ALGORITHM);
                    digest = calculateDigest(digAlgoId, dataToSign);

                    Sign message = new Sign(tokenAndKey.getKeyId(),
                            SIGNATURE_ALGORITHM, digest);

                    Object response = SignerUtil.ask(
                            ServiceLocator.getTokenSigner(getContext(),
                                    tokenAndKey.getTokenId()), message);
                    if (response instanceof SignResponse) {
                        return ((SignResponse) response).getSignature();
                    } else {
                        throw new RuntimeException("Failed to sign with key "
                                + tokenAndKey.getKeyId()
                                + "; response was " + response);
                    }
                } catch (Exception e) {
                    throw translateException(e);
                }
            }

        }
    }

}
