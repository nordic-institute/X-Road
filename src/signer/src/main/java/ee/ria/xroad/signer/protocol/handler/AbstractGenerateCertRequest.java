/**
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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.CertificateRequestFormat;
import ee.ria.xroad.signer.util.CalculateSignature;
import ee.ria.xroad.signer.util.CalculatedSignature;
import ee.ria.xroad.signer.util.TokenAndKey;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readX509PublicKey;

/**
 * Abstract base class for GenerateCertRequestRequestHandler and RegenerateCertRequestRequestHandler.
 * @param <T> the type of generate cert request message this handler handles
 */
@Slf4j
public abstract class AbstractGenerateCertRequest<T> extends AbstractRequestHandler<T> {

    PKCS10CertificationRequest buildSignedCertRequest(TokenAndKey tokenAndKey, String subjectName)
            throws Exception {

        if (tokenAndKey.getKey().getPublicKey() == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Key '%s' has no public key", tokenAndKey.getKeyId());
        }

        PublicKey publicKey = readPublicKey(tokenAndKey.getKey().getPublicKey());

        JcaPKCS10CertificationRequestBuilder certRequestBuilder = new JcaPKCS10CertificationRequestBuilder(
                new X500Name(subjectName), publicKey);

        ContentSigner signer = new TokenContentSigner(tokenAndKey, this);

        PKCS10CertificationRequest request = certRequestBuilder.build(signer);
        return request;
    }

    private static PublicKey readPublicKey(String publicKeyBase64) throws Exception {
        return readX509PublicKey(decodeBase64(publicKeyBase64));
    }

    static byte[] convert(PKCS10CertificationRequest request, CertificateRequestFormat format)
            throws Exception {
        switch (format) {
            case PEM:
                return toPem(request);
            default:
                return request.getEncoded(); // DER
        }
    }

    private static byte[] toPem(PKCS10CertificationRequest req) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PEMWriter pw = new PEMWriter(new OutputStreamWriter(out))) {
            pw.writeObject(req);
        }

        return out.toByteArray();
    }

    private static class TokenContentSigner implements ContentSigner {

        private static final int SIGNATURE_TIMEOUT_SECONDS = 10;

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private final TokenAndKey tokenAndKey;
        private final AbstractGenerateCertRequest abstractGenerateCertRequest;

        private final String digestAlgoId;
        private final String signAlgoId;

        private final CountDownLatch latch = new CountDownLatch(1);

        private volatile CalculatedSignature signature;

        TokenContentSigner(TokenAndKey tokenAndKey, AbstractGenerateCertRequest abstractGenerateCertRequest)
                throws NoSuchAlgorithmException {
            this.tokenAndKey = tokenAndKey;
            this.abstractGenerateCertRequest = abstractGenerateCertRequest;

            digestAlgoId = SystemProperties.getSignerCsrSignatureDigestAlgorithm();
            signAlgoId = CryptoUtils.getSignatureAlgorithmId(digestAlgoId, tokenAndKey.getSignMechanism());
        }

        @Override
        public AlgorithmIdentifier getAlgorithmIdentifier() {
            return new DefaultSignatureAlgorithmIdentifierFinder().find(signAlgoId);
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public byte[] getSignature() {
            log.debug("Calculating signature for certificate request...");

            byte[] digest;

            try {
                digest = calculateDigest(digestAlgoId, out.toByteArray());
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, e);
            }

            ActorRef signatureReceiver = abstractGenerateCertRequest.getContext().actorOf(
                    Props.create(SignatureReceiverActor.class, this));

            try {
                abstractGenerateCertRequest.tellToken(new CalculateSignature(abstractGenerateCertRequest.getSelf(),
                                tokenAndKey.getKeyId(), signAlgoId, digest),
                        tokenAndKey.getTokenId(), signatureReceiver);

                waitForSignature();

                if (signature.getException() != null) {
                    throw translateException(signature.getException());
                }

                return signature.getSignature();
            } finally {
                abstractGenerateCertRequest.getContext().stop(signatureReceiver);
            }
        }

        private void waitForSignature() {
            try {
                if (!latch.await(SIGNATURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new CodedException(X_INTERNAL_ERROR, "Signature calculation timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void setSignature(CalculatedSignature sig) {
            this.signature = sig;
            latch.countDown();
        }
    }

    private static class SignatureReceiverActor extends UntypedAbstractActor {

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
