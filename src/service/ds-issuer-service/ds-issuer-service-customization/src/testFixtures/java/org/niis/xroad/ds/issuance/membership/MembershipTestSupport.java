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

package org.niis.xroad.ds.issuance.membership;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Shared RSA key, self-signed certificate, and compact-JWS builder for membership-claim tests.
 */
@UtilityClass
public class MembershipTestSupport {

    private static final int CLAIM_LIFETIME_SECONDS = 300;
    private static final int RSA_KEY_SIZE = 2048;
    private static final long SELF_SIGNED_NOT_BEFORE_OFFSET_MILLIS = 60_000L;
    private static final long SELF_SIGNED_NOT_AFTER_OFFSET_MILLIS = 3_600_000L;

    public static String signMemberClaim(TestKeyAndCert keyAndCert, String holderDid, String issuerDid, boolean includeCert)
            throws Exception {
        return signMemberClaim(keyAndCert.cert(), keyAndCert.privateKey(), holderDid, issuerDid, includeCert);
    }

    public static String signMemberClaim(X509Certificate cert, PrivateKey key, String holderDid, String issuerDid, boolean includeCert)
            throws Exception {
        return signMemberClaim(cert, key, holderDid, issuerDid, includeCert, null);
    }

    /**
     * Signs a member claim whose JWS header also carries the base64-encoded, DER-encoded pinned
     * OCSP response that {@code GlobalConfMemberIdClaimVerifier} reads from the {@code ocsp} header.
     */
    public static String signMemberClaimWithOcsp(X509Certificate cert, PrivateKey key, String holderDid, String issuerDid,
                                                  String pinnedOcspBase64) throws Exception {
        return signMemberClaim(cert, key, holderDid, issuerDid, true, pinnedOcspBase64);
    }

    private static String signMemberClaim(X509Certificate cert, PrivateKey key, String holderDid, String issuerDid,
                                           boolean includeCert, String pinnedOcspBase64) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(issuerDid)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(CLAIM_LIFETIME_SECONDS)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256);
        if (includeCert) {
            headerBuilder.x509CertChain(List.of(Base64.encode(cert.getEncoded())));
        }
        if (pinnedOcspBase64 != null) {
            headerBuilder.customParam("ocsp", pinnedOcspBase64);
        }
        SignedJWT jwt = new SignedJWT(headerBuilder.build(), claims);
        JWSSigner signer = new RSASSASigner(key);
        jwt.sign(signer);
        return jwt.serialize();
    }

    public record TestKeyAndCert(PrivateKey privateKey, X509Certificate cert) {
        public static TestKeyAndCert generate() throws Exception {
            var kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(RSA_KEY_SIZE);
            var kp = kpg.generateKeyPair();
            X509Certificate cert = generateSelfSigned(kp.getPublic(), kp.getPrivate());
            return new TestKeyAndCert(kp.getPrivate(), cert);
        }
    }

    /**
     * Minimal self-signed cert generator using BouncyCastle's BC provider, which is already
     * on the classpath via globalconf-impl.
     */
    private static X509Certificate generateSelfSigned(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=Test, O=NIIS, C=EE");
        var serial = BigInteger.valueOf(System.currentTimeMillis());
        var notBefore = new Date(System.currentTimeMillis() - SELF_SIGNED_NOT_BEFORE_OFFSET_MILLIS);
        var notAfter = new Date(System.currentTimeMillis() + SELF_SIGNED_NOT_AFTER_OFFSET_MILLIS);
        var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                name, serial, notBefore, notAfter, name, publicKey);
        var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
        var holder = builder.build(signer);
        return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder);
    }
}
