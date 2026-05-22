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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.result.Result;

import java.util.Map;

/**
 * Parses the raw {@code xroadMemberClaim} value out of an EDC {@link org.eclipse.edc.spi.iam.ClaimToken}
 * into a typed {@link MemberIdClaim}.
 *
 * <p>The holder side encodes the claim as a JSON string (because EDC's
 * {@code ParticipantSecureTokenService} claim API is {@code Map<String, String>}). Inside
 * the JWT, that string value survives the JWT deserialisation as-is — i.e. the parser
 * receives a {@code String}, which we JSON-decode here. For forward-compatibility (e.g.
 * a future EDC version with a typed claim API), a {@code Map<String, Object>} shape is
 * also accepted directly.
 *
 * <p>Malformed input surfaces as {@link MembershipVerificationReason#CLAIM_MALFORMED}.
 */
public class MemberIdClaimParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @SuppressWarnings("unchecked")
    public Result<MemberIdClaim> parse(Object raw) {
        Map<?, ?> root;
        if (raw instanceof Map<?, ?> m) {
            root = m;
        } else if (raw instanceof String s) {
            try {
                root = OBJECT_MAPPER.readValue(s, MAP_TYPE);
            } catch (Exception e) {
                return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
            }
        } else {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        Object payloadRaw = root.get("payload");
        Object signature = root.get("signature");
        Object certificate = root.get("certificate");
        if (!(payloadRaw instanceof Map<?, ?> payload)
                || !(signature instanceof String sig)
                || !(certificate instanceof String cert)) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        Object holderDid = payload.get("holderDid");
        Object memberId = payload.get("memberId");
        Object nonce = payload.get("nonce");
        Object issuedAt = payload.get("issuedAt");
        if (!(holderDid instanceof String did) || !(memberId instanceof String mid)
                || !(nonce instanceof String n) || !(issuedAt instanceof Number ts)) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        var typed = new MemberIdClaim(
                new MemberIdClaim.Payload(did, mid, n, ts.longValue()),
                sig,
                cert);
        return Result.success(typed);
    }
}