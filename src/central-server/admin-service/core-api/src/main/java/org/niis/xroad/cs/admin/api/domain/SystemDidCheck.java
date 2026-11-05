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
package org.niis.xroad.cs.admin.api.domain;

import jakarta.annotation.Nullable;

/**
 * The result of checking a proposed SYSTEM DID against a proposed address of a Security Server.
 *
 * @param status           how the check ended
 * @param refusal          why the DID is refused, set only with {@link Status#REFUSED}
 * @param resolutionReason why the DID did not resolve, set only with {@link Status#NOT_RESOLVED}
 */
public record SystemDidCheck(Status status, @Nullable ParticipantValueRejection refusal,
                             @Nullable DidResolutionReason resolutionReason) {

    public static final SystemDidCheck RESOLVED = new SystemDidCheck(Status.RESOLVED, null, null);
    public static final SystemDidCheck RESOLVER_UNAVAILABLE = new SystemDidCheck(Status.RESOLVER_UNAVAILABLE, null, null);

    public static SystemDidCheck refused(ParticipantValueRejection refusal) {
        return new SystemDidCheck(Status.REFUSED, refusal, null);
    }

    public static SystemDidCheck notResolved(DidResolutionReason reason) {
        return new SystemDidCheck(Status.NOT_RESOLVED, null, reason);
    }

    public enum Status {
        /** The DID is not valid for the address; it was not resolved. */
        REFUSED,
        /** The DID is valid and resolves. */
        RESOLVED,
        /** The DID is valid and does not resolve. */
        NOT_RESOLVED,
        /** The DID is valid but the resolver could not be reached or did not answer for it. */
        RESOLVER_UNAVAILABLE
    }
}
