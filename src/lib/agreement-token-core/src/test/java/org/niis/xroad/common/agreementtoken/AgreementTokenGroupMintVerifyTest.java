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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.ENDPOINT_NOT_ALLOWED;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.MALFORMED_TOKEN;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.NOT_GROUP_MEMBER;
import static org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason.SERVICE_MISMATCH;

class AgreementTokenGroupMintVerifyTest {

    private static final ClientId OWNER = ClientId.Conf.create("DEV", "COM", "333", "PROVIDER");
    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ClientId STRANGER = ClientId.Conf.create("DEV", "COM", "222", "STRANGER");
    private static final ServiceId SERVICE = ServiceId.Conf.create(OWNER, "getData", "v1");
    private static final ServiceId OTHER_SERVICE = ServiceId.Conf.create(OWNER, "getOther", "v1");

    private static final AgreementTokenGroup LOCAL_GROUP =
            new AgreementTokenGroup.Local(OWNER, LocalGroupId.Conf.create("readers"));
    private static final AgreementTokenGroup OTHER_LOCAL_GROUP =
            new AgreementTokenGroup.Local(OWNER, LocalGroupId.Conf.create("writers"));
    private static final AgreementTokenGroup GLOBAL_GROUP =
            new AgreementTokenGroup.Global(GlobalGroupId.Conf.create("DEV", "consumers"));

    private static final TestProtocolProperties PROPERTIES =
            new TestProtocolProperties("xroad-agreement-token", "security-server-proxy", Duration.ofSeconds(60), Duration.ZERO);
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private final FakeGroupAccess groupAccess = new FakeGroupAccess();
    private InMemoryAgreementTokenKeyProvider keyProvider;
    private AgreementTokenMinter minter;

    @BeforeEach
    void setUp() {
        keyProvider = new InMemoryAgreementTokenKeyProvider();
        keyProvider.addKey("1", TestKeyPairs.generate());
        minter = new AgreementTokenMinter(keyProvider, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldAcceptALocalGroupMemberCallingAGrantedEndpoint() {
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, "GET", "/foo/*");

        var result = verifier().verify(mint(LOCAL_GROUP), restContext(MEMBER, "GET", "/foo/bar"));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.ValidGroup.class);
        var claims = ((AgreementTokenVerificationResult.ValidGroup) result).claims();
        assertThat(claims.agreementId()).isEqualTo("agreement-1");
        assertThat(claims.group()).isEqualTo(LOCAL_GROUP);
        assertThat(claims.service()).isEqualTo(SERVICE);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldAcceptAGlobalGroupMemberCallingAGrantedEndpoint() {
        groupAccess.addMember(GLOBAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, "GET", "/foo/*");

        var result = verifier().verify(mint(GLOBAL_GROUP), restContext(MEMBER, "GET", "/foo/bar"));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.ValidGroup.class);
        assertThat(((AgreementTokenVerificationResult.ValidGroup) result).claims().group()).isEqualTo(GLOBAL_GROUP);
    }

    @Test
    void shouldAcceptAMemberOnSoapWhenTheServiceIsGranted() {
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, null, null);

        var result = verifier().verify(mint(LOCAL_GROUP), AgreementTokenRequestContext.forSoap(MEMBER, SERVICE));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.ValidGroup.class);
    }

    @Test
    void shouldRefuseANonMemberPresentingAGroupToken() {
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.allow(STRANGER, "GET", "/foo/*");

        var result = verifier().verify(mint(LOCAL_GROUP), restContext(STRANGER, "GET", "/foo/bar"));

        assertRejected(result, NOT_GROUP_MEMBER);
    }

    @Test
    void shouldRefuseAMemberCallingAnEndpointTheAclDoesNotGrantIt() {
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, "GET", "/foo/*");

        var result = verifier().verify(mint(LOCAL_GROUP), restContext(MEMBER, "POST", "/foo/bar"));

        assertRejected(result, ENDPOINT_NOT_ALLOWED);
    }

    @Test
    void shouldRefuseAGroupTokenForAnotherService() {
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, "*", "**");

        var result = verifier().verify(mint(LOCAL_GROUP),
                AgreementTokenRequestContext.forRest(MEMBER, OTHER_SERVICE, "GET", "/foo/bar"));

        assertRejected(result, SERVICE_MISMATCH);
    }

    @Test
    void shouldLetASubsystemInTwoGroupsReachTheEndpointsOfBothThroughEitherToken() {
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.addMember(OTHER_LOCAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, "GET", "/read/*");
        groupAccess.allow(MEMBER, "POST", "/write/*");
        var readersToken = mint(LOCAL_GROUP);
        var writersToken = mint(OTHER_LOCAL_GROUP);

        for (var token : List.of(readersToken, writersToken)) {
            assertThat(verifier().verify(token, restContext(MEMBER, "GET", "/read/a")))
                    .isInstanceOf(AgreementTokenVerificationResult.ValidGroup.class);
            assertThat(verifier().verify(token, restContext(MEMBER, "POST", "/write/a")))
                    .isInstanceOf(AgreementTokenVerificationResult.ValidGroup.class);
        }
    }

    @Test
    void shouldRefuseTheNextCallOnceTheMembershipAnswerChanges() {
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, "GET", "/foo/*");
        var token = mint(LOCAL_GROUP);
        assertThat(verifier().verify(token, restContext(MEMBER, "GET", "/foo/bar")))
                .isInstanceOf(AgreementTokenVerificationResult.ValidGroup.class);

        groupAccess.removeMember(LOCAL_GROUP, MEMBER);

        assertRejected(verifier().verify(token, restContext(MEMBER, "GET", "/foo/bar")), NOT_GROUP_MEMBER);
    }

    @Test
    void shouldRefuseEveryGroupTokenWhenTheVerifierHasNoGroupAccess() {
        var result = new AgreementTokenVerifier(keyProvider, PROPERTIES, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC))
                .verify(mint(LOCAL_GROUP), restContext(MEMBER, "GET", "/foo/bar"));

        assertRejected(result, NOT_GROUP_MEMBER);
    }

    @Test
    void shouldCarryTheGroupAndNoClientOrScope() throws ParseException {
        var claims = SignedJWT.parse(mint(LOCAL_GROUP)).getJWTClaimsSet();

        assertThat(claims.getClaim("client_id")).isNull();
        assertThat(claims.getClaim("scope")).isNull();
        assertThat(claims.getStringClaim("service_id")).isEqualTo("DEV:COM:333:PROVIDER:getData");
        assertThat(claims.getStringClaim("service_version")).isEqualTo("v1");
        assertThat(claims.getJSONObjectClaim("group")).containsEntry("type", "LOCAL")
                .containsEntry("owner", "DEV:COM:333:PROVIDER")
                .containsEntry("code", "readers");
    }

    @Test
    void shouldRejectAGroupTokenThatAlsoCarriesAClient() {
        var grant = new AgreementTokenGroupGrant("agreement-1", LOCAL_GROUP, SERVICE);
        var token = mintWithExtraClaim(grant, AgreementTokenClaimsCodec.CLAIM_CLIENT, "DEV:COM:222:TESTCLIENT");
        groupAccess.addMember(LOCAL_GROUP, MEMBER);
        groupAccess.allow(MEMBER, "*", "**");

        assertRejected(verifier().verify(token, restContext(MEMBER, "GET", "/foo/bar")), MALFORMED_TOKEN);
    }

    @Test
    void shouldStillVerifyASubsystemTokenWithoutConsultingGroupAccess() {
        var token = minter.mint(new AgreementTokenGrant("agreement-1", MEMBER, SERVICE,
                List.of(new AgreementTokenScope("GET", "/foo/*"))));

        var result = verifier().verify(token, restContext(MEMBER, "GET", "/foo/bar"));

        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        assertThat(groupAccess.lookups).isZero();
    }

    private String mint(AgreementTokenGroup group) {
        return minter.mint(new AgreementTokenGroupGrant("agreement-1", group, SERVICE));
    }

    private String mintWithExtraClaim(AgreementTokenGroupGrant grant, String name, String value) {
        try {
            var parsed = SignedJWT.parse(minter.mint(grant));
            var claims = new JWTClaimsSet.Builder(parsed.getJWTClaimsSet()).claim(name, value).build();
            var signed = new SignedJWT(parsed.getHeader(), claims);
            signed.sign(new ECDSASigner(keyProvider.activeKey().keyPair()));
            return signed.serialize();
        } catch (ParseException | JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private AgreementTokenVerifier verifier() {
        return new AgreementTokenVerifier(keyProvider, PROPERTIES, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC), groupAccess);
    }

    private static AgreementTokenRequestContext restContext(ClientId client, String method, String path) {
        return AgreementTokenRequestContext.forRest(client, SERVICE, method, path);
    }

    private static void assertRejected(AgreementTokenVerificationResult result, AgreementTokenRejectionReason reason) {
        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Rejected.class);
        assertThat(((AgreementTokenVerificationResult.Rejected) result).reason()).isEqualTo(reason);
    }

    private static final class FakeGroupAccess implements AgreementTokenGroupAccess {

        private final Map<AgreementTokenGroup, Set<ClientId>> members = new HashMap<>();
        private final Map<ClientId, List<AgreementTokenScope>> acl = new HashMap<>();
        private int lookups;

        void addMember(AgreementTokenGroup group, ClientId client) {
            members.computeIfAbsent(group, g -> new HashSet<>()).add(client);
        }

        void removeMember(AgreementTokenGroup group, ClientId client) {
            members.get(group).remove(client);
        }

        void allow(ClientId client, String method, String path) {
            acl.computeIfAbsent(client, c -> new ArrayList<>())
                    .add(new AgreementTokenScope(method == null ? "*" : method, path == null ? "**" : path));
        }

        @Override
        public boolean isMember(AgreementTokenGroup group, ClientId client) {
            lookups++;
            return members.getOrDefault(group, Set.of()).contains(client);
        }

        @Override
        public boolean isEndpointAllowed(ClientId client, ServiceId service, String requestMethod, String requestPath) {
            lookups++;
            return acl.getOrDefault(client, List.of()).stream()
                    .anyMatch(scope -> requestMethod == null || scope.matches(requestMethod, requestPath));
        }
    }

    private record TestProtocolProperties(String issuer, String audience, Duration tokenTtl, Duration expiryLeeway)
            implements AgreementTokenProtocolProperties {
    }
}
