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
package org.niis.xroad.proxy.core.serverproxy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.serverproxy.AgreementTokenAccessCheck.Decision;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;

import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;

/**
 * Refuses calls without a verifiable agreement token while the {@link LegacyCallRefusalPolicy} refuses legacy
 * calls. The policy is only consulted for a token decision that would otherwise fall back to the ACL.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LegacyCallGate {

    static final String AGREEMENT_TOKEN_REQUIRED = "AGREEMENT_TOKEN_REQUIRED";
    static final String KEY_MATERIAL_UNAVAILABLE = "KEY_MATERIAL_UNAVAILABLE";

    /**
     * Throws for a tokenless or unverifiable call when the policy refuses legacy calls. Any other decision passes.
     */
    static void requireLegacyCallAllowed(Decision decision, LegacyCallRefusalPolicy policy) {
        var reason = legacyReason(decision);
        if (reason != null && policy.refusedAsProvider()) {
            throw rejected(reason);
        }
    }


    private static String legacyReason(Decision decision) {
        return switch (decision) {
            case Decision.Absent ignored -> AGREEMENT_TOKEN_REQUIRED;
            case Decision.Unavailable ignored -> KEY_MATERIAL_UNAVAILABLE;
            case Decision.Accepted ignored -> null;
            case Decision.Rejected ignored -> null;
        };
    }

    private static XrdRuntimeException rejected(String reason) {
        return XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED,
                "Agreement token rejected: %s".formatted(reason));
    }
}
