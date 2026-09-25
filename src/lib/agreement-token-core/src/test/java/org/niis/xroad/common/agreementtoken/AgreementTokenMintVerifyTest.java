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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenSigningKey;
import org.niis.xroad.common.agreementtoken.key.TestKeyPairs;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_AGREEMENT_ID;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_CLIENT;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.CLAIM_SCOPE;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeClient;
import static org.niis.xroad.common.agreementtoken.AgreementTokenClaimsCodec.encodeScope;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.AUDIENCE_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.CLIENT_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.EXPIRED;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.INVALID_SIGNATURE;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.ISSUER_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.MALFORMED_TOKEN;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.SCOPE_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.SERVICE_MISMATCH;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.UNKNOWN_KEY_ID;

class AgreementTokenMintVerifyTest {

    private static final ClientId CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ClientId OTHER_CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "OTHERCLIENT");
    private static final ClientId PROVIDER = ClientId.Conf.create("DEV", "COM", "333", "PROVIDER");
    private static final ServiceId SERVICE = ServiceId.Conf.create(PROVIDER, "getData", "v1");
    private static final ServiceId OTHER_SERVICE = ServiceId.Conf.create(PROVIDER, "getOther", "v1");

    private static final TestProtocolProperties PROPERTIES =
            new TestProtocolProperties("xroad-agreement-token", "security-server-proxy", Duration.ofSeconds(60));

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private InMemoryAgreementTokenKeyProvider keyProvider;
    private AgreementTokenMinter minter;
    private AgreementTokenSigningKey activeKey;

    @BeforeEach
    void setUp() {
        keyProvider = new InMemoryAgreementTokenKeyProvider();
        activeKey = keyProvider.addKey("1", randomKeyPair());
        minter = new AgreementTokenMinter(keyProvider, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldRoundTripMintAndVerifyForRest() {
        var grant = new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("GET", "/foo/*")));
        var token = minter.mint(grant);

        var verifier = verifierAt(NOW.plusSeconds(1));
        var result = verifier.verify(token, AgreementTokenRequestContext.forRest(CONSUMER, SERVICE, "GET", "/foo/bar"));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        var claims = ((AgreementTokenVerificationResult.Valid) result).claims();
        assertThat(claims.agreementId()).isEqualTo("agreement-1");
        assertThat(claims.client()).isEqualTo(CONSUMER);
        assertThat(claims.service()).isEqualTo(SERVICE);
        assertThat(claims.issuer()).isEqualTo(PROPERTIES.issuer());
        assertThat(claims.audience()).isEqualTo(PROPERTIES.audience());
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldRoundTripMintAndVerifyForSoapIgnoringScope() {
        // A scope that would never match a SOAP call proves SOAP verification never consults it.
        var grant = new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("POST", "/never/matches")));
        var token = minter.mint(grant);

        var verifier = verifierAt(NOW.plusSeconds(1));
        var result = verifier.verify(token, AgreementTokenRequestContext.forSoap(CONSUMER, SERVICE));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
    }

    @Test
    void shouldTakeTtlFromConfigurationNotHardcode() {
        var customTtlProperties = new TestProtocolProperties("xroad-agreement-token", "security-server-proxy", Duration.ofSeconds(5));
        var customTtlMinter = new AgreementTokenMinter(keyProvider, customTtlProperties, Clock.fixed(NOW, ZoneOffset.UTC));
        var grant = new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**")));

        var token = customTtlMinter.mint(grant);

        var verifier = new AgreementTokenVerifier(keyProvider, customTtlProperties, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        var result = verifier.verify(token, AgreementTokenRequestContext.forSoap(CONSUMER, SERVICE));
        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        var claims = ((AgreementTokenVerificationResult.Valid) result).claims();
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void defaultTokenTtlShouldBeSixtySeconds() {
        assertThat(AgreementTokenProtocolProperties.DEFAULT_TOKEN_TTL).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldRejectBlankToken() {
        assertRejected(verifierAt(NOW).verify("", restContext()), MALFORMED_TOKEN);
    }

    @Test
    void shouldRejectTokenThatIsNotAJws() {
        assertRejected(verifierAt(NOW).verify("this-is-not-a-jwt", restContext()), MALFORMED_TOKEN);
    }

    @Test
    void shouldRejectTruncatedToken() {
        var token = mintDefaultGrant();
        var truncated = token.substring(0, token.lastIndexOf('.'));

        assertRejected(verifierAt(NOW.plusSeconds(1)).verify(truncated, restContext()), MALFORMED_TOKEN);
    }

    @Test
    void shouldRejectTokenWithNoKeyIdHeader() throws Exception {
        var claimsSet = defaultClaimsSetBuilder().build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).build(), claimsSet);
        jwt.sign(new ECDSASigner(activeKey.keyPair()));

        assertRejected(verifierAt(NOW.plusSeconds(1)).verify(jwt.serialize(), restContext()), MALFORMED_TOKEN);
    }

    @Test
    void shouldRejectTokenMissingARequiredClaim() throws Exception {
        var claimsSet = new JWTClaimsSet.Builder()
                .issuer(PROPERTIES.issuer())
                .audience(PROPERTIES.audience())
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(60)))
                .claim(CLAIM_AGREEMENT_ID, "agreement-1")
                .claim(CLAIM_CLIENT, encodeClient(CONSUMER))
                // service_id claim deliberately omitted
                .claim(CLAIM_SCOPE, encodeScope(List.of(new AgreementTokenScope("*", "**"))))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(activeKey.keyId()).build(), claimsSet);
        jwt.sign(new ECDSASigner(activeKey.keyPair()));

        assertRejected(verifierAt(NOW.plusSeconds(1)).verify(jwt.serialize(), restContext()), MALFORMED_TOKEN);
    }

    @Test
    void shouldMintAnEs256TokenNamingTheActiveKey() throws Exception {
        var token = mintDefaultGrant();

        var header = SignedJWT.parse(token).getHeader();

        assertThat(header.getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(header.getKeyID()).isEqualTo(activeKey.keyId());
    }

    @Test
    void shouldRejectTokenSignedWithAnotherAlgorithmUnderAKnownKeyId() throws Exception {
        var hmacHeader = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(activeKey.keyId()).build();
        var jwt = new SignedJWT(hmacHeader, defaultClaimsSetBuilder().build());
        var hmacSecret = new byte[32];
        new SecureRandom().nextBytes(hmacSecret);
        jwt.sign(new MACSigner(hmacSecret));

        assertRejected(verifierAt(NOW.plusSeconds(1)).verify(jwt.serialize(), restContext()), INVALID_SIGNATURE);
    }

    @Test
    void shouldNormalizeTheRequestPathLikeTheAclBeforeMatchingScope() {
        var token = mintDefaultGrant();

        var result = verifierAt(NOW.plusSeconds(1))
                .verify(token, AgreementTokenRequestContext.forRest(CONSUMER, SERVICE, "GET", "/foo/./bar"));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
    }

    @Test
    void shouldRejectPathTraversalThatEscapesTheGrantedScope() {
        var grant = new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("GET", "/foo/**")));
        var token = minter.mint(grant);
        var verifier = verifierAt(NOW.plusSeconds(1));

        assertRejected(verifier.verify(token, AgreementTokenRequestContext.forRest(CONSUMER, SERVICE, "GET", "/foo/../admin")),
                SCOPE_MISMATCH);
        assertRejected(verifier.verify(token, AgreementTokenRequestContext.forRest(CONSUMER, SERVICE, "GET", "/foo/%2e%2e/admin")),
                SCOPE_MISMATCH);
    }

    @Test
    void shouldRejectASignedTokenWhoseScopePatternDoesNotCompile() throws Exception {
        var claimsSet = new JWTClaimsSet.Builder()
                .issuer(PROPERTIES.issuer())
                .audience(PROPERTIES.audience())
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(60)))
                .claim(CLAIM_AGREEMENT_ID, "agreement-1")
                .claim(CLAIM_CLIENT, encodeClient(CONSUMER))
                .claim(AgreementTokenClaimsCodec.CLAIM_SERVICE, AgreementTokenClaimsCodec.encodeService(SERVICE))
                .claim(CLAIM_SCOPE, List.of(Map.of("method", "GET", "path", "/foo/*?")))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(activeKey.keyId()).build(), claimsSet);
        jwt.sign(new ECDSASigner(activeKey.keyPair()));

        assertRejected(verifierAt(NOW.plusSeconds(1)).verify(jwt.serialize(), restContext()), MALFORMED_TOKEN);
    }

    @Test
    void shouldVerifyWithAPublicOnlyKeyAndRefuseToMintWithIt() {
        var token = mintDefaultGrant();

        var publicOnlyProvider = new InMemoryAgreementTokenKeyProvider();
        publicOnlyProvider.addKey(activeKey.keyId(), activeKey.publicKey());
        var verifier = new AgreementTokenVerifier(publicOnlyProvider, PROPERTIES, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        var publicOnlyMinter = new AgreementTokenMinter(publicOnlyProvider, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        var grant = new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**")));

        assertThat(verifier.verify(token, restContext())).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        assertThatThrownBy(() -> publicOnlyMinter.mint(grant)).isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void shouldRejectUnknownKeyId() {
        var token = mintDefaultGrant();

        var strangerKeyProvider = new InMemoryAgreementTokenKeyProvider();
        strangerKeyProvider.addKey("other-key", randomKeyPair());
        var verifier = new AgreementTokenVerifier(strangerKeyProvider, PROPERTIES, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        assertRejected(verifier.verify(token, restContext()), UNKNOWN_KEY_ID);
    }

    @Test
    void shouldRejectInvalidSignature() {
        var token = mintDefaultGrant();

        var wrongKeyProvider = new InMemoryAgreementTokenKeyProvider();
        wrongKeyProvider.addKey("1", randomKeyPair());
        var verifier = new AgreementTokenVerifier(wrongKeyProvider, PROPERTIES, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        assertRejected(verifier.verify(token, restContext()), INVALID_SIGNATURE);
    }

    @Test
    void shouldRejectExpiredToken() {
        var token = mintDefaultGrant();

        var verifier = verifierAt(NOW.plusSeconds(61));

        assertRejected(verifier.verify(token, restContext()), EXPIRED);
    }

    @Test
    void shouldRejectWrongIssuer() {
        var token = mintDefaultGrant();

        var wrongIssuerProperties = new TestProtocolProperties("someone-else", PROPERTIES.audience(), PROPERTIES.tokenTtl());
        var verifier = new AgreementTokenVerifier(keyProvider, wrongIssuerProperties, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        assertRejected(verifier.verify(token, restContext()), ISSUER_MISMATCH);
    }

    @Test
    void shouldRejectWrongAudience() {
        var token = mintDefaultGrant();

        var wrongAudienceProperties = new TestProtocolProperties(PROPERTIES.issuer(), "someone-else", PROPERTIES.tokenTtl());
        var verifier = new AgreementTokenVerifier(keyProvider, wrongAudienceProperties, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        assertRejected(verifier.verify(token, restContext()), AUDIENCE_MISMATCH);
    }

    @Test
    void shouldRejectClientMismatch() {
        var token = mintDefaultGrant();

        var verifier = verifierAt(NOW.plusSeconds(1));
        var result = verifier.verify(token, AgreementTokenRequestContext.forRest(OTHER_CONSUMER, SERVICE, "GET", "/foo/bar"));

        assertRejected(result, CLIENT_MISMATCH);
    }

    @Test
    void shouldRejectServiceMismatch() {
        var token = mintDefaultGrant();

        var verifier = verifierAt(NOW.plusSeconds(1));
        var result = verifier.verify(token, AgreementTokenRequestContext.forRest(CONSUMER, OTHER_SERVICE, "GET", "/foo/bar"));

        assertRejected(result, SERVICE_MISMATCH);
    }

    @Test
    void shouldRejectScopeMismatchOnMethod() {
        var token = mintDefaultGrant();

        var verifier = verifierAt(NOW.plusSeconds(1));
        var result = verifier.verify(token, AgreementTokenRequestContext.forRest(CONSUMER, SERVICE, "POST", "/foo/bar"));

        assertRejected(result, SCOPE_MISMATCH);
    }

    @Test
    void shouldRejectScopeMismatchOnPath() {
        var token = mintDefaultGrant();

        var verifier = verifierAt(NOW.plusSeconds(1));
        var result = verifier.verify(token, AgreementTokenRequestContext.forRest(CONSUMER, SERVICE, "GET", "/other/path"));

        assertRejected(result, SCOPE_MISMATCH);
    }

    @Test
    void shouldVerifyTokensFromPreviousKeyAfterRotation() {
        var oldToken = mintDefaultGrant();

        keyProvider.rotate();
        var newMinter = new AgreementTokenMinter(keyProvider, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        var newToken = newMinter.mint(
                new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("GET", "/foo/*"))));

        var verifier = verifierAt(NOW.plusSeconds(1));

        assertThat(verifier.verify(oldToken, restContext())).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        assertThat(verifier.verify(newToken, restContext())).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
    }

    @Test
    void shouldFailWhenNoActiveKeyIsAvailable() {
        var emptyKeyProvider = new InMemoryAgreementTokenKeyProvider();
        var minterWithoutKeys = new AgreementTokenMinter(emptyKeyProvider, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        var grant = new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**")));

        assertThatThrownBy(() -> minterWithoutKeys.mint(grant))
                .isInstanceOf(XrdRuntimeException.class);
    }

    private String mintDefaultGrant() {
        var grant = new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("GET", "/foo/*")));
        return minter.mint(grant);
    }

    private JWTClaimsSet.Builder defaultClaimsSetBuilder() {
        return new JWTClaimsSet.Builder()
                .issuer(PROPERTIES.issuer())
                .audience(PROPERTIES.audience())
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(60)))
                .claim(CLAIM_AGREEMENT_ID, "agreement-1")
                .claim(CLAIM_CLIENT, encodeClient(CONSUMER))
                .claim(AgreementTokenClaimsCodec.CLAIM_SERVICE, AgreementTokenClaimsCodec.encodeService(SERVICE))
                .claim(CLAIM_SCOPE, encodeScope(List.of(new AgreementTokenScope("GET", "/foo/*"))));
    }

    private AgreementTokenRequestContext restContext() {
        return AgreementTokenRequestContext.forRest(CONSUMER, SERVICE, "GET", "/foo/bar");
    }

    private AgreementTokenVerifier verifierAt(Instant instant) {
        return new AgreementTokenVerifier(keyProvider, PROPERTIES, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static void assertRejected(AgreementTokenVerificationResult result, AgreementTokenRejectionReason reason) {
        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Rejected.class);
        assertThat(((AgreementTokenVerificationResult.Rejected) result).reason()).isEqualTo(reason);
    }

    private static ECKey randomKeyPair() {
        return TestKeyPairs.generate();
    }

    private record TestProtocolProperties(String issuer, String audience, Duration tokenTtl) implements AgreementTokenProtocolProperties {
    }
}
