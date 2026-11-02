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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.key.TestKeyPairs;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.ENDPOINT_NOT_ALLOWED;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.MALFORMED_TOKEN;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.SERVICE_MISMATCH;

class AgreementTokenServiceMintVerifyTest {

    private static final ClientId OWNER = ClientId.Conf.create("DEV", "COM", "333", "PROVIDER");
    private static final ClientId CALLER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create(OWNER, "getData", "v1");
    private static final ServiceId OTHER_SERVICE = ServiceId.Conf.create(OWNER, "getOther", "v1");

    private static final TestProtocolProperties PROPERTIES =
            new TestProtocolProperties("xroad-agreement-token", "security-server-proxy", Duration.ofSeconds(60), Duration.ZERO);
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private InMemoryAgreementTokenKeyProvider keyProvider;
    private AgreementTokenMinter minter;
    private boolean aclAllows;
    private int aclLookups;
    private AgreementTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        keyProvider = new InMemoryAgreementTokenKeyProvider();
        keyProvider.addKey("1", TestKeyPairs.generate());
        minter = new AgreementTokenMinter(keyProvider, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        aclAllows = false;
        aclLookups = 0;
        verifier = new AgreementTokenVerifier(keyProvider, PROPERTIES, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC),
                new AgreementTokenGroupAccess() {
                    @Override
                    public boolean isMember(AgreementTokenGroup group, ClientId client) {
                        throw new AssertionError("a service-only token has no group");
                    }

                    @Override
                    public boolean isEndpointAllowed(ClientId client, ServiceId service, String method, String path) {
                        aclLookups++;
                        return client.equals(CALLER) && aclAllows;
                    }
                });
    }

    @Test
    void shouldAcceptWhenTheLiveAclGrantsTheCallerTheEndpoint() {
        aclAllows = true;

        var result = verifier.verify(mint(), AgreementTokenRequestContext.forRest(CALLER, SERVICE, "GET", "/foo"));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.ValidService.class);
        var claims = ((AgreementTokenVerificationResult.ValidService) result).claims();
        assertThat(claims.agreementId()).isEqualTo("agreement-1");
        assertThat(claims.service()).isEqualTo(SERVICE);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldRefuseWhenTheLiveAclDoesNotGrantTheCallerTheEndpoint() {
        var result = verifier.verify(mint(), AgreementTokenRequestContext.forRest(CALLER, SERVICE, "GET", "/foo"));

        assertRejected(result, ENDPOINT_NOT_ALLOWED);
        assertThat(aclLookups).isEqualTo(1);
    }

    @Test
    void shouldRefuseACallerTheAclDoesNotKnow() {
        aclAllows = true;
        var stranger = ClientId.Conf.create("DEV", "COM", "222", "STRANGER");

        assertRejected(verifier.verify(mint(), AgreementTokenRequestContext.forSoap(stranger, SERVICE)), ENDPOINT_NOT_ALLOWED);
    }

    @Test
    void shouldRefuseAnotherServiceWithoutConsultingTheAcl() {
        aclAllows = true;

        var result = verifier.verify(mint(), AgreementTokenRequestContext.forSoap(CALLER, OTHER_SERVICE));

        assertRejected(result, SERVICE_MISMATCH);
        assertThat(aclLookups).isZero();
    }

    @Test
    void shouldRefuseEveryServiceOnlyTokenWhenTheVerifierHasNoAclAnswer() {
        var withoutAcl = new AgreementTokenVerifier(keyProvider, PROPERTIES, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        assertRejected(withoutAcl.verify(mint(), AgreementTokenRequestContext.forSoap(CALLER, SERVICE)), ENDPOINT_NOT_ALLOWED);
    }

    @Test
    void shouldCarryTheServiceAndNoClientGroupOrScope() throws ParseException {
        var claims = SignedJWT.parse(mint()).getJWTClaimsSet();

        assertThat(claims.getClaim("client_id")).isNull();
        assertThat(claims.getClaim("scope")).isNull();
        assertThat(claims.getClaim("group")).isNull();
        assertThat(claims.getStringClaim("service_id")).isEqualTo("DEV:COM:333:PROVIDER:getData");
        assertThat(claims.getStringClaim("service_version")).isEqualTo("v1");
    }

    @Test
    void shouldRejectATokenThatCarriesAScopeButNoClient() {
        aclAllows = true;
        var token = withExtraClaim(mint(), "scope", List.of(Map.of("method", "*", "path", "**")));

        assertRejected(verifier.verify(token, AgreementTokenRequestContext.forSoap(CALLER, SERVICE)), MALFORMED_TOKEN);
        assertThat(aclLookups).isZero();
    }

    @Test
    void shouldRejectATokenThatCarriesAClientButNoScope() {
        aclAllows = true;
        var token = withExtraClaim(mint(), "client_id", "DEV:COM:222:TESTCLIENT");

        assertRejected(verifier.verify(token, AgreementTokenRequestContext.forSoap(CALLER, SERVICE)), MALFORMED_TOKEN);
        assertThat(aclLookups).isZero();
    }

    @Test
    void shouldRejectAServiceOnlyTokenWithoutAServiceClaim() {
        aclAllows = true;
        var token = withoutClaim(mint(), "service_id");

        assertRejected(verifier.verify(token, AgreementTokenRequestContext.forSoap(CALLER, SERVICE)), MALFORMED_TOKEN);
        assertThat(aclLookups).isZero();
    }

    private String mint() {
        return minter.mint(new AgreementTokenServiceGrant("agreement-1", SERVICE));
    }

    private String withExtraClaim(String token, String name, Object value) {
        return resign(token, claims -> claims.claim(name, value));
    }

    private String withoutClaim(String token, String name) {
        return resign(token, claims -> claims.claim(name, null));
    }

    private String resign(String token, Consumer<JWTClaimsSet.Builder> change) {
        try {
            var parsed = SignedJWT.parse(token);
            var builder = new JWTClaimsSet.Builder(parsed.getJWTClaimsSet());
            change.accept(builder);
            var signed = new SignedJWT(parsed.getHeader(), builder.build());
            signed.sign(new ECDSASigner(keyProvider.activeKey().keyPair()));
            return signed.serialize();
        } catch (ParseException | JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertRejected(AgreementTokenVerificationResult result, AgreementTokenRejectionReason reason) {
        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Rejected.class);
        assertThat(((AgreementTokenVerificationResult.Rejected) result).reason()).isEqualTo(reason);
    }

    private record TestProtocolProperties(String issuer, String audience, Duration tokenTtl, Duration expiryLeeway)
            implements AgreementTokenProtocolProperties {
    }
}
