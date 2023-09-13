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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;
import ee.ria.xroad.signer.tokenmanager.token.TokenWorkerProvider;
import ee.ria.xroad.signer.util.TokenAndKey;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.proto.SignReq;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readX509PublicKey;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotFound;

/**
 * Abstract base class for GenerateCertRequestRequestHandler and RegenerateCertRequestRequestHandler.
 *
 * @param <ReqT>  the type of generate cert request message this handler handles
 * @param <RespT> response type
 */
@Slf4j
@SuppressWarnings("squid:S119")
public abstract class AbstractGenerateCertReq<ReqT extends AbstractMessage,
        RespT extends AbstractMessage> extends AbstractRpcHandler<ReqT, RespT> {

    PKCS10CertificationRequest buildSignedCertRequest(TokenAndKey tokenAndKey, String subjectName)
            throws Exception {

        if (tokenAndKey.getKey().getPublicKey() == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Key '%s' has no public key", tokenAndKey.getKeyId());
        }

        PublicKey publicKey = readPublicKey(tokenAndKey.getKey().getPublicKey());

        JcaPKCS10CertificationRequestBuilder certRequestBuilder = new JcaPKCS10CertificationRequestBuilder(
                new X500Name(subjectName), publicKey);

        ContentSigner signer = new TokenContentSigner(tokenWorkerProvider, tokenAndKey);

        return certRequestBuilder.build(signer);
    }

    private static PublicKey readPublicKey(String publicKeyBase64) throws Exception {
        return readX509PublicKey(decodeBase64(publicKeyBase64));
    }

    static byte[] convert(PKCS10CertificationRequest request, CertificateRequestFormat format)
            throws Exception {
        if (CertificateRequestFormat.PEM == format) {
            return toPem(request);
        } else {
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
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private final TokenWorkerProvider tokenWorkerProvider;
        private final TokenAndKey tokenAndKey;

        private final String digestAlgoId;
        private final String signAlgoId;

        TokenContentSigner(final TokenWorkerProvider tokenWorkerProvider, final TokenAndKey tokenAndKey) throws NoSuchAlgorithmException {
            this.tokenAndKey = tokenAndKey;
            this.tokenWorkerProvider = tokenWorkerProvider;
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

            try {
                SignReq request = SignReq.newBuilder()
                        .setKeyId(tokenAndKey.getKeyId())
                        .setSignatureAlgorithmId(signAlgoId)
                        .setDigest(ByteString.copyFrom(calculateDigest(digestAlgoId, out.toByteArray())))
                        .build();


                return tokenWorkerProvider.getTokenWorker(tokenAndKey.getTokenId())
                        .orElseThrow(() -> tokenNotFound(tokenAndKey.getTokenId()))
                        .handleSign(request);
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }


}
