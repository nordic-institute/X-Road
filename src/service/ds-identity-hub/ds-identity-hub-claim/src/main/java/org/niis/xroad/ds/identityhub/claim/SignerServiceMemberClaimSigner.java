/*
 * The MIT License
 *
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
package org.niis.xroad.ds.identityhub.claim;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import com.nimbusds.jwt.JWTClaimsSet;
import org.eclipse.edc.spi.result.Result;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Production {@link MemberClaimSigner} that signs the X-Road MemberId claim JWS using the
 * member's existing X-Road sign key via the local signer service over gRPC.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link SignerRpcClient#getMemberSigningInfo(ClientId)} → {@code (keyId, cert, signMechanism)}.</li>
 *   <li>Compose JWT registered claims ({@code sub/iss = holderDid}, {@code aud = audience},
 *       {@code iat = now}, {@code exp = iat + lifetime}, {@code jti = random UUID}).</li>
 *   <li>{@link JwsBuilder} computes the JWS signing-input digest and asks
 *       {@link SignerSignClient#sign(String, SignAlgorithm, byte[])} for the signature.</li>
 *   <li>Returns the compact JWS string for embedding in the outer DCP JWT.</li>
 * </ol>
 *
 * <p>The JWS carries the pinned OCSP response for the signing cert in the
 * {@code ocsp} JWS header — fetched from the signer service's periodically-refreshed
 * OCSP cache. Cold-start path: if the signer's cache is empty for this cert (first
 * fetch hasn't completed), the credential request fails with a clear error and CRM
 * retries on its own.
 *
 * <p>Supports the RSA family ({@code SHA*WithRSA}). ECDSA/PSS/EdDSA require
 * DER↔R||S signature transcoding and are deferred until needed.
 */
public class SignerServiceMemberClaimSigner implements MemberClaimSigner {

    private final SignerRpcClient signerRpcClient;
    private final SignerSignClient signerSignClient;
    private final MemberClaimSignerProperties properties;
    private final Clock clock;
    private final OcspFetcher ocspFetcher;

    public SignerServiceMemberClaimSigner(SignerRpcClient signerRpcClient,
                                          SignerSignClient signerSignClient,
                                          MemberClaimSignerProperties properties,
                                          Clock clock) {
        this.signerRpcClient = signerRpcClient;
        this.signerSignClient = signerSignClient;
        this.properties = properties;
        this.clock = clock;
        this.ocspFetcher = new OcspFetcher(signerRpcClient);
    }

    @Override
    public Result<String> sign(ClientId memberClientId, String holderDid, String audience) {
        if (memberClientId == null || holderDid == null) {
            return Result.failure("memberClientId and holderDid must be supplied");
        }
        SignerRpcClient.MemberSigningInfoDto info;
        try {
            info = signerRpcClient.getMemberSigningInfo(memberClientId);
        } catch (Exception e) {
            return Result.failure("signer-service: cannot resolve sign key for '"
                    + memberClientId.asEncodedId() + "': " + e.getMessage());
        }
        if (info == null) {
            return Result.failure("signer-service: no signing info for '" + memberClientId.asEncodedId() + "'");
        }

        X509Certificate cert;
        try {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(info.cert().getCertificateBytes()));
        } catch (Exception e) {
            return Result.failure("signer-service: cannot parse signing cert: " + e.getMessage());
        }

        Result<byte[]> ocspResult = ocspFetcher.fetch(cert);
        if (ocspResult.failed()) {
            return Result.failure(ocspResult.getFailureDetail());
        }

        SignAlgorithm signAlgo = SignAlgorithm.ofDigestAndMechanism(DigestAlgorithm.SHA256, info.signMechanismName());
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.lifetime())))
                .jwtID(UUID.randomUUID().toString())
                .build();

        try {
            String compactJws = JwsBuilder.build(claims, cert, signAlgo, info.keyId(),
                    (keyId, digest) -> callSigner(keyId, signAlgo, digest), ocspResult.getContent());
            return Result.success(compactJws);
        } catch (JwsBuilder.JwsBuildException e) {
            return Result.failure("signer-service: " + e.getMessage());
        } catch (Exception e) {
            return Result.failure("signer-service: sign failed: " + e.getMessage());
        }
    }

    private byte[] callSigner(String keyId, SignAlgorithm signAlgo, byte[] digest) {
        try {
            return signerSignClient.sign(keyId, signAlgo, digest);
        } catch (Exception e) {
            throw new JwsBuilder.JwsBuildException("signer.sign failed: " + e.getMessage(), e);
        }
    }
}
