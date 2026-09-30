/*
 * The MIT License
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

package org.niis.xroad.proxy.dataplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.agreementtoken.AgreementTokenRequestContext;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerificationResult;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerifier;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.proxy.controlplane.AgreementGrant;
import org.niis.xroad.proxy.controlplane.AgreementGrantRpcClient;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.Endpoint;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementTokenIssuerTest {

    private static final String AGREEMENT_ID = "agreement-1";
    private static final ClientId CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create("DEV", "COM", "333", "PROVIDER", "getData", "v1");

    private static final TestAgreementTokenProtocolProperties PROPERTIES =
            new TestAgreementTokenProtocolProperties("x-road-provider-data-plane", "x-road-server-proxy", Duration.ofSeconds(60));

    @Mock
    private AgreementGrantRpcClient grantRpcClient;
    @Mock
    private ServerConfProvider serverConfProvider;

    private AgreementTokenKeyMaterial keyMaterial;
    private TestAgreementTokenKeyProvider keyProvider;
    private AgreementTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        keyProvider = new TestAgreementTokenKeyProvider();
        keyProvider.addKey("1");
        keyMaterial = mock(AgreementTokenKeyMaterial.class);
        lenient().when(keyMaterial.provider()).thenReturn(Optional.of(keyProvider));
        issuer = new AgreementTokenIssuer(grantRpcClient, serverConfProvider, keyMaterial, PROPERTIES);
    }

    @Test
    void mintsTokenWhenGrantAndLiveAclFullyMatch() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"), new AgreementTokenScope("POST", "/bar"));
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID)).thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE)).thenReturn(List.of(
                new Endpoint("getData", "GET", "/foo/*", false),
                new Endpoint("getData", "post", "/bar", false)));

        var token = issuer.issueToken(AGREEMENT_ID);

        assertThat(token).isPresent();
        var claims = decodeAndVerify(token.get());
        assertThat(claims.agreementId()).isEqualTo(AGREEMENT_ID);
        assertThat(claims.client()).isEqualTo(CONSUMER);
        assertThat(claims.service()).isEqualTo(SERVICE);
        assertThat(claims.scope()).containsExactlyInAnyOrderElementsOf(scope);
    }

    @Test
    void returnsEmptyWhenNoGrant() {
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID)).thenReturn(Optional.empty());

        assertThat(issuer.issueToken(AGREEMENT_ID)).isEmpty();
        verifyNoInteractions(serverConfProvider);
    }

    @Test
    void returnsEmptyWhenGrantLookupThrows() {
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID)).thenThrow(new RuntimeException("boom"));

        assertThat(issuer.issueToken(AGREEMENT_ID)).isEmpty();
    }

    @Test
    void returnsEmptyWhenAclEnumerationThrows() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"));
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID)).thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE)).thenThrow(new RuntimeException("boom"));

        assertThat(issuer.issueToken(AGREEMENT_ID)).isEmpty();
    }

    @Test
    void returnsEmptyWhenMintingThrows() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"));
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID)).thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE))
                .thenReturn(List.of(new Endpoint("getData", "GET", "/foo/*", false)));
        var throwingKeyProvider = mock(AgreementTokenKeyProvider.class);
        when(throwingKeyProvider.activeKey()).thenThrow(new RuntimeException("boom"));
        when(keyMaterial.provider()).thenReturn(Optional.of(throwingKeyProvider));

        assertThat(issuer.issueToken(AGREEMENT_ID)).isEmpty();
    }

    @Test
    void keepsOnlyTheScopeEntriesTheLiveAclStillGrants() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"), new AgreementTokenScope("POST", "/bar"));
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID)).thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE))
                .thenReturn(List.of(new Endpoint("getData", "GET", "/foo/*", false)));

        var token = issuer.issueToken(AGREEMENT_ID);

        assertThat(token).isPresent();
        var claims = decodeAndVerify(token.get());
        assertThat(claims.scope()).containsExactly(new AgreementTokenScope("GET", "/foo/*"));
    }

    @Test
    void returnsEmptyWhenNoGrantedScopeEntrySurvivesTheLiveAcl() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"));
        when(grantRpcClient.resolveAgreementGrant(AGREEMENT_ID)).thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE))
                .thenReturn(List.of(new Endpoint("getData", "DELETE", "/other", false)));

        assertThat(issuer.issueToken(AGREEMENT_ID)).isEmpty();
    }

    @Test
    void returnsEmptyWhenKeyMaterialUnavailableAndNeverCallsTheGrantLookup() {
        when(keyMaterial.provider()).thenReturn(Optional.empty());

        assertThat(issuer.issueToken(AGREEMENT_ID)).isEmpty();
        verifyNoInteractions(grantRpcClient);
    }

    @Test
    void returnsEmptyForBlankAgreementIdWithoutCallingAnyCollaborator() {
        assertThat(issuer.issueToken(" ")).isEmpty();
        assertThat(issuer.issueToken(null)).isEmpty();
        verifyNoInteractions(grantRpcClient, serverConfProvider);
    }

    private AgreementTokenClaimsView decodeAndVerify(String token) {
        var verifier = new AgreementTokenVerifier(keyProvider, PROPERTIES);
        var result = verifier.verify(token, AgreementTokenRequestContext.forSoap(CONSUMER, SERVICE));
        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        var claims = ((AgreementTokenVerificationResult.Valid) result).claims();
        return new AgreementTokenClaimsView(claims.agreementId(), claims.client(), claims.service(), claims.scope());
    }

    private record AgreementTokenClaimsView(String agreementId, ClientId client, ServiceId service, List<AgreementTokenScope> scope) {
    }
}
