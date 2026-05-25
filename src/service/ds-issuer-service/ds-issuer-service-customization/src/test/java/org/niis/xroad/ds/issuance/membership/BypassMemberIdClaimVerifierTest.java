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

import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import ee.ria.xroad.common.identifier.ClientId;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BypassMemberIdClaimVerifierTest {

    private BypassMemberIdClaimVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new BypassMemberIdClaimVerifier();
    }

    @Test
    void decodes_three_part_member_id_from_payload() {
        String jws = stubJws("did:web:holder", "did:web:issuer", "DEV/COM/SS0");

        Result<ClientId> result = verifier.verify(jws, "did:web:holder", "did:web:issuer");

        assertTrue(result.succeeded(), result.getFailureDetail());
        ClientId memberId = result.getContent();
        assertNotNull(memberId);
        assertEquals("DEV", memberId.getXRoadInstance());
        assertEquals("COM", memberId.getMemberClass());
        assertEquals("SS0", memberId.getMemberCode());
    }

    @Test
    void rejects_null_compact_jws_with_CLAIM_MISSING() {
        Result<ClientId> result = verifier.verify(null, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertEquals(MembershipVerificationReason.CLAIM_MISSING.name(), result.getFailureDetail());
    }

    @Test
    void rejects_unparseable_jws_with_CLAIM_MALFORMED() {
        Result<ClientId> result = verifier.verify("not-a-jws", "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertEquals(MembershipVerificationReason.CLAIM_MALFORMED.name(), result.getFailureDetail());
    }

    @Test
    void rejects_missing_member_id_claim_with_CLAIM_MALFORMED() {
        String jws = stubJwsWithoutMemberId("did:web:holder", "did:web:issuer");

        Result<ClientId> result = verifier.verify(jws, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertEquals(MembershipVerificationReason.CLAIM_MALFORMED.name(), result.getFailureDetail());
    }

    @Test
    void rejects_malformed_member_id_with_CLAIM_MALFORMED() {
        String jws = stubJws("did:web:holder", "did:web:issuer", "DEV/COM");

        Result<ClientId> result = verifier.verify(jws, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertEquals(MembershipVerificationReason.CLAIM_MALFORMED.name(), result.getFailureDetail());
    }

    @Test
    void rejects_holder_did_mismatch_with_CLAIM_AUDIENCE_INVALID() {
        String jws = stubJws("did:web:other", "did:web:issuer", "DEV/COM/SS0");

        Result<ClientId> result = verifier.verify(jws, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
        assertEquals(MembershipVerificationReason.CLAIM_AUDIENCE_INVALID.name(), result.getFailureDetail());
    }

    private static String stubJws(String holderDid, String audience, String memberId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim(BypassMemberIdClaimVerifier.STUB_MEMBER_ID_CLAIM, memberId)
                .build();
        return new PlainJWT(new PlainHeader(), claims).serialize();
    }

    private static String stubJwsWithoutMemberId(String holderDid, String audience) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        return new PlainJWT(new PlainHeader(), claims).serialize();
    }
}
