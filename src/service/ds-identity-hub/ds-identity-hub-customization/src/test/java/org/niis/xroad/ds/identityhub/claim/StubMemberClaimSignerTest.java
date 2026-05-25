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

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import ee.ria.xroad.common.identifier.ClientId;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubMemberClaimSignerTest {

    private StubMemberClaimSigner signer;

    @BeforeEach
    void setUp() {
        signer = new StubMemberClaimSigner();
    }

    @Test
    void emits_a_parseable_compact_jws() throws ParseException {
        ClientId memberId = ClientId.Conf.create("DEV", "COM", "SS0");

        Result<String> result = signer.sign(memberId, "did:web:holder", "did:web:issuer");

        assertTrue(result.succeeded(), result.getFailureDetail());
        String jws = result.getContent();
        assertNotNull(jws);
        assertFalse(jws.isBlank());
        // Parses cleanly with Nimbus
        JWTClaimsSet claims = JWTParser.parse(jws).getJWTClaimsSet();
        assertEquals("did:web:holder", claims.getSubject());
        assertEquals("did:web:holder", claims.getIssuer());
        assertEquals(1, claims.getAudience().size());
        assertEquals("did:web:issuer", claims.getAudience().get(0));
        assertNotNull(claims.getJWTID());
        assertNotNull(claims.getIssueTime());
        assertNotNull(claims.getExpirationTime());
        assertEquals("DEV/COM/SS0", claims.getClaim(StubMemberClaimSigner.STUB_MEMBER_ID_CLAIM));
    }

    @Test
    void rejects_null_member_id() {
        Result<String> result = signer.sign(null, "did:web:holder", "did:web:issuer");

        assertTrue(result.failed());
    }

    @Test
    void rejects_null_holder_did() {
        ClientId memberId = ClientId.Conf.create("DEV", "COM", "SS0");

        Result<String> result = signer.sign(memberId, null, "did:web:issuer");

        assertTrue(result.failed());
    }
}
