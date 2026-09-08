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

package org.niis.xroad.ds.identityhub.claim;

import ee.ria.xroad.common.identifier.ClientId;

import org.eclipse.edc.identityhub.spi.authentication.ParticipantSecureTokenService;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Decorator over the default {@link ParticipantSecureTokenService} that injects a signed
 * {@code xroadMemberClaim} JWS into the JWT claims for every credential-request token
 * issued by the IdentityHub.
 *
 * <p>The X-Road MemberId is read from the {@link IdentityHubParticipantContext} properties
 * under key {@value #MEMBER_ID_PROPERTY} as a canonical {@code INSTANCE/CLASS/CODE} string,
 * parsed to a {@link ClientId}, and handed to the {@link MemberClaimSigner} to produce a
 * compact-serialised JWS. The JWS is then embedded under the {@value #CLAIM_KEY} claim of
 * the outer JWT.
 *
 * <p>If the property is absent or malformed for a given participant context, the request
 * proceeds unchanged (the issuer-side attestation will then ERROR with {@code CLAIM_MISSING},
 * which surfaces the misconfiguration explicitly).
 */
public class XRoadClaimAwareSecureTokenService implements ParticipantSecureTokenService {

    /** Participant-context property key carrying the canonical INSTANCE/CLASS/CODE MemberId. */
    public static final String MEMBER_ID_PROPERTY = "xroadMemberId";

    /** JWT claim key the issuer-side attestation source reads. */
    public static final String CLAIM_KEY = "xroadMemberClaim";

    /** Standard JWT registered-claim name for the audience. */
    private static final String AUDIENCE_CLAIM = "aud";

    /** Number of segments in a canonical INSTANCE/CLASS/CODE member identifier. */
    private static final int MEMBER_ID_SEGMENT_COUNT = 3;

    private final ParticipantSecureTokenService delegate;
    private final IdentityHubParticipantContextService participantContextService;
    private final MemberClaimSigner signer;
    private final Monitor monitor;

    public XRoadClaimAwareSecureTokenService(ParticipantSecureTokenService delegate,
                                             IdentityHubParticipantContextService participantContextService,
                                             MemberClaimSigner signer,
                                             Monitor monitor) {
        this.delegate = delegate;
        this.participantContextService = participantContextService;
        this.signer = signer;
        this.monitor = monitor;
    }

    @Override
    public Result<TokenRepresentation> createToken(String participantContextId,
                                                   Map<String, String> claims,
                                                   String audience) {
        Map<String, String> enrichedClaims = withXRoadClaim(participantContextId, claims);
        return delegate.createToken(participantContextId, enrichedClaims, audience);
    }

    private Map<String, String> withXRoadClaim(String participantContextId, Map<String, String> claims) {
        ServiceResult<IdentityHubParticipantContext> ctxResult =
                participantContextService.getParticipantContext(participantContextId);
        if (ctxResult.failed()) {
            monitor.warning("X-Road claim signer: failed to resolve participant context '"
                    + participantContextId + "': " + ctxResult.getFailureDetail());
            return claims;
        }
        IdentityHubParticipantContext ctx = ctxResult.getContent();
        Object rawMemberId = ctx.getProperties() == null ? null : ctx.getProperties().get(MEMBER_ID_PROPERTY);
        if (!(rawMemberId instanceof String memberIdString) || memberIdString.isBlank()) {
            monitor.debug("X-Road claim signer: participant context '"
                    + participantContextId + "' has no '" + MEMBER_ID_PROPERTY
                    + "' property — passing through without xroadMemberClaim");
            return claims;
        }
        ClientId memberClientId = parseMemberId(memberIdString);
        if (memberClientId == null) {
            monitor.warning("X-Road claim signer: participant context '" + participantContextId
                    + "' has malformed '" + MEMBER_ID_PROPERTY + "' value '" + memberIdString
                    + "' (expected INSTANCE/CLASS/CODE) — passing through without xroadMemberClaim");
            return claims;
        }
        String audience = claims == null ? null : claims.get(AUDIENCE_CLAIM);
        Result<String> signed = signer.sign(memberClientId, ctx.getDid(), audience);
        if (signed.failed()) {
            monitor.warning("X-Road claim signer: signing failed for member '"
                    + memberIdString + "': " + signed.getFailureDetail());
            return claims;
        }
        Map<String, String> enriched = new HashMap<>(claims == null ? Map.of() : claims);
        enriched.put(CLAIM_KEY, signed.getContent());
        return enriched;
    }

    /**
     * Parses a canonical {@code INSTANCE/CLASS/CODE} string into a member-level {@link ClientId}.
     * Returns {@code null} on any malformed input (wrong segment count, blank segment).
     */
    private static ClientId parseMemberId(String memberId) {
        String[] parts = memberId.split("/", -1);
        if (parts.length != MEMBER_ID_SEGMENT_COUNT) {
            return null;
        }
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                return null;
            }
        }
        return ClientId.Conf.create(parts[0], parts[1], parts[2]);
    }
}
