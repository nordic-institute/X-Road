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
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgentServiceExtension;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper.XRD_INSTANCE_ATTRIBUTE;
import static org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper.XRD_MEMBER_CLASS_ATTRIBUTE;
import static org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper.XRD_MEMBER_CODE_ATTRIBUTE;

/**
 * {@link ParticipantAgentServiceExtension} that extracts the X-Road member identity from a validated
 * {@code XRoadMembershipCredential} in the DCP claim token and exposes it as participant-agent attributes.
 *
 * <p>The credential carries the identity as the separate {@code credentialSubject} claims
 * {@code xroadInstance}, {@code memberClass} and {@code memberCode}, set by the issuer service from
 * the verified X-Road sign certificate. They are surfaced verbatim as the participant-agent
 * attributes {@code xrd:xroadInstance}, {@code xrd:memberClass} and {@code xrd:memberCode}; the
 * X-Road constraint functions assemble the {@link ee.ria.xroad.common.identifier.ClientId} from them
 * via {@code PolicyContextHelper}.
 *
 * <p>If no {@code XRoadMembershipCredential} is present, or any of the three claims is absent/blank, an
 * empty map is returned — no exception is thrown.
 *
 * <p>A participant context can present more than one active {@code XRoadMembershipCredential} at once —
 * notably the per-Security-Server SYSTEM context, whose credential is additively re-anchored to a new
 * owner on ownership change while the stale one is left to expire rather than revoked. Candidates are
 * matched by participant context and credential type, and the most recently issued one wins; a
 * deterministic tiebreak on the member attributes themselves resolves equal issuance instants so the
 * outcome never depends on iteration order. Because re-anchoring always issues a strictly newer
 * credential than the one it supersedes, the previous owner's attributes keep being presented for the
 * whole overlap window between re-anchor and expiry of the stale credential — a deliberate fail-open
 * choice that favors continuity over an access denial while the replacement is still pending. The
 * {@link ClaimToken} carries no reference to the participant context itself, which is why this
 * selection is entirely self-contained within the presented credential set.
 */
@Slf4j
class XRoadMemberIdAttributes implements ParticipantAgentServiceExtension {

    static final String MEMBERSHIP_CREDENTIAL_TYPE = "XRoadMembershipCredential";
    static final String XROAD_INSTANCE_CLAIM = "xroadInstance";
    static final String MEMBER_CLASS_CLAIM = "memberClass";
    static final String MEMBER_CODE_CLAIM = "memberCode";

    /**
     * Most-recently-issued-wins, with a deterministic tiebreak over the member attributes
     * themselves for candidates sharing the same issuance instant — so the winner never
     * depends on the order the credentials happened to be iterated in.
     */
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparing(Candidate::issuanceDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(candidate -> candidate.attributes().get(XRD_MEMBER_CODE_ATTRIBUTE))
            .thenComparing(candidate -> candidate.attributes().get(XRD_MEMBER_CLASS_ATTRIBUTE))
            .thenComparing(candidate -> candidate.attributes().get(XRD_INSTANCE_ATTRIBUTE));

    @Override
    @NotNull
    public Map<String, String> attributesFor(ClaimToken token) {
        var vcList = token.getListClaim("vc");
        log.debug("attributesFor: ClaimToken claims={} vcListSize={} vcListElementTypes={}",
                token.getClaims().keySet(),
                vcList == null ? -1 : vcList.size(),
                vcList == null ? "<null>" : vcList.stream().map(o -> o == null ? "null" : o.getClass().getName()).toList());
        var result = extractMemberAttributes(vcList);
        log.debug("attributesFor: result={}", result);
        return result;
    }

    private Map<String, String> extractMemberAttributes(List<?> vcList) {
        // EDC's DcpDefaultServicesExtension.defaultClaimTokenFunction stuffs the
        // list of VerifiablePresentation.getCredentials() (i.e. VerifiableCredential,
        // NOT VerifiableCredentialContainer) under the "vc" claim key.
        if (vcList == null) {
            return Map.of();
        }
        return vcList.stream()
                .filter(VerifiableCredential.class::isInstance)
                .map(VerifiableCredential.class::cast)
                .peek(vc -> log.debug("extractMemberAttributes: candidate vc types={} subjects={}",
                        vc.getType(), vc.getCredentialSubject().size()))
                .filter(this::isXRoadMembershipCredential)
                .flatMap(this::candidatesOf)
                .max(CANDIDATE_ORDER)
                .map(Candidate::attributes)
                .orElse(Map.of());
    }

    private Stream<Candidate> candidatesOf(VerifiableCredential credential) {
        return credential.getCredentialSubject().stream()
                .peek(subject -> log.debug("extractMemberAttributes: subject claims={}", subject.getClaims().keySet()))
                .map(this::toMemberAttributes)
                .filter(Objects::nonNull)
                .map(attributes -> new Candidate(credential.getIssuanceDate(), attributes));
    }

    private record Candidate(Instant issuanceDate, Map<String, String> attributes) { }

    private Map<String, String> toMemberAttributes(CredentialSubject subject) {
        var xroadInstance = stringClaim(subject, XROAD_INSTANCE_CLAIM);
        var memberClass = stringClaim(subject, MEMBER_CLASS_CLAIM);
        var memberCode = stringClaim(subject, MEMBER_CODE_CLAIM);
        if (xroadInstance == null || memberClass == null || memberCode == null) {
            return null;
        }
        return Map.of(
                XRD_INSTANCE_ATTRIBUTE, xroadInstance,
                XRD_MEMBER_CLASS_ATTRIBUTE, memberClass,
                XRD_MEMBER_CODE_ATTRIBUTE, memberCode);
    }

    private static String stringClaim(CredentialSubject subject, String key) {
        var value = subject.getClaim("", key);
        if (value == null) {
            return null;
        }
        var asString = value.toString();
        return asString.isBlank() ? null : asString;
    }

    private boolean isXRoadMembershipCredential(VerifiableCredential credential) {
        return credential.getType().contains(MEMBERSHIP_CREDENTIAL_TYPE);
    }
}
