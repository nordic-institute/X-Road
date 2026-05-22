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

/**
 * Holder-side mirror of the signed X-Road MemberId assertion. The {@link Payload} is
 * serialised to canonical JSON and signed; result + cert are wrapped here and injected
 * into the DCP JWT as the {@code xroadMemberClaim} claim.
 *
 * <p>Issuer-side equivalent: {@code org.niis.xroad.ds.issuance.membership.MemberIdClaim}.
 * Kept duplicated rather than shared to keep IdentityHub and IssuerService modules
 * independent.
 *
 * @param payload     the signed assertion content
 * @param signature   base64-encoded signature of canonical-JSON payload bytes
 * @param certificate base64-encoded DER X.509 sign cert of the claiming member
 */
public record MemberClaim(Payload payload, String signature, String certificate) {

    /**
     * @param holderDid the IdentityHub participant context's DID
     * @param memberId  canonical INSTANCE/CLASS/CODE form
     * @param nonce     fresh per-request nonce
     * @param issuedAt  epoch seconds
     */
    public record Payload(String holderDid, String memberId, String nonce, long issuedAt) { }
}