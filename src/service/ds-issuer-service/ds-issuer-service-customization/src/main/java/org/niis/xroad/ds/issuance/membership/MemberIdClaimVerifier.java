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

import org.eclipse.edc.spi.result.Result;

/**
 * Validates a holder-submitted {@link MemberIdClaim}: signature, cert chain via global conf,
 * MemberId equality, freshness window, replay store.
 *
 * <p>Production implementation backs by {@code lib:globalconf-impl} and the issuer's nonce
 * store. A bypass implementation exists for system-test environments that lack a CS.
 */
public interface MemberIdClaimVerifier {

    /**
     * Verifies the supplied claim was issued by the X-Road member it names.
     *
     * @param claim     the parsed claim from the credential request
     * @param holderDid the DID extracted from the EDC ClaimToken; must match {@link MemberIdClaim.Payload#holderDid()}
     * @return success with the verified MemberId (canonical string), failure carrying a
     *         {@link MembershipVerificationReason} in the message field
     */
    Result<String> verify(MemberIdClaim claim, String holderDid);
}