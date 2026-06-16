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
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwsBuilderTest {

    private static KeyPair keyPair;
    private static X509Certificate cert;

    @BeforeAll
    static void setUpKeys() throws Exception {
        var kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        cert = SelfSignedCertGen.generate(keyPair.getPublic(), keyPair.getPrivate());
    }

    @Test
    void buildsVerifiableRs256JwsViaExternalSigner() throws Exception {
        SignAlgorithm signAlgo = SignAlgorithm.ofDigestAndMechanism(DigestAlgorithm.SHA256, SignMechanism.CKM_RSA_PKCS);
        JWTClaimsSet claims = sampleClaims();

        // Simulated signer: locally apply RSA-SHA256 to the SHA-256 digest.
        // SignerSignClient on the wire takes the digest; here we emulate by re-applying
        // SHA-256 + RSA via the JCA SHA256withRSA which expects the un-hashed input.
        // To match what signer-service does (it wraps the digest in PKCS#1 v1.5 ASN.1
        // and applies raw RSA), we sign the SHA-256 digest using the JCA "NONEwithRSA"
        // with a manually-prepended SHA-256 digest-info prefix. For unit-test simplicity
        // we just sign the un-hashed signing input bytes using JCA SHA256withRSA, which
        // produces the same final signature value.
        var capturedDigest = new byte[1][];
        byte[] ocspBytes = "dummy-ocsp-der".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String jws = JwsBuilder.build(claims, cert, signAlgo, "test-key", (keyId, digest) -> {
            capturedDigest[0] = digest;
            return signDigestRsaPkcs1v15Sha256(digest, keyPair);
        }, ocspBytes);

        assertNotNull(jws);
        assertEquals(2, jws.chars().filter(c -> c == '.').count());

        // Sanity: digest passed to signer is SHA-256 of the JWS signing input.
        SignedJWT parsed = SignedJWT.parse(jws);
        byte[] signingInput = parsed.getSigningInput();
        byte[] expectedDigest = MessageDigest.getInstance("SHA-256").digest(signingInput);
        org.junit.jupiter.api.Assertions.assertArrayEquals(expectedDigest, capturedDigest[0]);

        // Signature verifies against the cert's public key.
        JWSVerifier verifier = new RSASSAVerifier((java.security.interfaces.RSAPublicKey) cert.getPublicKey());
        assertTrue(parsed.verify(verifier));

        // Header carries the cert in x5c plus the OCSP custom header.
        assertEquals(JWSAlgorithm.RS256, parsed.getHeader().getAlgorithm());
        var chain = parsed.getHeader().getX509CertChain();
        assertNotNull(chain);
        assertEquals(1, chain.size());
        Object pinnedOcsp = parsed.getHeader().getCustomParam(JwsBuilder.OCSP_HEADER);
        assertNotNull(pinnedOcsp);
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                ocspBytes, java.util.Base64.getDecoder().decode(pinnedOcsp.toString()));

        // Payload preserves the standard claims.
        JWTClaimsSet decoded = parsed.getJWTClaimsSet();
        assertEquals("did:web:holder", decoded.getSubject());
        assertEquals("did:web:holder", decoded.getIssuer());
        assertEquals(List.of("did:web:issuer"), decoded.getAudience());
        assertNotNull(decoded.getJWTID());
    }

    @Test
    void omitsOcspHeaderWhenBytesNull() throws Exception {
        SignAlgorithm signAlgo = SignAlgorithm.ofDigestAndMechanism(DigestAlgorithm.SHA256, SignMechanism.CKM_RSA_PKCS);
        String jws = JwsBuilder.build(sampleClaims(), cert, signAlgo, "test-key",
                (k, d) -> signDigestRsaPkcs1v15Sha256(d, keyPair),
                null);

        SignedJWT parsed = SignedJWT.parse(jws);
        org.junit.jupiter.api.Assertions.assertNull(parsed.getHeader().getCustomParam(JwsBuilder.OCSP_HEADER));
    }

    @Test
    void rejectsEcdsaForNow() throws Exception {
        SignAlgorithm ecdsa = SignAlgorithm.ofDigestAndMechanism(DigestAlgorithm.SHA256, SignMechanism.CKM_ECDSA);
        JWTClaimsSet claims = sampleClaims();

        assertThrows(JwsBuilder.JwsBuildException.class,
                () -> JwsBuilder.build(claims, cert, ecdsa, "test-key", (k, d) -> new byte[0], null));
    }

    @Test
    void rejectsNullSignatureFromSigner() {
        SignAlgorithm signAlgo = SignAlgorithm.ofDigestAndMechanism(DigestAlgorithm.SHA256, SignMechanism.CKM_RSA_PKCS);

        assertThrows(JwsBuilder.JwsBuildException.class,
                () -> JwsBuilder.build(sampleClaims(), cert, signAlgo, "test-key", (k, d) -> null, null));
    }

    private static JWTClaimsSet sampleClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("did:web:holder")
                .subject("did:web:holder")
                .audience("did:web:issuer")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Emulate what X-Road's signer-service does when handed a SHA-256 digest and asked
     * for {@code SHA256withRSA}: wrap the digest with the PKCS#1 v1.5 ASN.1 DigestInfo
     * prefix for SHA-256, then apply raw RSA via {@code NONEwithRSA}. The output matches
     * what JCA's {@code SHA256withRSA} would have produced for the original signing-input
     * bytes — so the JWS we assemble verifies against the cert's public key.
     */
    private static byte[] signDigestRsaPkcs1v15Sha256(byte[] digest, KeyPair signingKeyPair) {
        try {
            byte[] sha256Prefix = new byte[]{
                    0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
                    0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20};
            byte[] toSign = new byte[sha256Prefix.length + digest.length];
            System.arraycopy(sha256Prefix, 0, toSign, 0, sha256Prefix.length);
            System.arraycopy(digest, 0, toSign, sha256Prefix.length, digest.length);
            Signature signature = Signature.getInstance("NONEwithRSA");
            signature.initSign(signingKeyPair.getPrivate());
            signature.update(toSign);
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class SelfSignedCertGen {
        static X509Certificate generate(java.security.PublicKey publicKey, java.security.PrivateKey privateKey) throws Exception {
            var name = new org.bouncycastle.asn1.x500.X500Name("CN=Test, O=NIIS, C=EE");
            var serial = BigInteger.valueOf(System.currentTimeMillis());
            var notBefore = new Date(System.currentTimeMillis() - 60_000);
            var notAfter = new Date(System.currentTimeMillis() + 3_600_000L);
            var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                    name, serial, notBefore, notAfter, name, publicKey);
            var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
            var holder = builder.build(signer);
            return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder);
        }
    }
}
