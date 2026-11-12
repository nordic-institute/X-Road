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

import ee.ria.xroad.common.TestCertUtil;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Shared DID constants, a {@link TestCertUtil}-backed key/certificate pair, and a compact-JWS
 * builder for membership-claim tests.
 */
@UtilityClass
public class MembershipTestSupport {

    public static final String HOLDER_DID = "did:web:holder";
    public static final String ISSUER_DID = "did:web:issuer";

    private static final int CLAIM_LIFETIME_SECONDS = 300;

    public static String signMemberClaim(TestKeyAndCert keyAndCert, String holderDid, String issuerDid, boolean includeCert)
            throws Exception {
        return signMemberClaim(keyAndCert.cert(), keyAndCert.privateKey(), holderDid, issuerDid, includeCert, Instant.now(), null);
    }

    public static String signMemberClaim(TestKeyAndCert keyAndCert, String holderDid, String issuerDid, boolean includeCert,
                                          Instant issuedAt) throws Exception {
        return signMemberClaim(keyAndCert.cert(), keyAndCert.privateKey(), holderDid, issuerDid, includeCert, issuedAt, null);
    }

    /**
     * Signs a member claim whose JWS header also carries the base64-encoded, DER-encoded pinned
     * OCSP response that {@code GlobalConfMemberIdClaimVerifier} reads from the {@code ocsp} header.
     */
    public static String signMemberClaimWithOcsp(X509Certificate cert, PrivateKey key, String holderDid, String issuerDid,
                                                  String pinnedOcspBase64) throws Exception {
        return signMemberClaim(cert, key, holderDid, issuerDid, true, Instant.now(), pinnedOcspBase64);
    }

    private static String signMemberClaim(X509Certificate cert, PrivateKey key, String holderDid, String issuerDid,
                                           boolean includeCert, Instant issuedAt, String pinnedOcspBase64) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(issuerDid)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(CLAIM_LIFETIME_SECONDS)))
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
        public static TestKeyAndCert generate() {
            var consumer = TestCertUtil.getConsumer();
            return new TestKeyAndCert(consumer.key, consumer.certChain[0]);
        }
    }
}
