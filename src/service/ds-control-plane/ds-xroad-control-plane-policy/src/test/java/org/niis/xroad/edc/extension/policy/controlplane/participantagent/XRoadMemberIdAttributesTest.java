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

package org.niis.xroad.edc.extension.policy.controlplane.participantagent;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper.XRD_MEMBER_IDENTIFIER_ATTRIBUTE;

class XRoadMemberIdAttributesTest {

    private XRoadMemberIdAttributes sut;

    @BeforeEach
    void setUp() {
        sut = new XRoadMemberIdAttributes();
    }

    @Test
    void attributesForReturnsIdentifierWhenMembershipCredentialPresentWithClaim() {
        var token = buildTokenWithMembershipVc("CS:ORG:1234");

        var result = sut.attributesFor(token);

        assertThat(result).containsEntry(XRD_MEMBER_IDENTIFIER_ATTRIBUTE, "CS:ORG:1234");
    }

    @Test
    void attributesForReturnsEmptyMapWhenMembershipCredentialHasNoClaim() {
        // CredentialSubject must have at least one claim; use an unrelated claim to simulate
        // a VC that lacks xrdMemberIdentifier specifically.
        var subject = CredentialSubject.Builder.newInstance()
                .claim("membershipType", "X-Road")
                .build();
        var vc = VerifiableCredential.Builder.newInstance()
                .type(XRoadMemberIdAttributes.MEMBERSHIP_CREDENTIAL_TYPE)
                .issuer(new Issuer("did:web:test-issuer"))
                .issuanceDate(java.time.Instant.now())
                .credentialSubject(subject)
                .build();
        var token = ClaimToken.Builder.newInstance()
                .claim("vc", List.of(vc))
                .build();

        var result = sut.attributesFor(token);

        assertThat(result).isEmpty();
    }

    @Test
    void attributesForReturnsEmptyMapWhenNoVcListClaim() {
        var token = ClaimToken.Builder.newInstance().build();

        var result = sut.attributesFor(token);

        assertThat(result).isEmpty();
    }

    @Test
    void attributesForReturnsEmptyMapWhenVcListContainsNoMembershipCredential() {
        var subject = CredentialSubject.Builder.newInstance()
                .claim("xrdMemberIdentifier", "CS:ORG:1234")
                .build();
        var vc = VerifiableCredential.Builder.newInstance()
                .type("SomeOtherCredential")
                .issuer(new Issuer("did:web:test-issuer"))
                .issuanceDate(java.time.Instant.now())
                .credentialSubject(subject)
                .build();
        var token = ClaimToken.Builder.newInstance()
                .claim("vc", List.of(vc))
                .build();

        var result = sut.attributesFor(token);

        assertThat(result).isEmpty();
    }

    private ClaimToken buildTokenWithMembershipVc(String memberIdentifier) {
        var subject = CredentialSubject.Builder.newInstance()
                .claim(XRoadMemberIdAttributes.XRD_MEMBER_IDENTIFIER_CLAIM, memberIdentifier)
                .build();
        var vc = VerifiableCredential.Builder.newInstance()
                .type(XRoadMemberIdAttributes.MEMBERSHIP_CREDENTIAL_TYPE)
                .issuer(new Issuer("did:web:test-issuer"))
                .issuanceDate(java.time.Instant.now())
                .credentialSubject(subject)
                .build();
        return ClaimToken.Builder.newInstance()
                .claim("vc", List.of(vc))
                .build();
    }
}
