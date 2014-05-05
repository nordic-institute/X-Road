package ee.cyber.sdsb.signer.protocol.handler;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.PublicKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.GenerateCertRequest;
import ee.cyber.sdsb.signer.protocol.message.GenerateCertRequestResponse;
import ee.cyber.sdsb.signer.util.CalculateSignature;
import ee.cyber.sdsb.signer.util.CalculatedSignature;
import ee.cyber.sdsb.signer.util.SignerUtil;
import ee.cyber.sdsb.signer.util.TokenAndKey;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_KEY_NOT_AVAILABLE;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

public class GenerateCertRequestRequestHandler
        extends AbstractRequestHandler<GenerateCertRequest> {

    @Override
    protected Object handle(GenerateCertRequest message) throws Exception {
        TokenAndKey tokenAndKey =
                TokenManager.findTokenAndKey(message.getKeyId());
        if (!TokenManager.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw new CodedException(X_KEY_NOT_AVAILABLE,
                    "Key '%s' is not available", tokenAndKey.getKeyId());
        }

        PublicKey publicKey =
                readPublicKey(tokenAndKey.getKey().getPublicKey());

        JcaPKCS10CertificationRequestBuilder certRequestBuilder =
                new JcaPKCS10CertificationRequestBuilder(
                        new X500Name(message.getSubjectName()), publicKey);

        ContentSigner signer = new TokenContentSigner(tokenAndKey);

        PKCS10CertificationRequest generatedRequest =
                certRequestBuilder.build(signer);

        String certReqId = TokenManager.addCertRequest(tokenAndKey.getKeyId(),
                message.getMemberId(), message.getSubjectName(),
                message.getKeyUsage());

        return new GenerateCertRequestResponse(certReqId,
                generatedRequest.getEncoded());
    }

    private static PublicKey readPublicKey(String publicKeyBase64)
            throws Exception {
        byte[] publicKeyBytes = decodeBase64(publicKeyBase64);
        return readX509PublicKey(publicKeyBytes);
    }

    private class TokenContentSigner implements ContentSigner {

        private static final String SIGNATURE_ALGORITHM = SHA1WITHRSA_ID;

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private final TokenAndKey tokenAndKey;

        private final CountDownLatch latch = new CountDownLatch(1);

        private volatile CalculatedSignature signature;

        TokenContentSigner(TokenAndKey tokenAndKey) {
            this.tokenAndKey = tokenAndKey;
        }

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
            LOG.debug("Calculating signature for certificate request...");

            byte[] tbsData = null;
            try {
                String digAlgoId = getDigestAlgorithmId(SIGNATURE_ALGORITHM);
                byte[] digest = calculateDigest(digAlgoId, out.toByteArray());
                tbsData = SignerUtil.createDataToSign(digest);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, e);
            }

            ActorRef signatureReceiver = getContext().actorOf(
                    Props.create(SignatureReceiverActor.class, this));
            try {
                tellTokenWorker(new CalculateSignature(getSelf(),
                        tokenAndKey.getKeyId(), tbsData),
                        tokenAndKey.getTokenId(), signatureReceiver);

                waitForSignature();

                if (signature.getException() != null) {
                    throw new CodedException(X_INTERNAL_ERROR,
                            signature.getException());
                }

                return signature.getSignature();
            } finally {
                getContext().stop(signatureReceiver);
            }
        }

        private void waitForSignature() {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Signature calculation timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void setSignature(CalculatedSignature signature) {
            this.signature = signature;
            latch.countDown();
        }
    }

    static class SignatureReceiverActor extends UntypedActor {

        private final TokenContentSigner signer;

        SignatureReceiverActor(TokenContentSigner signer) {
            this.signer = signer;
        }

        @Override
        public void onReceive(Object message) throws Exception {
            if (message instanceof CalculatedSignature) {
                signer.setSignature((CalculatedSignature) message);
            } else {
                unhandled(message);
            }
        }
    }
}
