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

/**
 * Container for the holder-signed assertion that claims a specific X-Road MemberId.
 *
 * <p>Carried inside the DCP JWT presented by the holder (claim key {@code xroadMemberClaim}).
 * The {@code payload} is signed by the member's X-Road sign key; the {@code certificate}
 * is the end-entity sign certificate. The issuer's verifier checks the signature, looks
 * the cert up in global conf to derive the actual MemberId, and asserts it matches
 * {@link Payload#memberId()}.
 *
 * @param payload     the signed assertion content
 * @param signature   base64-encoded signature of {@code payload} bytes (canonical JSON, sorted keys)
 * @param certificate base64-encoded DER X.509 sign cert of the claiming member
 */
public record MemberIdClaim(Payload payload, String signature, String certificate) {

    /**
     * Signed assertion content.
     *
     * @param holderDid the DID submitting the credential request
     * @param memberId  the X-Road MemberId claimed (canonical INSTANCE/CLASS/CODE form)
     * @param nonce     unique per-request nonce; rejected if seen within the freshness window
     * @param issuedAt  epoch seconds when the holder produced this claim
     */
    public record Payload(String holderDid, String memberId, String nonce, long issuedAt) { }
}