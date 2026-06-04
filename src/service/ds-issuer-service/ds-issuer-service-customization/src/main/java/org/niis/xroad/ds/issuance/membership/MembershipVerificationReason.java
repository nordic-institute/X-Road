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
 * Stable failure reasons emitted by the XRoadMembershipCredential attestation source.
 * Encoded in the EDC credential-request ERROR state and accessible to callers for
 * programmatic distinction of validation failures.
 *
 * <p>Coarse buckets: precise cause lives in the failure log message. See
 * {@code PLAN-real-signing-and-verification.md} Q17 for rationale.
 */
public enum MembershipVerificationReason {
    /** Outer JWT did not carry an {@code xroadMemberClaim}. */
    CLAIM_MISSING,
    /** Inner JWS unparseable, headers or payload invalid. */
    CLAIM_MALFORMED,
    /** {@code iat}/{@code exp} window violation (covers exp, iat-in-future, leeway exhausted). */
    CLAIM_EXPIRED,
    /** {@code jti} already seen. */
    CLAIM_REPLAYED,
    /** {@code sub} doesn't match outer JWT holder DID, or {@code aud} doesn't match this issuer. */
    CLAIM_AUDIENCE_INVALID,
    /** JWS signature didn't verify against the cert in the {@code x5c} header. */
    SIGNATURE_INVALID,
    /** PKIX failed (untrusted CA, expired cert, broken chain, malformed cert, subject not a ClientId). */
    CERT_CHAIN_INVALID,
    /** Pinned OCSP missing, stale, signature invalid, or status not {@code GOOD}. */
    OCSP_INVALID,
    /** Verifier infrastructure not ready (cold start, confclient unreachable). */
    GLOBALCONF_UNAVAILABLE
}
