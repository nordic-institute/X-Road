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
package org.niis.xroad.common.agreementtoken;

/**
 * Every way {@link AgreementTokenVerifier#verify} can decline a token, in the order they are checked. None of
 * them is ever an allow. A reason is either a validity failure (the token itself is unusable) or an authorization
 * failure (the token is sound but does not entitle the caller to this request); see
 * {@link #isAuthorizationFailure()}.
 */
public enum AgreementTokenRejectionReason {
    MALFORMED_TOKEN(false),
    UNKNOWN_KEY_ID(false),
    INVALID_SIGNATURE(false),
    EXPIRED(false),
    ISSUER_MISMATCH(false),
    AUDIENCE_MISMATCH(false),
    CLIENT_MISMATCH(true),
    SERVICE_MISMATCH(true),
    SCOPE_MISMATCH(true),
    NOT_GROUP_MEMBER(true),
    ENDPOINT_NOT_ALLOWED(true);

    private final boolean authorizationFailure;

    AgreementTokenRejectionReason(boolean authorizationFailure) {
        this.authorizationFailure = authorizationFailure;
    }

    /**
     * @return true when the token is sound but the signature-proven caller, the requested service or the requested
     *         endpoint is not entitled by it
     */
    public boolean isAuthorizationFailure() {
        return authorizationFailure;
    }
}
