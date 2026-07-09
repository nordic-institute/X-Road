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

package org.niis.xroad.test.framework.core.token;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Generic RS256 JWT test-token signer. Loads a private RSA JWK from a classpath resource
 * and produces signed {@code at+jwt} tokens. The API is String-based so callers do not
 * need nimbus on their compile classpath.
 */
public final class TestJwtSigner {

    private static final String ISSUER = "test-issuer";
    private static final long TTL_SECONDS = 3600L;

    private final RSAKey signingKey;
    private final RSASSASigner signer;

    /**
     * Creates a signer that loads the private JWK from the given classpath resource path.
     *
     * @param privateKeyResource classpath path to the RSA JWK JSON, e.g. {@code /jwks/private_key.json}
     */
    public TestJwtSigner(String privateKeyResource) {
        this.signingKey = loadKey(privateKeyResource);
        this.signer = buildSigner(signingKey);
    }

    /**
     * Signs a token carrying the supplied claims. Standard claims ({@code iss}, {@code iat},
     * {@code exp}, {@code jti}) are always set; entries in {@code claims} are merged on top.
     *
     * @param subject optional subject; pass {@code null} to omit the {@code sub} claim
     * @param claims  additional claims to include (e.g. {@code role}, {@code scope})
     * @return serialised compact JWT string (no {@code Bearer } prefix)
     */
    public String sign(String subject, Map<String, Object> claims) {
        var now = Instant.now();
        var builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(TTL_SECONDS)))
                .jwtID(UUID.randomUUID().toString());

        if (subject != null) {
            builder.subject(subject);
        }
        claims.forEach(builder::claim);

        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("at+jwt"))
                .keyID(signingKey.getKeyID())
                .build();

        var jwt = new SignedJWT(header, builder.build());
        try {
            jwt.sign(signer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test token", e);
        }
        return jwt.serialize();
    }

    private static RSAKey loadKey(String resource) {
        try (InputStream in = TestJwtSigner.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Signing key resource not found: " + resource);
            }
            return RSAKey.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load signing key from " + resource, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse signing key from " + resource, e);
        }
    }

    private static RSASSASigner buildSigner(RSAKey key) {
        try {
            return new RSASSASigner(key);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create RSA signer", e);
        }
    }
}
