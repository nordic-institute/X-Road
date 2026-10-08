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

package org.niis.xroad.edc.extension.policy.controlplane.util;

import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.AgreementPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Policy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyContextHelperTest {

    @Mock
    ParticipantAgentPolicyContext mockContext;
    @Mock
    AgreementPolicyContext mockAgreementContext;

    @Test
    void findMemberIdFromContextReturnsClientId() {
        var agent = new ParticipantAgent("test-id", Map.<String, Object>of(),
                Map.of("xrd:xroadInstance", "CS", "xrd:memberClass", "ORG", "xrd:memberCode", "1234"));
        var context = new CatalogPolicyContext(agent);

        var result = PolicyContextHelper.findMemberIdFromContext(context);

        assertThat(result).isPresent();
        assertThat(result.get().getXRoadInstance()).isEqualTo("CS");
        assertThat(result.get().getMemberClass()).isEqualTo("ORG");
        assertThat(result.get().getMemberCode()).isEqualTo("1234");
    }

    @Test
    void findMemberIdFromContextWithNullAgentReturnsEmpty() {
        when(mockContext.participantAgent()).thenReturn(null);

        var result = PolicyContextHelper.findMemberIdFromContext(mockContext);

        assertThat(result).isEmpty();
    }

    @Test
    void findMemberIdFromContextWithNoMemberAttributesReturnsEmpty() {
        var agent = new ParticipantAgent("test-id", Map.<String, Object>of(), Map.<String, String>of());
        var context = new CatalogPolicyContext(agent);

        var result = PolicyContextHelper.findMemberIdFromContext(context);

        assertThat(result).isEmpty();
    }

    @Test
    void findMemberIdFromContextWithPartialMemberAttributesReturnsEmpty() {
        var agent = new ParticipantAgent("test-id", Map.<String, Object>of(),
                Map.of("xrd:xroadInstance", "CS"));
        var context = new CatalogPolicyContext(agent);

        var result = PolicyContextHelper.findMemberIdFromContext(context);

        assertThat(result).isEmpty();
    }

    @Test
    void parseClientIdReturnsCorrectClientId() {
        var id = PolicyContextHelper.parseClientId("CS:ORG:1234");

        assertThat(id.getXRoadInstance()).isEqualTo("CS");
        assertThat(id.getMemberClass()).isEqualTo("ORG");
        assertThat(id.getMemberCode()).isEqualTo("1234");
        assertThat(id.getSubsystemCode()).isNull();
    }

    @Test
    void findMemberIdDelegatesToParticipantAgentForAParticipantAgentPolicyContext() {
        var agent = new ParticipantAgent("test-id", Map.<String, Object>of(),
                Map.of("xrd:xroadInstance", "CS", "xrd:memberClass", "ORG", "xrd:memberCode", "1234"));
        var context = new CatalogPolicyContext(agent);

        var result = PolicyContextHelper.findMemberId(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMemberCode()).isEqualTo("1234");
    }

    @Test
    void findMemberIdDelegatesToAgreementClaimsForAnAgreementPolicyContext() {
        when(mockAgreementContext.contractAgreement()).thenReturn(agreementWithMembershipClaims("CS", "ORG", "1234"));

        var result = PolicyContextHelper.findMemberId(mockAgreementContext);

        assertThat(result).isPresent();
        assertThat(result.get().getXRoadInstance()).isEqualTo("CS");
        assertThat(result.get().getMemberClass()).isEqualTo("ORG");
        assertThat(result.get().getMemberCode()).isEqualTo("1234");
    }

    @Test
    void findMemberIdReturnsEmptyWhenAgreementPolicyContextHasNoAgreement() {
        when(mockAgreementContext.contractAgreement()).thenReturn(null);

        var result = PolicyContextHelper.findMemberId(mockAgreementContext);

        assertThat(result).isEmpty();
    }

    @Test
    void findMemberIdFromClaimsReturnsClientIdFromMembershipCredential() {
        var claims = Map.<String, Object>of("vc", List.of(membershipCredential("CS", "ORG", "1234")));

        var result = PolicyContextHelper.findMemberIdFromClaims(claims);

        assertThat(result).isPresent();
        assertThat(result.get().getXRoadInstance()).isEqualTo("CS");
        assertThat(result.get().getMemberClass()).isEqualTo("ORG");
        assertThat(result.get().getMemberCode()).isEqualTo("1234");
    }

    @Test
    void findMemberIdFromClaimsReturnsEmptyWhenNoMembershipCredentialIsPresent() {
        var result = PolicyContextHelper.findMemberIdFromClaims(Map.of());

        assertThat(result).isEmpty();
    }

    private static ContractAgreement agreementWithMembershipClaims(String xroadInstance, String memberClass, String memberCode) {
        return ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("consumer-1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .claims(Map.of("vc", List.of(membershipCredential(xroadInstance, memberClass, memberCode))))
                .build();
    }

    private static VerifiableCredential membershipCredential(String xroadInstance, String memberClass, String memberCode) {
        var subject = CredentialSubject.Builder.newInstance()
                .claim("xroadInstance", xroadInstance)
                .claim("memberClass", memberClass)
                .claim("memberCode", memberCode)
                .build();
        return VerifiableCredential.Builder.newInstance()
                .type("XRoadMembershipCredential")
                .issuer(new Issuer("did:web:test-issuer"))
                .issuanceDate(Instant.now())
                .credentialSubject(subject)
                .build();
    }
}
