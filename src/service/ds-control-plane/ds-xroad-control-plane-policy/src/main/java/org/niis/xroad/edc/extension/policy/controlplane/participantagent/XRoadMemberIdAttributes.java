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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgentServiceExtension;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper.XRD_MEMBER_IDENTIFIER_ATTRIBUTE;

/**
 * {@link ParticipantAgentServiceExtension} that extracts the X-Road member identifier from a
 * validated {@code MembershipCredential} in the DCP claim token and maps it to the participant-agent
 * attribute {@value PolicyContextHelper#XRD_MEMBER_IDENTIFIER_ATTRIBUTE}.
 *
 * <p>The credential is expected to carry {@code credentialSubject.xrdMemberIdentifier} in
 * colon-separated form ({@code INSTANCE:CLASS:CODE}), set by the issuer service from the holder's
 * registered {@code xrdMemberIdentifier} property.
 *
 * <p>If no {@code MembershipCredential} is present, or the field is absent/null, an empty map is
 * returned — no exception is thrown. Existing VCs issued before the issuer upgrade will therefore
 * keep returning empty until reissued (backward-compatible additive change).
 */
@Slf4j
class XRoadMemberIdAttributes implements ParticipantAgentServiceExtension {

    static final String MEMBERSHIP_CREDENTIAL_TYPE = "MembershipCredential";
    static final String XRD_MEMBER_IDENTIFIER_CLAIM = "xrdMemberIdentifier";

    @Override
    @NotNull
    public Map<String, String> attributesFor(ClaimToken token) {
        var vcList = token.getListClaim("vc");
        log.debug("attributesFor: ClaimToken claims={} vcListSize={} vcListElementTypes={}",
                token.getClaims().keySet(),
                vcList == null ? -1 : vcList.size(),
                vcList == null ? "<null>" : vcList.stream().map(o -> o == null ? "null" : o.getClass().getName()).toList());
        var result = extractMemberIdentifier(vcList)
                .map(value -> Map.of(XRD_MEMBER_IDENTIFIER_ATTRIBUTE, value))
                .orElse(Map.of());
        log.debug("attributesFor: result={}", result);
        return result;
    }

    private Optional<String> extractMemberIdentifier(List<?> vcList) {
        // EDC's DcpDefaultServicesExtension.defaultClaimTokenFunction stuffs the
        // list of VerifiablePresentation.getCredentials() (i.e. VerifiableCredential,
        // NOT VerifiableCredentialContainer) under the "vc" claim key.
        if (vcList == null) {
            return Optional.empty();
        }
        return vcList.stream()
                .filter(VerifiableCredential.class::isInstance)
                .map(VerifiableCredential.class::cast)
                .peek(vc -> log.debug("extractMemberIdentifier: candidate vc types={} subjects={}",
                        vc.getType(), vc.getCredentialSubject().size()))
                .filter(this::isMembershipCredential)
                .flatMap(credential -> credential.getCredentialSubject().stream())
                .peek(subject -> log.debug("extractMemberIdentifier: subject claims={}", subject.getClaims().keySet()))
                .map(subject -> subject.getClaim("", XRD_MEMBER_IDENTIFIER_CLAIM))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private boolean isMembershipCredential(VerifiableCredential credential) {
        return credential.getType().contains(MEMBERSHIP_CREDENTIAL_TYPE);
    }
}
