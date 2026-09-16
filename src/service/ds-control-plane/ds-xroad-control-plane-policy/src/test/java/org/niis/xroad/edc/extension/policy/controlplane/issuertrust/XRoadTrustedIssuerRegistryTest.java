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

import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class XRoadTrustedIssuerRegistryTest {

    private static final String DID_A = "did:web:cs1.example.test%3A6183:issuer";
    private static final String DID_B = "did:web:cs2.example.test%3A6183:issuer";
    private static final String OTHER_CREDENTIAL_TYPE = "SomeCredentialType";

    private final XRoadTrustedIssuerRegistry registry = new XRoadTrustedIssuerRegistry();

    @Test
    void replaceAllDropsAnIssuerMissingFromTheNewSnapshot() {
        registry.replaceAll(Set.of(DID_A, DID_B), TrustedIssuerRegistry.WILDCARD);

        registry.replaceAll(Set.of(DID_A), TrustedIssuerRegistry.WILDCARD);

        assertThat(registry.getSupportedTypes(new Issuer(DID_B))).isEmpty();
        assertThat(registry.getSupportedTypes(new Issuer(DID_A))).containsExactly(TrustedIssuerRegistry.WILDCARD);
    }

    @Test
    void replaceAllWithEmptySetDropsAllTrust() {
        registry.replaceAll(Set.of(DID_A, DID_B), TrustedIssuerRegistry.WILDCARD);

        registry.replaceAll(Set.of(), TrustedIssuerRegistry.WILDCARD);

        assertThat(registry.getSupportedTypes(new Issuer(DID_A))).isEmpty();
        assertThat(registry.getSupportedTypes(new Issuer(DID_B))).isEmpty();
    }

    @Test
    void registerStaysAdditiveBetweenReplaceAllSnapshots() {
        registry.replaceAll(Set.of(DID_A), TrustedIssuerRegistry.WILDCARD);

        registry.register(new Issuer(DID_B), OTHER_CREDENTIAL_TYPE);

        assertThat(registry.getSupportedTypes(new Issuer(DID_A))).containsExactly(TrustedIssuerRegistry.WILDCARD);
        assertThat(registry.getSupportedTypes(new Issuer(DID_B))).containsExactly(OTHER_CREDENTIAL_TYPE);
    }

    @Test
    void registerAddsAFurtherCredentialTypeWithoutDroppingTheExistingOne() {
        registry.register(new Issuer(DID_A), TrustedIssuerRegistry.WILDCARD);

        registry.register(new Issuer(DID_A), OTHER_CREDENTIAL_TYPE);

        assertThat(registry.getSupportedTypes(new Issuer(DID_A)))
                .containsExactlyInAnyOrder(TrustedIssuerRegistry.WILDCARD, OTHER_CREDENTIAL_TYPE);
    }

    @Test
    void getSupportedTypesForUnknownIssuerIsEmpty() {
        assertThat(registry.getSupportedTypes(new Issuer(DID_A))).isEmpty();
    }
}
