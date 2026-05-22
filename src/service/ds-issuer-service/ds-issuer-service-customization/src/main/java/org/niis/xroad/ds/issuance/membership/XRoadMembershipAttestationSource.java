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

package org.niis.xroad.ds.issuance.membership;

import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationContext;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSource;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.result.Result;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AttestationSource that replaces EDC's built-in {@code holder} type for X-Road dataspaces.
 *
 * <p>The holder embeds an {@link MemberIdClaim} inside the DCP JWT under the
 * {@code xroadMemberClaim} claim. This source pulls the claim from the JWT's parsed
 * {@link ClaimToken}, hands it to a {@link MemberIdClaimVerifier} for cryptographic
 * + freshness + replay checks, and emits the verified MemberId (split into structured
 * fields + canonical string) for cred-def mappings to route into {@code credentialSubject}.
 *
 * <p>Failure paths return {@link MembershipVerificationReason} names as the failure
 * message so EDC's ERROR state surfaces a stable reason code.
 */
public class XRoadMembershipAttestationSource implements AttestationSource {

    static final String CLAIM_KEY = "xroadMemberClaim";
    static final String JWT_CLAIM_TYPE = "jwt";

    private final MemberIdClaimVerifier verifier;
    private final MemberIdClaimParser parser;

    public XRoadMembershipAttestationSource(MemberIdClaimVerifier verifier, MemberIdClaimParser parser) {
        this.verifier = verifier;
        this.parser = parser;
    }

    @Override
    public Result<Map<String, Object>> execute(AttestationContext context) {
        ClaimToken token = context.getClaimToken(JWT_CLAIM_TYPE);
        if (token == null) {
            return Result.failure(MembershipVerificationReason.CLAIM_MISSING.name());
        }
        Object raw = token.getClaim(CLAIM_KEY);
        if (raw == null) {
            return Result.failure(MembershipVerificationReason.CLAIM_MISSING.name());
        }
        Result<MemberIdClaim> parsed = parser.parse(raw);
        if (parsed.failed()) {
            return parsed.mapEmpty();
        }
        MemberIdClaim claim = parsed.getContent();
        String holderDid = subjectOf(token);
        Result<String> verified = verifier.verify(claim, holderDid);
        if (verified.failed()) {
            return verified.mapEmpty();
        }
        return Result.success(toAttestationOutput(verified.getContent()));
    }

    private static String subjectOf(ClaimToken token) {
        Object sub = token.getClaim("sub");
        return sub == null ? null : sub.toString();
    }

    private static Map<String, Object> toAttestationOutput(String memberId) {
        String[] parts = memberId.split("/");
        if (parts.length != 3) {
            // Verifier accepted; this is structurally guaranteed. Returning the canonical only
            // keeps the failure-surface narrow rather than throwing in a hot path.
            return Map.of("memberId", memberId);
        }
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("xRoadInstance", parts[0]);
        out.put("memberClass", parts[1]);
        out.put("memberCode", parts[2]);
        out.put("memberId", memberId);
        return out;
    }
}