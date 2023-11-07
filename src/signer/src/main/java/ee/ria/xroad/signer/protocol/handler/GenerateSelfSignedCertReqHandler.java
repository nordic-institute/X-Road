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
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.mapper.ClientIdMapper;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.TokenAndKey;

import com.google.protobuf.ByteString;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertReq;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertResp;
import org.niis.xroad.signer.proto.SignReq;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.SHA512_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.readX509PublicKey;
import static ee.ria.xroad.signer.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles generation of self signed certificates.
 */
@Slf4j
@SuppressWarnings("deprecation")
@Component
@RequiredArgsConstructor
public class GenerateSelfSignedCertReqHandler extends AbstractRpcHandler<GenerateSelfSignedCertReq, GenerateSelfSignedCertResp> {
    private final SignReqHandler signReqHandler;
    private final ImportCertReqHandler importCertReqHandler;

    // TODO make configurable
    private static final String SIGNATURE_DIGEST_ALGORITHM = SHA512_ID;

    @Override
    protected GenerateSelfSignedCertResp handle(GenerateSelfSignedCertReq request) throws Exception {
        TokenAndKey tokenAndKey = TokenManager.findTokenAndKey(request.getKeyId());

        if (!TokenManager.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw keyNotAvailable(tokenAndKey.getKeyId());
        }

        if (tokenAndKey.getKey().getPublicKey() == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Key '%s' has no public key", request.getKeyId());
        }

        PublicKey pk = readX509PublicKey(decodeBase64(tokenAndKey.getKey().getPublicKey()));

        String signAlgoId = CryptoUtils.getSignatureAlgorithmId(SIGNATURE_DIGEST_ALGORITHM,
                tokenAndKey.getSignMechanism());

        X509Certificate cert = new DummyCertBuilder().build(tokenAndKey, request, pk, signAlgoId);

        importCertReqHandler.importCertificate(cert,
                CertificateInfo.STATUS_REGISTERED,
                request.hasMemberId() ? ClientIdMapper.fromDto(request.getMemberId()) : null
        );

        return GenerateSelfSignedCertResp.newBuilder()
                .setCertificateBytes(ByteString.copyFrom(cert.getEncoded()))
                .build();
    }

    class DummyCertBuilder {

        X509Certificate build(TokenAndKey tokenAndKey, GenerateSelfSignedCertReq message, PublicKey publicKey,
                              String signAlgoId) throws Exception {
            X500Name subject = new X500Name("CN=" + message.getCommonName());

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                    fromUnixTimestamp(message.getDateNotBefore()),
                    fromUnixTimestamp(message.getDateNotAfter()), subject, publicKey);

            if (message.getKeyUsage() == KeyUsageInfo.SIGNING) {
                KeyUsage keyUsage = new KeyUsage(KeyUsage.nonRepudiation | KeyUsage.keyCertSign);
                builder.addExtension(X509Extension.keyUsage, true, keyUsage);
                builder.addExtension(X509Extension.basicConstraints,
                        true, new BasicConstraints(true));
            } else {
                KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature);
                builder.addExtension(X509Extension.keyUsage, true, keyUsage);
            }

            ContentSigner signer = new CertContentSigner(tokenAndKey, signAlgoId);

            X509CertificateHolder holder = builder.build(signer);

            return new JcaX509CertificateConverter().getCertificate(holder);
        }

        private Date fromUnixTimestamp(long unixDate) {
            return Date.from(Instant.ofEpochMilli(unixDate));
        }

        @Data
        private class CertContentSigner implements ContentSigner {

            private final ByteArrayOutputStream out = new ByteArrayOutputStream();

            private final TokenAndKey tokenAndKey;

            private final String signAlgoId;

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
                byte[] dataToSign = out.toByteArray();
                byte[] digest;

                try {
                    String digAlgoId = getDigestAlgorithmId(signAlgoId);
                    digest = calculateDigest(digAlgoId, dataToSign);

                    var message = SignReq.newBuilder()
                            .setKeyId(tokenAndKey.getKeyId())
                            .setSignatureAlgorithmId(signAlgoId)
                            .setDigest(ByteString.copyFrom(digest))
                            .build();
                    return signReqHandler.signData(message);

                } catch (Exception e) {
                    throw translateException(e);
                }
            }

        }
    }

}
