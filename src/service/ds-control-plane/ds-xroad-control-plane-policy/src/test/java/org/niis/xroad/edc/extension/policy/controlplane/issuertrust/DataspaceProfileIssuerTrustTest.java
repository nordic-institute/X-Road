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
package org.niis.xroad.edc.extension.policy.controlplane.issuertrust;

import org.eclipse.edc.connector.core.profile.DataspaceProfileContextRegistryImpl;
import org.eclipse.edc.iam.verifiablecredentials.rules.HasValidIssuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.jsonld.spi.JsonLdNamespace;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.ParticipantIdExtractionFunction;
import org.eclipse.edc.protocol.spi.ProtocolVersion;
import org.eclipse.edc.protocol.spi.ProtocolWebhook;
import org.eclipse.edc.protocol.spi.TrustedIssuer;
import org.eclipse.edc.spi.EdcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class DataspaceProfileIssuerTrustTest {

    private static final String PROFILE_NAME = "test-profile";
    private static final String OTHER_PROFILE_NAME = "other-test-profile";
    private static final String DID_1 = "did:web:cs1.example.test%3A6183:issuer";
    private static final String DID_2 = "did:web:cs2.example.test%3A6183:issuer";
    private static final String UNTRUSTED_DID = "did:web:untrusted.example.test:issuer";
    private static final String CREDENTIAL_TYPE = "SomeCredentialType";
    private static final String OTHER_CREDENTIAL_TYPE = "AnotherCredentialType";

    private final DataspaceProfileContextRegistry profileRegistry = new DataspaceProfileContextRegistryImpl();
    private final DataspaceProfileIssuerTrust issuerTrust = new DataspaceProfileIssuerTrust(profileRegistry);

    @BeforeEach
    void registerDefaultProfile() {
        profileRegistry.registerDefault(profile(PROFILE_NAME, List.of(trustedIssuer(UNTRUSTED_DID))));
    }

    @Test
    void trustOnlyTrustsEveryGivenIssuerForEveryCredentialType() {
        issuerTrust.trustOnly(Set.of(DID_1, DID_2));

        assertThat(isTrusted(DID_1, CREDENTIAL_TYPE)).isTrue();
        assertThat(isTrusted(DID_1, OTHER_CREDENTIAL_TYPE)).isTrue();
        assertThat(isTrusted(DID_2, CREDENTIAL_TYPE)).isTrue();
        assertThat(isTrusted(DID_2, OTHER_CREDENTIAL_TYPE)).isTrue();
    }

    @Test
    void trustOnlyReplacesTheTrustedIssuersTheProfileWasRegisteredWith() {
        issuerTrust.trustOnly(Set.of(DID_1));

        assertThat(isTrusted(UNTRUSTED_DID, CREDENTIAL_TYPE)).isFalse();
    }

    @Test
    void trustOnlyStopsTrustingAnIssuerMissingFromTheNewSet() {
        issuerTrust.trustOnly(Set.of(DID_1, DID_2));

        issuerTrust.trustOnly(Set.of(DID_1));

        assertThat(isTrusted(DID_2, CREDENTIAL_TYPE)).isFalse();
        assertThat(isTrusted(DID_1, CREDENTIAL_TYPE)).isTrue();
    }

    @Test
    void trustOnlyWithEmptySetDropsAllTrust() {
        issuerTrust.trustOnly(Set.of(DID_1, DID_2));

        issuerTrust.trustOnly(Set.of());

        assertThat(isTrusted(DID_1, CREDENTIAL_TYPE)).isFalse();
        assertThat(isTrusted(DID_2, CREDENTIAL_TYPE)).isFalse();
    }

    @Test
    void trustOnlyLeavesTheOtherProfileSettingsUntouched() {
        var original = profileRegistry.getProfile(PROFILE_NAME);

        issuerTrust.trustOnly(Set.of(DID_1));

        var updated = profileRegistry.getProfile(PROFILE_NAME);
        assertThat(updated.name()).isEqualTo(original.name());
        assertThat(updated.protocolVersion()).isEqualTo(original.protocolVersion());
        assertThat(updated.webhook()).isSameAs(original.webhook());
        assertThat(updated.idExtractionFunction()).isSameAs(original.idExtractionFunction());
        assertThat(updated.protocolNamespace()).isEqualTo(original.protocolNamespace());
        assertThat(updated.jsonLdContextsUrl()).isEqualTo(original.jsonLdContextsUrl());
    }

    @Test
    void trustOnlyKeepsEveryRegisteredProfileActive() {
        profileRegistry.registerDefault(profile(OTHER_PROFILE_NAME, List.of()));

        issuerTrust.trustOnly(Set.of(DID_1));

        assertThat(profileRegistry.getProfiles()).extracting(DataspaceProfileContext::name)
                .containsExactlyInAnyOrder(PROFILE_NAME, OTHER_PROFILE_NAME);
        assertThat(isTrusted(OTHER_PROFILE_NAME, DID_1, CREDENTIAL_TYPE)).isTrue();
    }

    @Test
    void trustOnlyFailsWhenNoProfileIsRegistered() {
        var emptyIssuerTrust = new DataspaceProfileIssuerTrust(new DataspaceProfileContextRegistryImpl());

        assertThat(catchThrowable(() -> emptyIssuerTrust.trustOnly(Set.of(DID_1)))).isInstanceOf(EdcException.class);
    }

    private boolean isTrusted(String issuerDid, String credentialType) {
        return isTrusted(PROFILE_NAME, issuerDid, credentialType);
    }

    private boolean isTrusted(String profileName, String issuerDid, String credentialType) {
        var credential = VerifiableCredential.Builder.newInstance()
                .type(credentialType)
                .issuer(new Issuer(issuerDid))
                .issuanceDate(Instant.now())
                .credentialSubject(CredentialSubject.Builder.newInstance().id("subject").claim("key", "value").build())
                .build();
        return new HasValidIssuer(profileRegistry.getProfile(profileName)).apply(credential).succeeded();
    }

    private static DataspaceProfileContext profile(String name, List<TrustedIssuer> trustedIssuers) {
        ProtocolWebhook webhook = () -> "https://localhost/dsp/" + name;
        ParticipantIdExtractionFunction idExtractionFunction = claimToken -> "participant";
        return new DataspaceProfileContext(name,
                new ProtocolVersion("2025-1", "/" + name, "HTTPS"),
                webhook,
                idExtractionFunction,
                new JsonLdNamespace("https://w3id.org/dspace/2025/1/"),
                List.of("https://w3id.org/dspace/2025/1/context.jsonld"),
                trustedIssuers);
    }

    private static TrustedIssuer trustedIssuer(String issuerDid) {
        return TrustedIssuer.Builder.newInstance().id(issuerDid).supportedTypes(List.of(TrustedIssuer.WILDCARD_TYPE)).build();
    }
}
