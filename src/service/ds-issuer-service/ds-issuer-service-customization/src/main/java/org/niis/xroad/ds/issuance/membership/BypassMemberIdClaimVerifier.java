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

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import ee.ria.xroad.common.identifier.ClientId;
import org.eclipse.edc.spi.result.Result;

import java.text.ParseException;

/**
 * Test-only verifier that decodes the JWS payload without any cryptographic checks.
 *
 * <p>Pairs with the {@code StubMemberClaimSigner}, which emits an unsigned JWT carrying a
 * custom {@code xroadMemberId} payload claim (since the stub has no real X-Road cert to
 * embed). This verifier extracts that claim and returns it as a {@link ClientId}.
 *
 * <p>Activated when {@code xroad.issuer.verify-claim} is {@code false}. Required for
 * system-test environments that have no Central Server / global conf / signer service.
 *
 * <p>MUST NOT be enabled in production deployments.
 */
public class BypassMemberIdClaimVerifier implements MemberIdClaimVerifier {

    static final String STUB_MEMBER_ID_CLAIM = "xroadMemberId";

    @Override
    public Result<ClientId> verify(String compactJws, String expectedHolderDid, String expectedIssuerDid) {
        if (compactJws == null || compactJws.isBlank()) {
            return Result.failure(MembershipVerificationReason.CLAIM_MISSING.name());
        }
        JWTClaimsSet claims;
        try {
            claims = JWTParser.parse(compactJws).getJWTClaimsSet();
        } catch (ParseException e) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        if (claims == null) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        String subject = claims.getSubject();
        if (expectedHolderDid != null && subject != null && !expectedHolderDid.equals(subject)) {
            return Result.failure(MembershipVerificationReason.CLAIM_AUDIENCE_INVALID.name());
        }
        Object rawMemberId;
        try {
            rawMemberId = claims.getClaim(STUB_MEMBER_ID_CLAIM);
        } catch (Exception e) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        if (!(rawMemberId instanceof String memberIdString) || memberIdString.isBlank()) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        ClientId memberId = parseMemberId(memberIdString);
        if (memberId == null) {
            return Result.failure(MembershipVerificationReason.CLAIM_MALFORMED.name());
        }
        return Result.success(memberId);
    }

    private static ClientId parseMemberId(String memberId) {
        String[] parts = memberId.split("/", -1);
        if (parts.length != 3) {
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
