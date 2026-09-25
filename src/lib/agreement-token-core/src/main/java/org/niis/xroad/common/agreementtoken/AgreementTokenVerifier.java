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
package org.niis.xroad.common.agreementtoken;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_AGREEMENT_ID;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_CLIENT;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_SERVICE;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.decodeClient;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.decodeScope;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.decodeService;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.requireStringClaim;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.AUDIENCE_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.CLIENT_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.EXPIRED;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.INVALID_SIGNATURE;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.ISSUER_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.MALFORMED_TOKEN;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.SCOPE_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.SERVICE_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.UNKNOWN_KEY_ID;

/**
 * Verifies agreement tokens end to end: signature, expiry, issuer, audience, then the client/service/scope
 * claims against what the caller actually observed for the request. Every failure — malformed input included —
 * comes back as an {@link AgreementTokenVerificationResult.Rejected} value with a reason, never as a thrown
 * exception, so a caller can always fall back to the ACL with one {@code switch}.
 * <p>
 * The signature check accepts ES256 only: a token re-signed under any other algorithm but naming a known
 * key id is rejected outright instead of being checked against the wrong primitive.
 */
public final class AgreementTokenVerifier {

    private final AgreementTokenKeyProvider keyProvider;
    private final AgreementTokenProtocolProperties properties;
    private final Clock clock;

    public AgreementTokenVerifier(AgreementTokenKeyProvider keyProvider, AgreementTokenProtocolProperties properties) {
        this(keyProvider, properties, Clock.systemUTC());
    }

    public AgreementTokenVerifier(AgreementTokenKeyProvider keyProvider, AgreementTokenProtocolProperties properties, Clock clock) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AgreementTokenVerificationResult verify(String token, AgreementTokenRequestContext context) {
        Objects.requireNonNull(context, "context must not be null");
        if (token == null || token.isBlank()) {
            return rejected(MALFORMED_TOKEN, "token is blank");
        }

        SignedJWT signedJwt;
        try {
            signedJwt = SignedJWT.parse(token);
        } catch (ParseException | IllegalArgumentException e) {
            return rejected(MALFORMED_TOKEN, "token is not a well-formed JWS: " + e.getMessage());
        }

        var keyId = signedJwt.getHeader().getKeyID();
        if (keyId == null || keyId.isBlank()) {
            return rejected(MALFORMED_TOKEN, "token header carries no key id");
        }

        var signingKey = keyProvider.keyById(keyId);
        if (signingKey.isEmpty()) {
            return rejected(UNKNOWN_KEY_ID, "no signing key known for key id '" + keyId + "'");
        }

        var algorithm = signedJwt.getHeader().getAlgorithm();
        if (!JWSAlgorithm.ES256.equals(algorithm)) {
            return rejected(INVALID_SIGNATURE, "expected signature algorithm ES256, got '" + algorithm + "'");
        }

        boolean signatureValid;
        try {
            signatureValid = signedJwt.verify(new ECDSAVerifier(signingKey.get().publicKey()));
        } catch (JOSEException e) {
            return rejected(INVALID_SIGNATURE, "signature verification failed: " + e.getMessage());
        }
        if (!signatureValid) {
            return rejected(INVALID_SIGNATURE, "signature does not match");
        }

        JWTClaimsSet claimsSet;
        try {
            claimsSet = signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return rejected(MALFORMED_TOKEN, "token payload is not a valid claims set: " + e.getMessage());
        }

        var expirationTime = claimsSet.getExpirationTime();
        if (expirationTime == null) {
            return rejected(MALFORMED_TOKEN, "token carries no expiry");
        }
        var expiresAt = expirationTime.toInstant();
        if (!Instant.now(clock).isBefore(expiresAt)) {
            return rejected(EXPIRED, "token expired at " + expiresAt);
        }

        if (!Objects.equals(properties.issuer(), claimsSet.getIssuer())) {
            return rejected(ISSUER_MISMATCH, "expected issuer '" + properties.issuer() + "', got '" + claimsSet.getIssuer() + "'");
        }

        var audience = claimsSet.getAudience();
        if (audience == null || !audience.contains(properties.audience())) {
            return rejected(AUDIENCE_MISMATCH, "expected audience '" + properties.audience() + "', got " + audience);
        }

        AgreementTokenClaims claims;
        try {
            claims = decodeClaims(claimsSet, expiresAt);
        } catch (IllegalArgumentException e) {
            return rejected(MALFORMED_TOKEN, "token claims are malformed: " + e.getMessage());
        }

        if (!claims.client().equals(context.expectedClient())) {
            return rejected(CLIENT_MISMATCH, "token client does not match the signature-proven client");
        }
        if (!claims.service().equals(context.expectedService())) {
            return rejected(SERVICE_MISMATCH, "token service does not match the requested service");
        }
        if (context.isRest()) {
            var scopeMatches = claims.scope().stream()
                    .anyMatch(entry -> entry.matches(context.requestMethod(), context.requestPath()));
            if (!scopeMatches) {
                return rejected(SCOPE_MISMATCH, "no scope entry matches " + context.requestMethod() + " " + context.requestPath());
            }
        }

        return new AgreementTokenVerificationResult.Valid(claims);
    }

    private AgreementTokenClaims decodeClaims(JWTClaimsSet claimsSet, Instant expiresAt) {
        var agreementId = requireStringClaim(claimsSet, CLAIM_AGREEMENT_ID);
        var client = decodeClient(requireStringClaim(claimsSet, CLAIM_CLIENT));
        var service = decodeService(requireStringClaim(claimsSet, CLAIM_SERVICE));
        var scope = decodeScope(claimsSet);
        return new AgreementTokenClaims(agreementId, client, service, scope, claimsSet.getIssuer(), properties.audience(), expiresAt);
    }

    private static AgreementTokenVerificationResult.Rejected rejected(AgreementTokenRejectionReason reason, String details) {
        return new AgreementTokenVerificationResult.Rejected(reason, details);
    }
}
