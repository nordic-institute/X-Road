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

import com.google.protobuf.ByteString;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.protocol.dto.CertificateInfoProto;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignerServiceMemberClaimSignerTest {

    private static KeyPair keyPair;
    private static X509Certificate cert;

    private SignerRpcClient signerRpcClient;
    private SignerSignClient signerSignClient;
    private SignerServiceMemberClaimSigner signer;

    @BeforeAll
    static void setUpKeys() throws Exception {
        var kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        cert = generateSelfSignedRsaCert(keyPair);
    }

    @BeforeEach
    void setUp() {
        signerRpcClient = mock(SignerRpcClient.class);
        signerSignClient = mock(SignerSignClient.class);
        MemberClaimSignerProperties properties = mock(MemberClaimSignerProperties.class);
        lenient().when(properties.lifetimeSeconds()).thenReturn(300L);
        lenient().when(properties.lifetime()).thenReturn(java.time.Duration.ofSeconds(300));
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        signer = new SignerServiceMemberClaimSigner(signerRpcClient, signerSignClient, properties, clock);
    }

    @Test
    void emits_jws_signed_by_signer_service_response_with_pinned_ocsp() throws Exception {
        ClientId memberId = ClientId.Conf.create("DEV", "COM", "SS0");
        stubMemberSigningInfo(memberId);
        byte[] ocspDer = "test-ocsp-der".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubOcspCacheHit(ocspDer);
        when(signerSignClient.sign(eq("key-1"), any(), any()))
                .thenAnswer(inv -> signDigestRsaPkcs1v15Sha256(inv.getArgument(2)));

        Result<String> result = signer.sign(memberId, "did:web:holder", "did:web:issuer");

        assertTrue(result.succeeded(), result.getFailureDetail());
        String jws = result.getContent();
        assertNotNull(jws);
        SignedJWT parsed = SignedJWT.parse(jws);
        JWSVerifier verifier = new RSASSAVerifier((java.security.interfaces.RSAPublicKey) cert.getPublicKey());
        assertTrue(parsed.verify(verifier));

        // OCSP pinned in header.
        Object pinned = parsed.getHeader().getCustomParam(JwsBuilder.OCSP_HEADER);
        assertNotNull(pinned);
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                ocspDer, java.util.Base64.getDecoder().decode(pinned.toString()));

        JWTClaimsSet claims = parsed.getJWTClaimsSet();
        assertEquals("did:web:holder", claims.getSubject());
        assertEquals("did:web:holder", claims.getIssuer());
        assertEquals(java.util.List.of("did:web:issuer"), claims.getAudience());
        assertNotNull(claims.getJWTID());
        // lifetime = 300s with fixed clock
        assertEquals(Date.from(Instant.parse("2026-01-01T00:05:00Z")), claims.getExpirationTime());
    }

    @Test
    void fails_when_signer_ocsp_cache_empty() {
        ClientId memberId = ClientId.Conf.create("DEV", "COM", "SS0");
        stubMemberSigningInfo(memberId);
        when(signerRpcClient.getOcspResponses(any(String[].class))).thenReturn(new String[]{null});

        Result<String> result = signer.sign(memberId, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertTrue(result.getFailureDetail().contains("no cached OCSP response"));
    }

    @Test
    void fails_when_signer_lookup_throws() {
        ClientId memberId = ClientId.Conf.create("DEV", "COM", "UNKNOWN");
        when(signerRpcClient.getMemberSigningInfo(memberId)).thenThrow(new RuntimeException("no key"));

        Result<String> result = signer.sign(memberId, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertTrue(result.getFailureDetail().contains("cannot resolve sign key"));
    }

    @Test
    void fails_when_signer_sign_throws() {
        ClientId memberId = ClientId.Conf.create("DEV", "COM", "SS0");
        stubMemberSigningInfo(memberId);
        stubOcspCacheHit("dummy".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(signerSignClient.sign(any(), any(), any())).thenThrow(new RuntimeException("hsm offline"));

        Result<String> result = signer.sign(memberId, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertTrue(result.getFailureDetail().contains("signer-service"));
    }

    @Test
    void rejects_null_arguments() {
        assertTrue(signer.sign(null, "did:web:holder", "did:web:issuer").failed());
        assertTrue(signer.sign(ClientId.Conf.create("DEV", "COM", "SS0"), null, "did:web:issuer").failed());
    }

    private void stubOcspCacheHit(byte[] ocspDer) {
        when(signerRpcClient.getOcspResponses(any(String[].class)))
                .thenReturn(new String[]{java.util.Base64.getEncoder().encodeToString(ocspDer)});
    }

    private void stubMemberSigningInfo(ClientId memberId) {
        try {
            CertificateInfo certInfo = new CertificateInfo(CertificateInfoProto.newBuilder()
                    .setCertificateBytes(ByteString.copyFrom(cert.getEncoded()))
                    .setStatus(CertificateInfo.STATUS_REGISTERED)
                    .setActive(true)
                    .build());
            when(signerRpcClient.getMemberSigningInfo(memberId))
                    .thenReturn(new SignerRpcClient.MemberSigningInfoDto("key-1", certInfo, SignMechanism.CKM_RSA_PKCS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Emulate what X-Road's signer-service does when handed a SHA-256 digest and asked
     * for {@code SHA256withRSA}: wrap the digest with the PKCS#1 v1.5 ASN.1 DigestInfo
     * prefix for SHA-256, then apply raw RSA via {@code NONEwithRSA}.
     */
    private static byte[] signDigestRsaPkcs1v15Sha256(byte[] digest) {
        try {
            byte[] sha256Prefix = new byte[]{
                    0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                    0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20};
            byte[] toSign = new byte[sha256Prefix.length + digest.length];
            System.arraycopy(sha256Prefix, 0, toSign, 0, sha256Prefix.length);
            System.arraycopy(digest, 0, toSign, sha256Prefix.length, digest.length);
            Signature signature = Signature.getInstance("NONEwithRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(toSign);
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static X509Certificate generateSelfSignedRsaCert(KeyPair kp) throws Exception {
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=Test, O=NIIS, C=EE");
        var serial = BigInteger.valueOf(System.currentTimeMillis());
        var notBefore = new Date(System.currentTimeMillis() - 60_000);
        var notAfter = new Date(System.currentTimeMillis() + 3_600_000L);
        var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                name, serial, notBefore, notAfter, name, kp.getPublic());
        var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
        var holder = builder.build(signer);
        return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder);
    }
}
