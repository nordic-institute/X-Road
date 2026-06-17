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
package org.niis.xroad.signer.core.protocol.handler;

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import com.google.protobuf.ByteString;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.common.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.util.TokenAndKey;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertReq;
import org.niis.xroad.signer.proto.GenerateSelfSignedCertResp;
import org.niis.xroad.signer.proto.SignReq;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotAvailable;

/**
 * Handles generation of self signed certificates.
 */
@Slf4j
@SuppressWarnings("deprecation")
@ApplicationScoped
@RequiredArgsConstructor
public class GenerateSelfSignedCertReqHandler extends AbstractRpcHandler<GenerateSelfSignedCertReq, GenerateSelfSignedCertResp> {
    private final SignerProperties signerProperties;
    private final SignReqHandler signReqHandler;
    private final ImportCertReqHandler importCertReqHandler;
    private final TokenLookup tokenLookup;
    private final KeyManagers keyManagers;

    @Override
    protected GenerateSelfSignedCertResp handle(GenerateSelfSignedCertReq request) {
        TokenAndKey tokenAndKey = tokenLookup.findTokenAndKey(request.getKeyId());

        if (!tokenLookup.isKeyAvailable(tokenAndKey.getKeyId())) {
            throw keyNotAvailable(tokenAndKey.getKeyId());
        }

        if (tokenAndKey.key().getPublicKey() == null) {
            throw XrdRuntimeException.systemInternalError("Key '%s' has no public key".formatted(request.getKeyId()));
        }

        try {
            PublicKey pk = keyManagers.getFor(tokenAndKey.getSignMechanism()).readX509PublicKey(tokenAndKey.key().getPublicKey());

            SignAlgorithm signAlgoId = SignAlgorithm.ofDigestAndMechanism(
                    DigestAlgorithm.ofName(signerProperties.selfsignedCertDigestAlgorithm()),
                    tokenAndKey.getSignMechanism()
            );

            X509Certificate cert = new DummyCertBuilder().build(tokenAndKey, request, pk, signAlgoId);

            ClientId.Conf memberId = request.hasMemberId() ? ClientIdMapper.fromDto(request.getMemberId()) : null;
            importCertReqHandler.importCertificate(cert,
                    CertificateInfo.STATUS_REGISTERED,
                    memberId,
                    !KeyUsageInfo.AUTHENTICATION.equals(request.getKeyUsage())
            );

            return GenerateSelfSignedCertResp.newBuilder()
                    .setCertificateBytes(ByteString.copyFrom(cert.getEncoded()))
                    .build();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    class DummyCertBuilder {

        X509Certificate build(TokenAndKey tokenAndKey, GenerateSelfSignedCertReq message, PublicKey publicKey,
                              SignAlgorithm signAlgoId) throws CertIOException, CertificateException {
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
        private final class CertContentSigner implements ContentSigner {

            private final ByteArrayOutputStream out = new ByteArrayOutputStream();

            private final TokenAndKey tokenAndKey;

            private final SignAlgorithm signAlgoId;

            @Override
            public AlgorithmIdentifier getAlgorithmIdentifier() {
                return new DefaultSignatureAlgorithmIdentifierFinder().find(signAlgoId.name());
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
                    digest = calculateDigest(signAlgoId.digest(), dataToSign);

                    var message = SignReq.newBuilder()
                            .setKeyId(tokenAndKey.getKeyId())
                            .setSignatureAlgorithmId(signAlgoId.name())
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
