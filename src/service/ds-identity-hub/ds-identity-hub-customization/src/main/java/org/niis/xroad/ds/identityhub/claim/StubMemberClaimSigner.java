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

import org.eclipse.edc.spi.result.Result;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Test-only signer that produces a deterministic-shape claim with a fixed-string
 * signature and certificate. Paired with the issuer-side
 * {@code BypassMemberIdClaimVerifier} for end-to-end testing without real PKI.
 *
 * <p>MUST NOT be enabled in production. Activated by {@code xroad.identityhub.sign-claim=false}.
 */
public class StubMemberClaimSigner implements MemberClaimSigner {

    private static final String STUB_SIGNATURE = "stub-signature";
    private static final String STUB_CERTIFICATE = "stub-certificate";

    private final SecureRandom random = new SecureRandom();

    @Override
    public Result<MemberClaim> sign(String holderDid, String memberId) {
        if (holderDid == null || memberId == null) {
            return Result.failure("holderDid and memberId must be supplied");
        }
        var payload = new MemberClaim.Payload(holderDid, memberId, freshNonce(), Instant.now().getEpochSecond());
        return Result.success(new MemberClaim(payload, STUB_SIGNATURE, STUB_CERTIFICATE));
    }

    private String freshNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}